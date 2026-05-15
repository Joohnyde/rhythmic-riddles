package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-lifecycle")
@Tag("ws-fast")
@DisplayName("WebSocket lifecycle, duplicates, ordering, and race behavior")
class WebSocketLifecycleIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void duplicateAdminAndTvAttemptsDoNotReceiveBroadcastsAndOriginalsReceiveExactlyOnce()
      throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final SocketProbe duplicateAdmin = new SocketProbe();
    final SocketProbe duplicateTv = new SocketProbe();

    connectPossiblyRejected(0, ROOM_A, duplicateAdmin);
    connectPossiblyRejected(1, ROOM_A, duplicateTv);
    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"single_delivery\"}");

    assertEquals("single_delivery", admin.readJson().get("type"));
    assertEquals("single_delivery", tv.readJson().get("type"));
    assertNull(admin.pollFrame(250), "admin must receive broadcast exactly once");
    assertNull(tv.pollFrame(250), "TV must receive broadcast exactly once");
    assertNull(duplicateAdmin.pollFrame(250), "rejected duplicate admin must receive nothing");
    assertNull(duplicateTv.pollFrame(250), "rejected duplicate TV must receive nothing");
  }

  @Test
  void disconnectingTvCleansRegistryAndDoesNotAffectAdminDelivery() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();

    tv.close(CloseStatus.NORMAL);
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    assertTrue(sessionRegistry.isAdminPresent(ROOM_A));
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"admin_after_tv_disconnect\"}");
    broadcastGateway.toTv(ROOM_A, "{\"type\":\"must_not_throw_or_deliver\"}");
    assertEquals("admin_after_tv_disconnect", admin.readJson().get("type"));
    assertNull(tv.pollFrame(250));
  }

  @Test
  void broadcastFramesPreserveServerSendOrderForBothClients() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final List<String> expected = List.of("pause", "answer", "song_reveal", "song_next");

    for (String type : expected) {
      broadcastGateway.broadcast(ROOM_A, "{\"type\":\"" + type + "\"}");
    }

    for (String type : expected) {
      assertEquals(type, admin.readJson().get("type"));
    }
    for (String type : expected) {
      assertEquals(type, tv.readJson().get("type"));
    }
    assertNull(admin.pollFrame(250));
    assertNull(tv.pollFrame(250));
  }

  @Test
  void simultaneousAdminConnectionsAllowOnlyOneWinnerAndNoDuplicateWelcome() throws Exception {
    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final SocketProbe first = new SocketProbe();
    final SocketProbe second = new SocketProbe();
    try {
      final Callable<Boolean> c1 = () -> connectPossiblyRejected(0, ROOM_A, first);
      final Callable<Boolean> c2 = () -> connectPossiblyRejected(0, ROOM_A, second);
      final Future<Boolean> f1 = executor.submit(c1);
      final Future<Boolean> f2 = executor.submit(c2);

      f1.get(3, TimeUnit.SECONDS);
      f2.get(3, TimeUnit.SECONDS);

      final String firstFrame = first.pollFrame(1000);
      final String secondFrame = second.pollFrame(1000);
      final int welcomes = (firstFrame == null ? 0 : 1) + (secondFrame == null ? 0 : 1);
      assertEquals(1, welcomes, "two racing admins must produce exactly one welcome frame total");

      final SocketProbe winner = firstFrame != null ? first : second;
      final SocketProbe loser = firstFrame != null ? second : first;
      final String winnerFrame = firstFrame != null ? firstFrame : secondFrame;
      assertEquals("welcome", mapper.readValue(winnerFrame, java.util.HashMap.class).get("type"));
      assertNull(winner.pollFrame(250), "winner must get exactly one welcome");
      assertNull(loser.pollFrame(250), "losing race participant must get no frame");
    } finally {
      executor.shutdownNow();
      first.close(CloseStatus.NORMAL);
      second.close(CloseStatus.NORMAL);
    }
  }

  @Test
  void adminCanReconnectOnlyAfterOldSessionCleanupCompletes() throws Exception {
    final SocketProbe original = connectAdmin(ROOM_A);
    assertEquals("welcome", original.readJson().get("type"));

    original.close(CloseStatus.NORMAL);
    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));
    final SocketProbe reconnected = connectAdmin(ROOM_A);

    assertEquals("welcome", reconnected.readJson().get("type"));
    assertNull(reconnected.pollFrame(250));
  }

  @Test
  void abruptTvNetworkCloseCleansRegistryAndTriggersSystemPauseForAdmin()
      throws DerivedException, Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final Fixture fixture = fixture();
    stubRound(fixture);
    org.mockito.Mockito.when(gameService.getStage(ROOM_A)).thenReturn(2);

    tv.close(CloseStatus.GOING_AWAY);
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    final java.util.Map<?, ?> pause = admin.readJson();
    assertContract(pause, "pause");
    assertEquals("null", pause.get("answeringTeamId"));
    assertTrue(sessionRegistry.isAdminPresent(ROOM_A));
  }

  @Test
  void rapidTvReconnectDoesNotDuplicateGameplayFramesOrCorruptRoomState() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe originalTv = connectTv(ROOM_A);
    admin.readJson();
    originalTv.readJson();

    final SocketProbe duplicateTv = new SocketProbe();
    final boolean duplicateAccepted = connectPossiblyRejected(1, ROOM_A, duplicateTv);

    if (duplicateAccepted) {
      final String optionalWelcome = duplicateTv.pollFrame(250);
      if (optionalWelcome != null) {
        assertContract(mapper.readValue(optionalWelcome, java.util.Map.class), "welcome");
      }
    }

    broadcastGateway.toTv(ROOM_A, "{\"type\":\"tv_after_rapid_reconnect\"}");

    final int deliveredToOriginal = countFramesOfType(originalTv, "tv_after_rapid_reconnect", 500);
    final int deliveredToDuplicate =
        duplicateAccepted ? countFramesOfType(duplicateTv, "tv_after_rapid_reconnect", 500) : 0;

    assertEquals(
        1,
        deliveredToOriginal + deliveredToDuplicate,
        "A rapid TV reconnect must not duplicate gameplay delivery");

    originalTv.close(CloseStatus.NORMAL);
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    final SocketProbe recoveredTv = connectTv(ROOM_A);
    assertContract(recoveredTv.readJson(), "welcome");

    broadcastGateway.toTv(ROOM_A, "{\"type\":\"tv_after_cleanup_recovery\"}");
    assertEquals("tv_after_cleanup_recovery", recoveredTv.readJson().get("type"));
    assertNull(originalTv.pollFrame(150));
  }
}
