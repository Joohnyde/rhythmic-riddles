package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-nightly")
@Tag("ws-security")
@DisplayName("WebSocket security and abuse-resilience integration tests")
class WebSocketSecurityIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void unknownRoomAndEncodedPathTraversalRoomCodesCannotOpenApplicationSession()
      throws DerivedException {
    final SocketProbe unknown = new SocketProbe();
    final SocketProbe encodedTraversal = new SocketProbe();

    assertFalse(connectPossiblyRejected(0, "NOPE", unknown));
    assertFalse(
        connectUrlPossiblyRejected(
            "ws://localhost:" + port + "/ws/0" + ROOM_A + "%2F..%2F" + ROOM_B, encodedTraversal));

    assertNull(unknown.pollFrame(250));
    assertNull(encodedTraversal.pollFrame(250));
    assertFalse(sessionRegistry.isAdminPresent("NOPE"));
    assertFalse(sessionRegistry.isAdminPresent(ROOM_A + "%2F..%2F" + ROOM_B));
    verify(gameService, never()).contextFetch("NOPE");
  }

  @Test
  void roomHijackPayloadFromRoomADoesNotCreateFramesInRoomB() throws Exception {
    final SocketProbe adminA = connectAdmin(ROOM_A);
    final SocketProbe tvA = connectTv(ROOM_A);
    final SocketProbe adminB = connectAdmin(ROOM_B);
    assertContract(adminA.readJson(), "welcome");
    assertContract(tvA.readJson(), "welcome");
    assertContract(adminB.readJson(), "welcome");

    adminA.send("{\"type\":\"song_next\",\"roomCode\":\"" + ROOM_B + "\",\"force\":true}");
    tvA.send("{\"type\":\"pause\",\"roomCode\":\"" + ROOM_B + "\",\"answeringTeamId\":\"evil\"}");

    assertNull(
        adminA.pollFrame(300), "client-originated room hijack payload must not echo to room A");
    assertNull(tvA.pollFrame(300), "client-originated room hijack payload must not echo to TV");
    assertNull(
        adminB.pollFrame(300), "room B must not receive command injection from room A clients");
    assertTrue(sessionRegistry.areBothPresent(ROOM_A));
    assertTrue(sessionRegistry.isAdminPresent(ROOM_B));
  }

  @Test
  void unsupportedInboundFloodFromTvDoesNotBroadcastDoesNotEchoAndKeepsConnectionUsable()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");

    for (int i = 0; i < 100; i++) {
      tv.send("{\"type\":\"unsupported_" + i + "\",\"payload\":{\"index\":" + i + "}}");
    }

    assertNull(admin.pollFrame(500), "unsupported inbound flood must not reach admin");
    assertNull(tv.pollFrame(500), "unsupported inbound flood must not be echoed to TV");
    assertTrue(tv.isOpen());
    broadcastGateway.toTv(ROOM_A, "{\"type\":\"after_unsupported_flood\"}");
    assertEquals("after_unsupported_flood", tv.readJson().get("type"));
  }

  @Test
  void malformedJsonFloodDoesNotCrashHandlerOrPoisonFutureBroadcasts() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");

    for (int i = 0; i < 50; i++) {
      admin.send("{bad-json-" + i);
      tv.send("[not-a-command," + i);
    }

    assertNull(admin.pollFrame(400));
    assertNull(tv.pollFrame(400));
    assertTrue(admin.isOpen());
    assertTrue(tv.isOpen());

    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"after_malformed_flood\"}");
    assertEquals("after_malformed_flood", admin.readJson().get("type"));
    assertEquals("after_malformed_flood", tv.readJson().get("type"));
  }

  @Test
  void oversizedClientPayloadMayBeIgnoredOrClosedButMustNotPoisonRoomOrFutureConnections()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    assertContract(admin.readJson(), "welcome");

    final StringBuilder payload =
        new StringBuilder("{\"type\":\"oversized_ignored\",\"payload\":\"");
    payload.append("x".repeat(96 * 1024));
    payload.append("\"}");

    try {
      admin.send(payload.toString());
    } catch (IllegalStateException ex) {
      // Acceptable: the WebSocket container may already have closed this abusive session.
    }

    if (admin.isOpen()) {
      assertNull(admin.pollFrame(500), "oversized client payload must not produce app frames");
      broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"after_oversized_payload\"}");

      final String maybeDelivered = admin.pollFrame(700);
      if (maybeDelivered != null) {
        assertEquals(
            "after_oversized_payload", mapper.readValue(maybeDelivered, Map.class).get("type"));
        admin.close(CloseStatus.NORMAL);
      } else {
        // The close can happen asynchronously after isOpen() was checked.
        admin.close(CloseStatus.GOING_AWAY);
        closeRegistrySession(ROOM_A, true);
      }
    } else {
      closeRegistrySession(ROOM_A, true);
    }

    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
    final SocketProbe recoveredAdmin = connectAdmin(ROOM_A);
    assertContract(recoveredAdmin.readJson(), "welcome");
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"clean_connection_after_oversized_payload\"}");
    assertEquals("clean_connection_after_oversized_payload", recoveredAdmin.readJson().get("type"));
  }

  @Test
  void reconnectSpamAgainstAdminSlotDoesNotReplaceWinnerOrDuplicateDelivery() throws Exception {
    final SocketProbe canonicalAdmin = connectAdmin(ROOM_A);
    assertContract(canonicalAdmin.readJson(), "welcome");

    final List<SocketProbe> spamClients = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      final SocketProbe contender = new SocketProbe();
      spamClients.add(contender);
      connectPossiblyRejected(0, ROOM_A, contender);
      assertNull(contender.pollFrame(100));
    }

    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"winner_still_controls_admin_slot\"}");
    assertEquals("winner_still_controls_admin_slot", canonicalAdmin.readJson().get("type"));
    assertNull(canonicalAdmin.pollFrame(250));
    for (SocketProbe contender : spamClients) {
      assertNull(contender.pollFrame(100));
      contender.close(CloseStatus.NORMAL);
    }
  }

  @Test
  void inboundDosStyleConcurrentMessageSpamDoesNotLeakIntoOutboundProtocol() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");

    final ExecutorService executor = Executors.newFixedThreadPool(6);
    final List<Future<?>> futures = new ArrayList<>();
    for (int worker = 0; worker < 6; worker++) {
      final int workerId = worker;
      futures.add(
          executor.submit(
              () -> {
                try {
                  for (int i = 0; i < 25; i++) {
                    final SocketProbe target = workerId % 2 == 0 ? admin : tv;
                    synchronized (target) {
                      target.send(
                          "{\"type\":\"client_spam\",\"worker\":"
                              + workerId
                              + ",\"sequence\":"
                              + i
                              + "}");
                    }
                  }
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              }));
    }

    for (Future<?> future : futures) {
      future.get(5, TimeUnit.SECONDS);
    }
    executor.shutdownNow();

    assertNull(admin.pollFrame(500));
    assertNull(tv.pollFrame(500));
    assertTrue(admin.isOpen());
    assertTrue(tv.isOpen());
    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"after_inbound_spam\"}");
    assertEquals("after_inbound_spam", admin.readJson().get("type"));
    assertEquals("after_inbound_spam", tv.readJson().get("type"));
  }

  @Test
  void roleSpoofingInsidePayloadDoesNotChangeRegisteredSocketRole() throws Exception {
    final SocketProbe tv = connectTv(ROOM_A);
    final SocketProbe admin = connectAdmin(ROOM_A);
    assertContract(tv.readJson(), "welcome");
    assertContract(admin.readJson(), "welcome");

    tv.send("{\"type\":\"role_change\",\"role\":\"admin\",\"socketPosition\":0}");
    assertNull(admin.pollFrame(300));
    assertNull(tv.pollFrame(300));

    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"admin_only_after_role_spoof\"}");
    assertEquals("admin_only_after_role_spoof", admin.readJson().get("type"));
    assertNull(tv.pollFrame(250), "TV must not become admin through client-supplied role JSON");
  }

  @Test
  void manuallyBroadcastUnknownServerFrameStillStaysRoomScopedAndValidJson() throws Exception {
    final SocketProbe adminA = connectAdmin(ROOM_A);
    final SocketProbe tvA = connectTv(ROOM_A);
    final SocketProbe adminB = connectAdmin(ROOM_B);
    assertContract(adminA.readJson(), "welcome");
    assertContract(tvA.readJson(), "welcome");
    assertContract(adminB.readJson(), "welcome");

    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"unknown_future_v1_extension\",\"safe\":true}");

    final Map<?, ?> adminFrame = adminA.readJson();
    final Map<?, ?> tvFrame = tvA.readJson();
    assertEquals("unknown_future_v1_extension", adminFrame.get("type"));
    assertEquals(true, adminFrame.get("safe"));
    assertEquals("unknown_future_v1_extension", tvFrame.get("type"));
    assertEquals(true, tvFrame.get("safe"));
    assertNull(adminB.pollFrame(300), "unknown future frames must still obey room isolation");
  }
}
