package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cevapinxile.cestereg.common.exception.GuessNotAllowedException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import({InterruptServiceImpl.class, TeamServiceImpl.class, FixedTestClockConfiguration.class})
class InterruptInvariantIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;

  @Autowired private InterruptService interruptService;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;
  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void teamBuzzCannotCreatePartialOverlapWhileSystemPauseIsActive() {
    final Round round = round("IPAU");
    fixture.interrupt(round.scheduleId(), null, NOW.minusSeconds(3), null, null, 1);
    final int before = interruptCount(round.scheduleId());
    final GuessNotAllowedException exception =
        assertThrows(
            GuessNotAllowedException.class,
            () -> interruptService.interrupt(round.roomCode(), round.teamId()));

    assertEquals("The game is paused", exception.getMessage());
    assertEquals(before, interruptCount(round.scheduleId()));
  }

  @Test
  void resolvedSystemPauseAllowsLaterTeamBuzzAsDisjointInterval() throws Exception {
    final Round round = round("IDIS");
    fixture.interrupt(round.scheduleId(), null, NOW.minusSeconds(5), NOW.minusSeconds(3), null, 1);

    interruptService.interrupt(round.roomCode(), round.teamId());
    assertEquals(2, interruptCount(round.scheduleId()));
    assertEquals(
        NOW,
        jdbc.queryForObject(
            "SELECT arrived_at FROM interrupt WHERE schedule_id = ? AND team_id = ?",
            LocalDateTime.class,
            round.scheduleId(),
            round.teamId()));
  }

  @Test
  void savePreviousScenarioUpdatesLatestUnresolvedSystemPause() throws Exception {
    final Round round = round("IUPS");
    final UUID pauseId =
        fixture.interrupt(round.scheduleId(), null, NOW.minusSeconds(3), null, null, 1);

    interruptService.savePreviousScenario(2, round.roomCode());

    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT score_or_scenario_id FROM interrupt WHERE id = ?", Integer.class, pauseId));
  }

  @Test
  void savePreviousScenarioDoesNotModifyResolvedSystemPause() throws Exception {
    final Round round = round("IRPS");
    final UUID pauseId =
        fixture.interrupt(
            round.scheduleId(), null, NOW.minusSeconds(3), NOW.minusSeconds(1), null, 1);

    interruptService.savePreviousScenario(2, round.roomCode());

    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT score_or_scenario_id FROM interrupt WHERE id = ?", Integer.class, pauseId));
  }

  private Round round(final String roomCode) {
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Invariant Team", "team.png");
    final UUID albumId = fixture.album("Invariant Album");
    final UUID songId = fixture.song("Artist", "Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, NOW.minusSeconds(8), null);
    return new Round(roomCode, teamId, scheduleId);
  }

  private int interruptCount(final UUID scheduleId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM interrupt WHERE schedule_id = ?", Integer.class, scheduleId);
  }

  private record Round(String roomCode, UUID teamId, UUID scheduleId) {}
}
