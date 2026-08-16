package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.core.service.impl.GameServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.ScheduleServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import({
  GameServiceImpl.class,
  TeamServiceImpl.class,
  InterruptServiceImpl.class,
  ScheduleServiceImpl.class,
  FixedTestClockConfiguration.class
})
class RoomOwnershipIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;

  @Autowired private ScheduleService scheduleService;
  @Autowired private InterruptService interruptService;
  @Autowired private TeamService teamService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private CategoryService categoryService;
  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
    when(presenceGateway.areBothPresent(anyString())).thenReturn(true);
  }

  @Nested
  class ScheduleOwnership {

    @Test
    void replaySongRejectsScheduleOwnedByAnotherRoom() {
      fixture.game("OWA1", 2, 3, 2);
      final PersistedRound otherRoom = round("OWB1");
      final LocalDateTime originalStartedAt = startedAt(otherRoom.scheduleId());

      assertThrows(
          DerivedException.class,
          () -> scheduleService.replaySong(otherRoom.scheduleId(), "OWA1"));

      assertEquals(originalStartedAt, startedAt(otherRoom.scheduleId()));
    }

    @Test
    void revealAnswerRejectsScheduleOwnedByAnotherRoom() {
      fixture.game("OWA2", 2, 3, 2);
      final PersistedRound otherRoom = round("OWB2");

      assertThrows(
          DerivedException.class,
          () -> scheduleService.revealAnswer(otherRoom.scheduleId(), "OWA2"));

      assertNull(revealedAt(otherRoom.scheduleId()));
    }
  }

  @Nested
  class InterruptOwnership {

    @Test
    void resolveErrorsRejectsScheduleOwnedByAnotherRoom() {
      fixture.game("OWA3", 2, 3, 2);
      final PersistedRound otherRoom = round("OWB3");
      final UUID systemPause =
          fixture.interrupt(
              otherRoom.scheduleId(), null, NOW.minusSeconds(2), null, null, 1);

      assertThrows(
          DerivedException.class,
          () -> interruptService.resolveErrors(otherRoom.scheduleId(), "OWA3"));

      assertNull(resolvedAt(systemPause));
    }

    @Test
    void findCorrectAnswerRejectsScheduleOwnedByAnotherRoom() {
      fixture.game("OWA4", 2, 3, 2);
      final PersistedRound otherRoom = round("OWB4");
      fixture.interrupt(
          otherRoom.scheduleId(),
          otherRoom.teamId(),
          NOW.minusSeconds(3),
          NOW.minusSeconds(2),
          true,
          30);

      assertThrows(
          DerivedException.class,
          () -> interruptService.findCorrectAnswer(otherRoom.scheduleId(), "OWA4"));
    }

    @Test
    void answerRejectsInterruptWhoseTeamAndScheduleBelongToDifferentRooms() {
      final UUID localGame = fixture.game("OWA6", 2, 3, 2);
      final UUID localTeam = fixture.team(localGame, "Local Team", "local.png");
      final PersistedRound foreignRound = round("OWB6");
      final UUID corruptAnswer =
          fixture.interrupt(
              foreignRound.scheduleId(),
              localTeam,
              NOW.minusSeconds(2),
              null,
              null,
              null);

      assertThrows(
          DerivedException.class,
          () -> interruptService.answer(corruptAnswer, new AnswerRequest(true), "OWA6"));

      assertNull(resolvedAt(corruptAnswer));
      assertNull(revealedAt(foreignRound.scheduleId()));
    }
  }

  @Nested
  class TeamOwnership {

    @Test
    void kickTeamRejectsTeamOwnedByAnotherRoom() {
      fixture.game("OWA5", 0, 3, 2);
      final UUID otherGame = fixture.game("OWB5", 0, 3, 2);
      final UUID otherTeam = fixture.team(otherGame, "Other Team", "other.png");

      assertThrows(
          DerivedException.class,
          () -> teamService.kickTeam(otherTeam.toString(), "OWA5"));

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM team WHERE id = ?", Integer.class, otherTeam));
    }
  }

  private PersistedRound round(final String roomCode) {
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Owner Team", "owner.png");
    final UUID albumId = fixture.album("Owner Album " + roomCode);
    final UUID songId = fixture.song("Artist", "Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, NOW.minusSeconds(8), null);
    return new PersistedRound(teamId, scheduleId);
  }

  private LocalDateTime startedAt(final UUID scheduleId) {
    return jdbc.queryForObject(
        "SELECT started_at FROM schedule WHERE id = ?", LocalDateTime.class, scheduleId);
  }

  private LocalDateTime revealedAt(final UUID scheduleId) {
    return jdbc.queryForObject(
        "SELECT revealed_at FROM schedule WHERE id = ?", LocalDateTime.class, scheduleId);
  }

  private LocalDateTime resolvedAt(final UUID interruptId) {
    return jdbc.queryForObject(
        "SELECT resolved_at FROM interrupt WHERE id = ?", LocalDateTime.class, interruptId);
  }

  private record PersistedRound(UUID teamId, UUID scheduleId) {}
}
