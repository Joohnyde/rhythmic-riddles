package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-integration")
@Tag("ws-fast")
@DisplayName("Real WebSocket integration behavior")
class RealWebSocketIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void acceptedConnectReceivesExactlyOneWelcomeFrame() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);

    final Map<?, ?> welcome = admin.readJson();
    assertEquals("welcome", welcome.get("type"));
    assertEquals(ROOM_A, welcome.get("roomCode"));
    assertNull(admin.pollFrame(250), "accepted connect must not receive duplicate welcome frames");
  }

  @Test
  void rejectedConnectDoesNotRegisterSessionAndDoesNotReceiveFrame() throws DerivedException {
    final SocketProbe rejected = new SocketProbe();

    assertFalse(connectPossiblyRejected(0, "MISS", rejected));
    assertNull(rejected.pollFrame(250), "rejected handshakes must not leak any websocket frame");
    assertFalse(sessionRegistry.isAdminPresent("MISS"));
    verify(gameService, never()).contextFetch("MISS");
  }

  @Test
  void reconnectingAdminReceivesRecoveryWelcomeAfterRealDisconnect() throws Exception {
    final SocketProbe first = connectAdmin(ROOM_A);
    assertEquals("welcome", first.readJson().get("type"));
    first.close(CloseStatus.GOING_AWAY);
    assertEventuallyFalse(() -> sessionRegistry.isAdminPresent(ROOM_A));

    final SocketProbe reconnected = connectAdmin(ROOM_A);

    final Map<?, ?> recovery = reconnected.readJson();
    assertEquals("welcome", recovery.get("type"));
    assertEquals(ROOM_A, recovery.get("roomCode"));
    assertNull(
        reconnected.pollFrame(250), "reconnected admin should receive one recovery welcome only");
  }

  @Test
  void duplicateAdminConnectIsRejectedWithoutReplacingExistingSession() throws Exception {
    final SocketProbe first = connectAdmin(ROOM_A);
    assertEquals("welcome", first.readJson().get("type"));
    final SocketProbe duplicate = new SocketProbe();

    connectPossiblyRejected(0, ROOM_A, duplicate);

    assertNull(
        duplicate.pollFrame(300), "duplicate client must not receive a recovery/welcome frame");
    assertTrue(sessionRegistry.isAdminPresent(ROOM_A));
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"still_original\"}");
    assertEquals("still_original", first.readJson().get("type"));
  }

  @Test
  void existingTvDoesNotReceiveDuplicateRecoveryFrameWhenDuplicateTvAttemptsToConnect()
      throws Exception {
    final SocketProbe tv = connectTv(ROOM_A);
    assertEquals("welcome", tv.readJson().get("type"));
    final SocketProbe duplicateTv = new SocketProbe();

    connectPossiblyRejected(1, ROOM_A, duplicateTv);

    assertNull(duplicateTv.pollFrame(300));
    assertNull(tv.pollFrame(300), "existing TV must not receive a duplicate recovery frame");
    broadcastGateway.toTv(ROOM_A, "{\"type\":\"tv_slot_intact\"}");
    assertEquals("tv_slot_intact", tv.readJson().get("type"));
  }

  @Test
  void roomBGetsNothingWhenRoomAReceivesBroadcasts() throws Exception {
    final SocketProbe adminA = connectAdmin(ROOM_A);
    final SocketProbe tvA = connectTv(ROOM_A);
    final SocketProbe adminB = connectAdmin(ROOM_B);
    adminA.readJson();
    tvA.readJson();
    adminB.readJson();

    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"room_a_only\",\"value\":42}");

    assertEquals("room_a_only", adminA.readJson().get("type"));
    assertEquals("room_a_only", tvA.readJson().get("type"));
    assertNull(adminB.pollFrame(300), "room B must not receive room A frames");
  }

  @Test
  void manualWireFramesArriveAsValidJsonOnBothAdminAndTv() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();

    broadcastGateway.broadcast(ROOM_A, "{\"type\":\"custom_probe\",\"nested\":{\"ok\":true}}");

    assertEquals(true, ((Map<?, ?>) admin.readJson().get("nested")).get("ok"));
    assertEquals(true, ((Map<?, ?>) tv.readJson().get("nested")).get("ok"));
  }

  @Test
  void pauseAnswerErrorSolvedRepeatRevealAndNextArriveCorrectlySerialized()
      throws DerivedException, Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final Fixture fixture = fixture();

    stubRound(fixture);

    interruptService.interrupt(ROOM_A, fixture.team().getId());
    assertPause(admin.readJson(), fixture.team().getId());
    assertPause(tv.readJson(), fixture.team().getId());

    interruptService.answer(fixture.teamInterrupt().getId(), new AnswerRequest(true), ROOM_A);
    assertAnswer(admin.readJson(), fixture.team().getId(), fixture.currentSchedule().getId(), true);
    assertAnswer(tv.readJson(), fixture.team().getId(), fixture.currentSchedule().getId(), true);

    interruptService.resolveErrors(fixture.currentSchedule().getId(), ROOM_A);
    assertErrorSolved(admin.readJson(), 4);
    assertErrorSolved(tv.readJson(), 4);

    scheduleService.replaySong(fixture.currentSchedule().getId(), ROOM_A);
    assertRepeat(admin.readJson(), 21.5d);
    assertRepeat(tv.readJson(), 21.5d);

    scheduleService.revealAnswer(fixture.currentSchedule().getId(), ROOM_A);
    assertOnlyType(admin.readJson(), "song_reveal");
    assertOnlyType(tv.readJson(), "song_reveal");

    scheduleService.progress(ROOM_A);
    assertNext(admin.readJson(), fixture.nextSchedule());
    assertNext(tv.readJson(), fixture.nextSchedule());
  }

  @Test
  void unknownRoomIsRejectedWithoutFrameOrRegistryEntry() throws DerivedException {
    final SocketProbe rejected = new SocketProbe();

    assertFalse(connectPossiblyRejected(0, "BAD", rejected));

    assertNull(rejected.pollFrame(250));
    assertFalse(sessionRegistry.isAdminPresent("BAD"));
    verify(gameService, never()).contextFetch("BAD");
  }

  @Test
  void invalidSocketPositionIsRejectedBeforeContextRecovery() throws DerivedException {
    final SocketProbe rejected = new SocketProbe();

    assertFalse(connectPossiblyRejected(9, ROOM_A, rejected));

    assertNull(rejected.pollFrame(250));
    assertFalse(sessionRegistry.isAdminPresent("9" + ROOM_A));
    verify(gameService, never()).contextFetch("9" + ROOM_A);
  }

  @Test
  void queryOnlyOrMissingPathPayloadIsRejectedWithoutAnyApplicationFrame() {
    final SocketProbe queryOnly = new SocketProbe();
    final SocketProbe missingPathPayload = new SocketProbe();

    assertFalse(
        connectUrlPossiblyRejected("ws://localhost:" + port + "/ws?room=" + ROOM_A, queryOnly));
    assertFalse(connectUrlPossiblyRejected("ws://localhost:" + port + "/ws/", missingPathPayload));

    assertNull(queryOnly.pollFrame(250));
    assertNull(missingPathPayload.pollFrame(250));
    assertFalse(sessionRegistry.isAdminPresent(""));
  }

  @Test
  void malformedClientJsonIsIgnoredAndConnectionRemainsUsable() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    assertEquals("welcome", admin.readJson().get("type"));

    admin.send("{this-is-not-json");

    assertNull(admin.pollFrame(300), "malformed inbound client JSON must not create server noise");
    assertTrue(
        admin.isOpen(), "server should not kill an otherwise valid socket for ignored input");
    broadcastGateway.toAdmin(ROOM_A, "{\"type\":\"after_malformed\"}");
    assertEquals("after_malformed", admin.readJson().get("type"));
  }

  @Test
  void unsupportedClientMessageTypeIsIgnoredAndConnectionRemainsUsable() throws Exception {
    final SocketProbe tv = connectTv(ROOM_A);
    assertEquals("welcome", tv.readJson().get("type"));

    tv.send("{\"type\":\"unsupported_admin_command\",\"payload\":{\"x\":1}}");

    assertNull(
        tv.pollFrame(300), "unsupported inbound message type must not be echoed or broadcast");
    assertTrue(tv.isOpen());
    broadcastGateway.toTv(ROOM_A, "{\"type\":\"after_unsupported\"}");
    assertEquals("after_unsupported", tv.readJson().get("type"));
  }

  @Test
  void miniRoundEmitsFrontendContractInOrderToAdminAndTvOnly() throws DerivedException, Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    final SocketProbe roomB = connectAdmin(ROOM_B);
    admin.readJson();
    tv.readJson();
    roomB.readJson();
    final Fixture fixture = fixture();
    stubRound(fixture);

    scheduleService.progress(ROOM_A);
    assertNext(admin.readJson(), fixture.nextSchedule());
    assertNext(tv.readJson(), fixture.nextSchedule());

    interruptService.interrupt(ROOM_A, fixture.team().getId());
    assertPause(admin.readJson(), fixture.team().getId());
    assertPause(tv.readJson(), fixture.team().getId());

    interruptService.answer(fixture.teamInterrupt().getId(), new AnswerRequest(false), ROOM_A);
    assertAnswer(
        admin.readJson(), fixture.team().getId(), fixture.currentSchedule().getId(), false);
    assertAnswer(tv.readJson(), fixture.team().getId(), fixture.currentSchedule().getId(), false);

    scheduleService.revealAnswer(fixture.currentSchedule().getId(), ROOM_A);
    assertOnlyType(admin.readJson(), "song_reveal");
    assertOnlyType(tv.readJson(), "song_reveal");

    scheduleService.progress(ROOM_A);
    assertNext(admin.readJson(), fixture.nextSchedule());
    assertNext(tv.readJson(), fixture.nextSchedule());

    interruptService.resolveErrors(fixture.currentSchedule().getId(), ROOM_A);
    assertErrorSolved(admin.readJson(), 4);
    assertErrorSolved(tv.readJson(), 4);
    assertNull(roomB.pollFrame(300), "full room A game flow must not leak to room B");
  }
}
