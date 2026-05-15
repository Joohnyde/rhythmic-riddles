package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-chaos")
@Tag("ws-nightly")
@DisplayName("WebSocket soak and chaos-style behavior")
class WebSocketSoakChaosIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void scaledReconnectSoakLeavesRegistryReusableAndDoesNotDuplicateFinalBroadcast() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    admin.readJson();

    for (int i = 0; i < 20; i++) {
      final SocketProbe tv = connectTv(ROOM_A);
      assertContract(tv.readJson(), "welcome");
      broadcastGateway.toTv(ROOM_A, "{\"type\":\"soak_probe\",\"sequence\":" + i + "}");
      assertEquals(i, ((Number) tv.readJson().get("sequence")).intValue());
      tv.close(i % 2 == 0 ? CloseStatus.NORMAL : CloseStatus.GOING_AWAY);
      assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));
    }

    final SocketProbe finalTv = connectTv(ROOM_A);
    assertContract(finalTv.readJson(), "welcome");
    broadcastGateway.toTv(ROOM_A, "{\"type\":\"after_soak\"}");
    assertEquals("after_soak", finalTv.readJson().get("type"));
    assertNull(finalTv.pollFrame(250));
  }

  @Test
  void forcedDisconnectsDuringBurstDoNotPoisonSubsequentCleanReconnect() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();

    for (int i = 0; i < 5; i++) {
      broadcastGateway.broadcast(ROOM_A, "{\"type\":\"before_forced_close\",\"sequence\":" + i + "}");
    }
    tv.close(CloseStatus.GOING_AWAY);
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    for (int i = 0; i < 5; i++) {
      final Map<?, ?> frame = admin.readJson();
      assertEquals("before_forced_close", frame.get("type"));
      assertEquals(i, ((Number) frame.get("sequence")).intValue());
    }

    final SocketProbe recoveredTv = connectTv(ROOM_A);
    assertContract(recoveredTv.readJson(), "welcome");
    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"after_forced_close_recovery\"}");
    assertEquals("after_forced_close_recovery", admin.readJson().get("type"));
    assertEquals("after_forced_close_recovery", recoveredTv.readJson().get("type"));
  }

  @Test
  void malformedUtfLikeAndFragmentLikeApplicationMessagesAreIgnoredWithoutClosingSocket()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    assertContract(admin.readJson(), "welcome");

    admin.send("{\"type\":");
    admin.send("\"partial\"}");
    admin.send("[not-a-supported-command]");

    assertNull(admin.pollFrame(300));
    assertTrue(admin.isOpen());
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"after_fragment_like_noise\"}");
    assertEquals("after_fragment_like_noise", admin.readJson().get("type"));
  }

  @Test
  void frozenClientThatDoesNotDrainImmediatelyCanResumeAndObserveOrderedFrames() throws Exception {
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(tv.readJson(), "welcome");
    final int queuedFrames = 25;

    for (int i = 0; i < queuedFrames; i++) {
      broadcastGateway.toTv(ROOM_A, "{\"type\":\"frozen_client_queue\",\"sequence\":" + i + "}");
    }

    Thread.sleep(150);
    assertTrue(tv.isOpen());
    for (int i = 0; i < queuedFrames; i++) {
      final Map<?, ?> frame = tv.readJson();
      assertEquals("frozen_client_queue", frame.get("type"));
      assertEquals(i, ((Number) frame.get("sequence")).intValue());
    }
    assertNull(tv.pollFrame(250));
  }

  @Test
  void oversizedValidClientPayloadIsIgnoredOrClosedButRoomRecoversForFutureServerFrames()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    final String largeIgnoredPayload =
        "{\"type\":\"ignored_large_payload\",\"payload\":\"" + "x".repeat(32_000) + "\"}";

    try {
      admin.send(largeIgnoredPayload);
    } catch (IllegalStateException ex) {
      // Acceptable defensive behavior: the container may close oversized-message sessions.
    }

    if (admin.isOpen()) {
      assertNull(admin.pollFrame(300), "oversized inbound payload must not create app frames");
      broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"after_large_ignored_payload\"}");

      final String maybeDelivered = admin.pollFrame(700);
      if (maybeDelivered != null) {
        assertEquals("after_large_ignored_payload", mapper.readValue(maybeDelivered, Map.class).get("type"));
        return;
      }

      // The close can happen asynchronously after isOpen() was checked, so fall through to recovery.
      admin.close(CloseStatus.GOING_AWAY);
      closeRegistrySession(ROOM_A, true);
    }

    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
    final SocketProbe recovered = connectAdmin(ROOM_A);
    assertContract(recovered.readJson(), "welcome");
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"after_large_payload_recovery\"}");
    assertEquals("after_large_payload_recovery", recovered.readJson().get("type"));
  }

  @Test
  void serverMemoryResetScenarioIsDocumentedAsRequiringFreshHandshakeNotInPlaceRecovery()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();

    closeRegistrySession(ROOM_A, true);
    closeRegistrySession(ROOM_A, false);
    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    final SocketProbe freshAdmin = connectAdmin(ROOM_A);
    final SocketProbe freshTv = connectTv(ROOM_A);
    assertContract(freshAdmin.readJson(), "welcome");
    assertContract(freshTv.readJson(), "welcome");
    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"after_registry_reset\"}");
    assertEquals("after_registry_reset", freshAdmin.readJson().get("type"));
    assertEquals("after_registry_reset", freshTv.readJson().get("type"));
  }

  @Test
  void repeatedAdminTvCleanReconnectCyclesKeepBothRolesUsable() throws DerivedException, Exception {
    final List<SocketProbe> closedProbes = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      final SocketProbe admin = connectAdmin(ROOM_A);
      final SocketProbe tv = connectTv(ROOM_A);
      assertContract(admin.readJson(), "welcome");
      assertContract(tv.readJson(), "welcome");

      broadcastGateway.broadcast(ROOM_A, "{\"type\":\"cycle_probe\",\"sequence\":" + i + "}");
      assertEquals(i, ((Number) admin.readJson().get("sequence")).intValue());
      assertEquals(i, ((Number) tv.readJson().get("sequence")).intValue());

      admin.close(CloseStatus.NORMAL);
      tv.close(CloseStatus.NORMAL);
      closedProbes.add(admin);
      closedProbes.add(tv);
      assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
      assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));
    }

    for (SocketProbe probe : closedProbes) {
      assertNull(probe.pollFrame(50));
    }
  }
}
