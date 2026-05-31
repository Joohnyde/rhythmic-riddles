package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-nightly")
@Tag("ws-resource")
@DisplayName("WebSocket resource-leak and retained-session integration tests")
class WebSocketResourceLeakIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void repeatedCleanAdminTvConnectDisconnectCyclesLeaveNoRegistrySessions() throws Exception {
    final int cycles = 40;

    for (int i = 0; i < cycles; i++) {
      final SocketProbe admin = connectAdmin(ROOM_A);
      final SocketProbe tv = connectTv(ROOM_A);
      assertContract(admin.readJson(), "welcome");
      assertContract(tv.readJson(), "welcome");

      admin.close(CloseStatus.NORMAL);
      tv.close(CloseStatus.NORMAL);
      assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
      assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));
    }

    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");
    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"after_leak_cycle\"}");
    assertEquals("after_leak_cycle", admin.readJson().get("type"));
    assertEquals("after_leak_cycle", tv.readJson().get("type"));
    assertNull(admin.pollFrame(250));
    assertNull(tv.pollFrame(250));
  }

  @Test
  void manyRoomSessionsCanBeCreatedClosedAndRecreatedWithoutRetainedRoomState() throws Exception {
    final int roomCount = 24;
    final List<String> rooms = new ArrayList<>();
    final List<SocketProbe> probes = new ArrayList<>();

    for (int i = 0; i < roomCount; i++) {
      final String room = "R" + String.format("%03d", i);
      rooms.add(room);
      gamesByCode.put(room, game(room));
      final SocketProbe admin = connectAdmin(room);
      final SocketProbe tv = connectTv(room);
      assertContract(admin.readJson(), "welcome");
      assertContract(tv.readJson(), "welcome");
      probes.add(admin);
      probes.add(tv);
    }

    for (SocketProbe probe : probes) {
      probe.close(CloseStatus.NORMAL);
    }
    for (String room : rooms) {
      assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(room));
      assertEventuallyFalse(() -> sessionRegistry.isTvPresent(room));
    }

    for (String room : rooms) {
      final SocketProbe admin = connectAdmin(room);
      assertContract(admin.readJson(), "welcome");
      broadcastGateway.toAdmin(room, "{\"type\":\"room_recreated\",\"roomCode\":\"" + room + "\"}");
      assertEquals("room_recreated", admin.readJson().get("type"));
      admin.close(CloseStatus.NORMAL);
    }
  }

  @Test
  void rejectedDuplicateStormDoesNotRetainLoserSessionsOrBlockCanonicalClients() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");

    final int attempts = 30;
    final List<SocketProbe> duplicates = new ArrayList<>();
    for (int i = 0; i < attempts; i++) {
      final SocketProbe duplicate = new SocketProbe();
      duplicates.add(duplicate);
      connectPossiblyRejected(i % 2, ROOM_A, duplicate);
      assertNull(
          duplicate.pollFrame(100), "duplicate slot contender must not receive application frames");
      duplicate.close(CloseStatus.NORMAL);
    }

    assertTrue(sessionRegistry.isAdminPresent(ROOM_A));
    assertTrue(sessionRegistry.isTvPresent(ROOM_A));
    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"canonical_after_duplicate_storm\"}");
    assertEquals("canonical_after_duplicate_storm", admin.readJson().get("type"));
    assertEquals("canonical_after_duplicate_storm", tv.readJson().get("type"));
    assertNull(admin.pollFrame(250));
    assertNull(tv.pollFrame(250));
  }

  @Test
  void concurrentOpenCloseStormEndsWithEmptyRegistryAndFreshConnectionWorks() throws Exception {
    final int tasks = 20;
    final ExecutorService executor = Executors.newFixedThreadPool(8);
    final List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < tasks; i++) {
      final int index = i;
      futures.add(
          executor.submit(
              () -> {
                final SocketProbe probe = new SocketProbe();
                try {
                  connectPossiblyRejected(index % 2, ROOM_A, probe);
                  probe.pollFrame(100);
                  probe.close(index % 3 == 0 ? CloseStatus.GOING_AWAY : CloseStatus.NORMAL);
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              }));
    }

    for (Future<?> future : futures) {
      future.get(5, TimeUnit.SECONDS);
    }
    executor.shutdownNow();

    closeRegistrySession(ROOM_A, true);
    closeRegistrySession(ROOM_A, false);
    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    final SocketProbe admin = connectAdmin(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"fresh_after_open_close_storm\"}");
    assertEquals("fresh_after_open_close_storm", admin.readJson().get("type"));
  }

  @Test
  void registryIsCleanAfterMixedNormalGoingAwayAndDuplicateLifecycle() throws Exception {
    for (int i = 0; i < 12; i++) {
      final SocketProbe admin = connectAdmin(ROOM_A);
      final SocketProbe tv = connectTv(ROOM_A);
      assertContract(admin.readJson(), "welcome");
      assertContract(tv.readJson(), "welcome");

      final SocketProbe duplicateAdmin = new SocketProbe();
      final SocketProbe duplicateTv = new SocketProbe();
      connectPossiblyRejected(0, ROOM_A, duplicateAdmin);
      connectPossiblyRejected(1, ROOM_A, duplicateTv);
      duplicateAdmin.close(CloseStatus.GOING_AWAY);
      duplicateTv.close(CloseStatus.GOING_AWAY);

      admin.close(i % 2 == 0 ? CloseStatus.NORMAL : CloseStatus.GOING_AWAY);
      tv.close(i % 2 == 0 ? CloseStatus.GOING_AWAY : CloseStatus.NORMAL);
      assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
      assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));
    }

    assertFalse(sessionRegistry.areBothPresent(ROOM_A));
  }
}
