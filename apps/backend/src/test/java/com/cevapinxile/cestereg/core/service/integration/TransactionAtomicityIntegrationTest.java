package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Import({InterruptServiceImpl.class, FixedTestClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionAtomicityIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;

  @Autowired private InterruptService interruptService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private TeamService teamService;
  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    DatabaseTestCleaner.clear(jdbc);
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @AfterEach
  void tearDown() {
    DatabaseTestCleaner.clear(jdbc);
  }

  @Test
  void answerRollsBackAllPersistedChangesWhenLateDomainFailureOccurs() throws Exception {
    final String roomCode = "TATM";
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Atomic Team", "atomic.png");
    final UUID albumId = fixture.album("Atomic Album");
    final UUID songId = fixture.song("Artist", "Atomic Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, NOW.minusSeconds(8), null);
    final UUID answerId =
        fixture.interrupt(scheduleId, teamId, NOW.minusSeconds(4), null, null, null);
    final UUID systemPause =
        fixture.interrupt(scheduleId, null, NOW.minusSeconds(2), null, null, 1);

    when(presenceGateway.areBothPresent(roomCode)).thenReturn(true);
    when(teamService.getTeamPoints(teamId, roomCode)).thenReturn(0);
    doThrow(new InvalidReferencedObjectException("forced late persistence boundary failure"))
        .when(teamService)
        .saveTeamAnswer(eq(teamId), eq(scheduleId), eq(30), eq(roomCode));

    assertThrows(
        InvalidReferencedObjectException.class,
        () -> interruptService.answer(answerId, new AnswerRequest(true), roomCode));

    assertNull(column("interrupt", "resolved_at", answerId, LocalDateTime.class));
    assertNull(column("interrupt", "is_correct", answerId, Boolean.class));
    assertNull(column("interrupt", "score_or_scenario_id", answerId, Integer.class));
    assertNull(column("interrupt", "resolved_at", systemPause, LocalDateTime.class));
    assertNull(column("schedule", "revealed_at", scheduleId, LocalDateTime.class));
  }

  private <T> T column(
      final String table, final String column, final UUID id, final Class<T> type) {
    return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", type, id);
  }
}
