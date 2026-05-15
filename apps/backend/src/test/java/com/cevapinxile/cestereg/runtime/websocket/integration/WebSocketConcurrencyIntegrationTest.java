package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-concurrency")
@Tag("ws-nightly")
@DisplayName("WebSocket concurrency correctness auditing")
class WebSocketConcurrencyIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void concurrentAdminJoinRaceNeverCreatesDuplicateWelcomeAndCanBeExplicitlyCleanedForRecovery()
      throws Exception {
    final int contenders = 10;
    final ExecutorService executor = Executors.newFixedThreadPool(contenders);
    final CountDownLatch start = new CountDownLatch(1);
    final List<SocketProbe> probes = new ArrayList<>();
    final List<Future<Boolean>> futures = new ArrayList<>();

    try {
      for (int i = 0; i < contenders; i++) {
        final SocketProbe probe = new SocketProbe();
        probes.add(probe);
        futures.add(
            executor.submit(
                () -> {
                  start.await(2, TimeUnit.SECONDS);
                  return connectPossiblyRejected(0, ROOM_A, probe);
                }));
      }

      start.countDown();
      for (Future<Boolean> future : futures) {
        future.get(4, TimeUnit.SECONDS);
      }

      int welcomeFrames = 0;
      for (SocketProbe probe : probes) {
        final String frame = probe.pollFrame(700);
        if (frame != null) {
          assertContract(mapper.readValue(frame, Map.class), "welcome");
          assertNull(probe.pollFrame(250), "racing admin must not receive duplicate welcome");
          welcomeFrames++;
        }
      }

      assertTrue(
          welcomeFrames <= 1,
          "concurrent admin joins must never create duplicate welcome winners");
      assertTrue(
          sessionRegistry.isAdminPresent(ROOM_A) || welcomeFrames == 0,
          "race may reject all transient sockets, but must not register duplicate admin state");
    } finally {
      executor.shutdownNow();
      for (SocketProbe probe : probes) {
        probe.close(CloseStatus.NORMAL);
      }
      // Some containers leave the winning racing session registered briefly even after client-side close.
      // Explicitly close the registry session: the invariant is no duplicate winner, not timing of async cleanup.
      closeRegistrySession(ROOM_A, true);
    }

    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
    final SocketProbe recovered = connectAdmin(ROOM_A);
    assertContract(recovered.readJson(), "welcome");
  }

  @Test
  void concurrentTvJoinLeaveStormDoesNotBreakAdminDeliveryOrLeakTvSession() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    admin.readJson();
    final int attempts = 16;
    final ExecutorService executor = Executors.newFixedThreadPool(8);
    final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
    final CountDownLatch start = new CountDownLatch(1);

    try {
      final List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < attempts; i++) {
        futures.add(
            executor.submit(
                () -> {
                  final SocketProbe tv = new SocketProbe();
                  try {
                    start.await(2, TimeUnit.SECONDS);
                    connectPossiblyRejected(1, ROOM_A, tv);
                    tv.pollFrame(100);
                    tv.close(CloseStatus.GOING_AWAY);
                  } catch (Throwable ex) {
                    failures.add(ex);
                  }
                }));
      }

      start.countDown();
      for (Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }

      assertTrue(failures.isEmpty(), "join/leave storm must not throw: " + failures);
      assertTrue(sessionRegistry.isAdminPresent(ROOM_A));
      broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"admin_after_join_leave_storm\"}");
      assertEquals("admin_after_join_leave_storm", admin.readJson().get("type"));
    } finally {
      executor.shutdownNow();
      assertDoesNotThrow(() -> closeRegistrySession(ROOM_A, false));
    }
  }

  @Test
  void repeatedRapidTvReconnectsNeverCreateDuplicateGameplayDelivery() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final List<SocketProbe> duplicates = new ArrayList<>();

    try {
      for (int i = 0; i < 8; i++) {
        final SocketProbe duplicate = new SocketProbe();
        duplicates.add(duplicate);
        connectPossiblyRejected(1, ROOM_A, duplicate);
        duplicate.pollFrame(100);
      }

      broadcastGateway.toTv(ROOM_A, "{\"type\":\"after_reconnect_storm\"}");

      int total = countFramesOfType(tv, "after_reconnect_storm", 500);
      for (SocketProbe duplicate : duplicates) {
        total += countFramesOfType(duplicate, "after_reconnect_storm", 150);
      }
      assertEquals(1, total, "reconnect storm must not fan out one gameplay frame to multiple TVs");
    } finally {
      for (SocketProbe duplicate : duplicates) {
        duplicate.close(CloseStatus.NORMAL);
      }
    }
  }

  @Test
  void simultaneousRoomRemovalAndBroadcastDoesNotCorruptUnrelatedRoom() throws Exception {
    final SocketProbe adminA = connectAdmin(ROOM_A);
    final SocketProbe tvA = connectTv(ROOM_A);
    final SocketProbe adminB = connectAdmin(ROOM_B);
    adminA.readJson();
    tvA.readJson();
    adminB.readJson();

    final ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      final Future<?> removeRoom = executor.submit(() -> gamesByCode.remove(ROOM_A));
      final Future<?> broadcastRoomA =
          executor.submit(() -> broadcastGateway.broadcast(ROOM_A, "{\"type\":\"during_room_removal\"}"));
      removeRoom.get(3, TimeUnit.SECONDS);
      broadcastRoomA.get(3, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    broadcastGateway.toAdmin(ROOM_B, "{\"type\":\"room_b_still_healthy\"}");
    assertEquals("room_b_still_healthy", adminB.readJson().get("type"));
  }

  @Test
  void executorSaturationWithDuplicateConnectsStillAllowsCanonicalClientsToReceiveOnce()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final int duplicateTasks = 24;
    final ExecutorService executor = Executors.newFixedThreadPool(6);
    final AtomicInteger acceptedDuplicates = new AtomicInteger();
    final List<SocketProbe> duplicates = new ArrayList<>();

    try {
      final List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < duplicateTasks; i++) {
        final int socketPosition = i % 2;
        final SocketProbe duplicate = new SocketProbe();
        duplicates.add(duplicate);
        futures.add(
            executor.submit(
                () -> {
                  if (connectPossiblyRejected(socketPosition, ROOM_A, duplicate)) {
                    acceptedDuplicates.incrementAndGet();
                  }
                  duplicate.pollFrame(100);
                }));
      }

      for (Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }

      broadcastGateway.broadcast(ROOM_A, "{\"type\":\"after_saturation\"}");
      assertEquals("after_saturation", admin.readJson().get("type"));
      assertEquals("after_saturation", tv.readJson().get("type"));
      assertNull(admin.pollFrame(250));
      assertNull(tv.pollFrame(250));
    } finally {
      executor.shutdownNow();
      for (SocketProbe duplicate : duplicates) {
        duplicate.close(CloseStatus.NORMAL);
      }
    }
  }
}
