package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ws-contract")
@Tag("ws-fast")
@DisplayName("Strict frontend WebSocket contract validation")
class WebSocketContractIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void welcomeContractIsStableAndContainsNoUndocumentedFields() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);

    assertContract(admin.readJson(), "welcome");
    assertNull(admin.pollFrame(250), "welcome must be emitted exactly once");
  }

  @Test
  void allCoreOutboundFramesMatchJsonFixtureContracts() throws DerivedException, Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");
    final Fixture fixture = fixture();
    stubRound(fixture);

    interruptService.interrupt(ROOM_A, fixture.team().getId());
    assertContract(admin.readJson(), "pause");
    assertContract(tv.readJson(), "pause");

    interruptService.answer(fixture.teamInterrupt().getId(), new AnswerRequest(true), ROOM_A);
    assertContract(admin.readJson(), "answer");
    assertContract(tv.readJson(), "answer");

    interruptService.resolveErrors(fixture.currentSchedule().getId(), ROOM_A);
    assertContract(admin.readJson(), "error_solved");
    assertContract(tv.readJson(), "error_solved");

    scheduleService.replaySong(fixture.currentSchedule().getId(), ROOM_A);
    assertContract(admin.readJson(), "song_repeat");
    assertContract(tv.readJson(), "song_repeat");

    scheduleService.revealAnswer(fixture.currentSchedule().getId(), ROOM_A);
    assertContract(admin.readJson(), "song_reveal");
    assertContract(tv.readJson(), "song_reveal");

    scheduleService.progress(ROOM_A);
    assertContract(admin.readJson(), "song_next");
    assertContract(tv.readJson(), "song_next");
  }

  @Test
  void tvCannotTriggerPrivilegedServerEventsBySendingCommandJson() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();

    tv.send("{\"type\":\"pause\",\"answeringTeamId\":\"malicious\"}");
    tv.send("{\"type\":\"song_next\"}");

    assertNull(admin.pollFrame(350), "TV-originated privileged command JSON must not reach admin");
    assertNull(tv.pollFrame(350), "server must not echo or accept TV-originated privileged commands");
    assertTrue(sessionRegistry.areBothPresent(ROOM_A));
  }
}
