package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Import({InterruptServiceImpl.class, TeamServiceImpl.class, FixedTestClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScoreCacheConsistencyIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;

  @Autowired private InterruptService interruptService;
  @Autowired private TeamService teamService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
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
  void answerBroadcastRunsOnlyAfterCommittedStateIsVisible() throws Exception {
    final String roomCode = "SCAC";
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Cache Team", "cache.png");
    final UUID albumId = fixture.album("Cache Album");
    final UUID songId = fixture.song("Artist", "Cache Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, NOW.minusSeconds(8), null);
    final UUID answerId =
        fixture.interrupt(scheduleId, teamId, NOW.minusSeconds(2), null, null, null);
    final AtomicBoolean committedStateVisibleDuringBroadcast = new AtomicBoolean(false);

    when(presenceGateway.areBothPresent(roomCode)).thenReturn(true);
    assertEquals(0, teamService.getTeamPoints(teamId, roomCode));
    doAnswer(
            invocation -> {
              final Boolean correct =
                  jdbc.queryForObject(
                      "SELECT is_correct FROM interrupt WHERE id = ?", Boolean.class, answerId);
              final LocalDateTime revealedAt =
                  jdbc.queryForObject(
                      "SELECT revealed_at FROM schedule WHERE id = ?",
                      LocalDateTime.class,
                      scheduleId);
              committedStateVisibleDuringBroadcast.set(
                  Boolean.TRUE.equals(correct) && NOW.equals(revealedAt));
              return null;
            })
        .when(broadcastGateway)
        .broadcast(eq(roomCode), anyString());

    interruptService.answer(answerId, new AnswerRequest(true), roomCode);

    assertTrue(committedStateVisibleDuringBroadcast.get());
    assertEquals(30, teamService.getTeamPoints(teamId, roomCode));
  }

  @Test
  void rolledBackAnswerDoesNotBroadcastOrAdvanceScoreCache() throws Exception {
    final String roomCode = "SCRB";
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Rollback Team", "rollback.png");
    final UUID albumId = fixture.album("Rollback Album");
    final UUID songId = fixture.song("Artist", "Rollback Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, NOW.minusSeconds(8), null);
    final UUID answerId =
        fixture.interrupt(scheduleId, teamId, NOW.minusSeconds(2), null, null, null);

    when(presenceGateway.areBothPresent(roomCode)).thenReturn(true);
    assertEquals(0, teamService.getTeamPoints(teamId, roomCode));

    final TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(
        status -> {
          try {
            interruptService.answer(answerId, new AnswerRequest(true), roomCode);
          } catch (final Exception exception) {
            throw new IllegalStateException(exception);
          }
          status.setRollbackOnly();
        });

    verify(broadcastGateway, never()).broadcast(eq(roomCode), anyString());
    assertNull(
        jdbc.queryForObject(
            "SELECT is_correct FROM interrupt WHERE id = ?", Boolean.class, answerId));
    assertNull(
        jdbc.queryForObject(
            "SELECT revealed_at FROM schedule WHERE id = ?", LocalDateTime.class, scheduleId));
    assertEquals(0, teamService.getTeamPoints(teamId, roomCode));
  }
}
