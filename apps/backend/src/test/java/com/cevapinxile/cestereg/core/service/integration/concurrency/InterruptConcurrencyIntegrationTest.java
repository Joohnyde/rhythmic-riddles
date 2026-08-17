package com.cevapinxile.cestereg.core.service.integration.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.RoomBusyException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
class InterruptConcurrencyIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;
  private static final int RACE_ROUNDS = 12;

  @Autowired private InterruptService interruptService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    DatabaseTestCleaner.clear(jdbc);
    fixture = new QuizPersistenceFixture(jdbc);
    when(presenceGateway.areBothPresent(anyString())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    DatabaseTestCleaner.clear(jdbc);
  }

  @Test
  void simultaneousTeamBuzzesPersistExactlyOneActiveAnsweringTeam() throws Exception {
    for (int roundIndex = 0; roundIndex < RACE_ROUNDS; roundIndex++) {
      final String roomCode = String.format("B%03d", roundIndex);
      final UUID gameId = fixture.game(roomCode, 2, 3, 2);
      final UUID firstTeam = fixture.team(gameId, "First " + roundIndex, "first.png");
      final UUID secondTeam = fixture.team(gameId, "Second " + roundIndex, "second.png");
      final UUID scheduleId = playingSchedule(gameId, firstTeam, roundIndex);
      clearInvocations(broadcastGateway);
      final List<InvocationResult> results =
          invokeSimultaneously(
              () -> interruptService.interrupt(roomCode, firstTeam),
              () -> interruptService.interrupt(roomCode, secondTeam));

      assertEquals(
          1L, successCount(results), "buzz race round " + roundIndex + " results=" + results);
      assertEquals(1, activeTeamInterruptCount(scheduleId));
    }
  }

  @Test
  void simultaneousDuplicateAnswersAllowExactlyOneResolution() throws Exception {
    for (int roundIndex = 0; roundIndex < RACE_ROUNDS; roundIndex++) {
      final String roomCode = String.format("A%03d", roundIndex);
      final UUID gameId = fixture.game(roomCode, 2, 3, 2);
      final UUID teamId = fixture.team(gameId, "Answer " + roundIndex, "answer.png");
      final UUID scheduleId = playingSchedule(gameId, teamId, 100 + roundIndex);
      final UUID answerId =
          fixture.interrupt(scheduleId, teamId, NOW.minusSeconds(2), null, null, null);
      clearInvocations(broadcastGateway);
      final List<InvocationResult> results =
          invokeSimultaneously(
              () -> interruptService.answer(answerId, new AnswerRequest(true), roomCode),
              () -> interruptService.answer(answerId, new AnswerRequest(true), roomCode));
      assertEquals(
          1L, successCount(results), "answer race round " + roundIndex + " results=" + results);
      assertEquals(
          Boolean.TRUE,
          jdbc.queryForObject(
              "SELECT is_correct FROM interrupt WHERE id = ?", Boolean.class, answerId));
      assertEquals(
          30,
          jdbc.queryForObject(
              "SELECT score_or_scenario_id FROM interrupt WHERE id = ?", Integer.class, answerId));
    }
  }

  @Test
  void systemInterruptWaitsForInFlightTeamBuzzAndIsNotLost() throws Exception {
    final String roomCode = "SWTB";
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Waiting Team", "waiting.png");
    final UUID scheduleId = playingSchedule(gameId, teamId, 300);
    final CountDownLatch teamTransactionOwnsRoom = new CountDownLatch(1);
    final CountDownLatch releaseTeamTransaction = new CountDownLatch(1);
    final CountDownLatch systemStarted = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final Future<?> teamTransaction =
          executor.submit(
              () ->
                  holdRoomLock(
                      roomCode,
                      teamTransactionOwnsRoom,
                      releaseTeamTransaction,
                      () ->
                          fixture.interrupt(
                              scheduleId, teamId, NOW.minusSeconds(2), null, null, null)));
      assertTrue(teamTransactionOwnsRoom.await(5, TimeUnit.SECONDS));
      final Future<InvocationResult> systemResult =
          executor.submit(
              () -> {
                systemStarted.countDown();
                return invoke(() -> interruptService.interrupt(roomCode, null));
              });
      assertTrue(systemStarted.await(5, TimeUnit.SECONDS));
      try {
        systemResult.get(250, TimeUnit.MILLISECONDS);
        throw new AssertionError("system interrupt should wait for the in-flight room transaction");
      } catch (final TimeoutException expected) {
        // Expected: system events block for the room lock rather than being discarded by NOWAIT.
      }

      releaseTeamTransaction.countDown();

      teamTransaction.get(5, TimeUnit.SECONDS);
      assertTrue(systemResult.get(5, TimeUnit.SECONDS).succeeded());
    }
    assertEquals(1, activeTeamInterruptCount(scheduleId));
    assertEquals(1, activeSystemInterruptCount(scheduleId));
    assertEquals(2, interruptCount(scheduleId));
  }

  @Test
  void systemInterruptWinsWhenItAlreadyOwnsTheRoomLock() throws Exception {
    final String roomCode = "SWIN";
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Losing Team", "losing.png");
    final UUID scheduleId = playingSchedule(gameId, teamId, 301);
    final CountDownLatch systemTransactionOwnsRoom = new CountDownLatch(1);
    final CountDownLatch releaseSystemTransaction = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final Future<?> systemTransaction =
          executor.submit(
              () ->
                  holdRoomLock(
                      roomCode,
                      systemTransactionOwnsRoom,
                      releaseSystemTransaction,
                      () ->
                          fixture.interrupt(scheduleId, null, NOW.minusSeconds(2), null, null, 1)));
      assertTrue(systemTransactionOwnsRoom.await(5, TimeUnit.SECONDS));
      final Future<InvocationResult> teamResult =
          executor.submit(() -> invoke(() -> interruptService.interrupt(roomCode, teamId)));
      final InvocationResult losingTeam = teamResult.get(5, TimeUnit.SECONDS);

      assertFalse(losingTeam.succeeded(), "team buzz must fail fast while system owns the room");
      assertTrue(losingTeam.failure() instanceof RoomBusyException);
      assertEquals(
          "Another request is already changing game SWIN.", losingTeam.failure().getMessage());
      releaseSystemTransaction.countDown();
      systemTransaction.get(5, TimeUnit.SECONDS);
    }
    assertEquals(0, activeTeamInterruptCount(scheduleId));
    assertEquals(1, activeSystemInterruptCount(scheduleId));
    assertEquals(1, interruptCount(scheduleId));
  }

  private void holdRoomLock(
      final String roomCode,
      final CountDownLatch lockAcquired,
      final CountDownLatch releaseLock,
      final ThrowingRunnable mutation) {
    final TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(
        status -> {
          jdbc.queryForObject(
              "SELECT id FROM game WHERE code = ? FOR UPDATE", UUID.class, roomCode);
          try {
            mutation.run();
            lockAcquired.countDown();
            if (!releaseLock.await(5, TimeUnit.SECONDS)) {
              throw new IllegalStateException("room lock release timed out");
            }
          } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
          } catch (final Exception exception) {
            throw new IllegalStateException(exception);
          }
        });
  }

  private UUID playingSchedule(final UUID gameId, final UUID teamId, final int discriminator) {
    final UUID albumId = fixture.album("Race Album " + discriminator);
    final UUID songId = fixture.song("Artist", "Race Song " + discriminator, 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    return fixture.schedule(categoryId, trackId, 1, NOW.minusSeconds(8), null);
  }

  private List<InvocationResult> invokeSimultaneously(
      final ThrowingRunnable first, final ThrowingRunnable second) throws Exception {
    final CountDownLatch ready = new CountDownLatch(2);
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final Future<InvocationResult> firstResult =
          executor.submit(() -> invoke(ready, start, first));
      final Future<InvocationResult> secondResult =
          executor.submit(() -> invoke(ready, start, second));

      assertTrue(ready.await(5, TimeUnit.SECONDS), "concurrent workers did not become ready");
      start.countDown();
      return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
    }
  }

  private InvocationResult invoke(
      final CountDownLatch ready, final CountDownLatch start, final ThrowingRunnable action) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        return InvocationResult.failure(new IllegalStateException("race start timed out"));
      }
      action.run();
      return InvocationResult.success();
    } catch (final Throwable throwable) {
      return InvocationResult.failure(throwable);
    }
  }

  private InvocationResult invoke(final ThrowingRunnable action) {
    try {
      action.run();
      return InvocationResult.success();
    } catch (final Throwable throwable) {
      return InvocationResult.failure(throwable);
    }
  }

  private long successCount(final List<InvocationResult> results) {
    return results.stream().filter(InvocationResult::succeeded).count();
  }

  private int activeTeamInterruptCount(final UUID scheduleId) {
    return jdbc.queryForObject(
        """
            SELECT COUNT(*)
            FROM interrupt
            WHERE schedule_id = ?
              AND team_id IS NOT NULL
              AND resolved_at IS NULL
            """,
        Integer.class,
        scheduleId);
  }

  private int activeSystemInterruptCount(final UUID scheduleId) {
    return jdbc.queryForObject(
        """
            SELECT COUNT(*)
            FROM interrupt
            WHERE schedule_id = ?
              AND team_id IS NULL
              AND resolved_at IS NULL
            """,
        Integer.class,
        scheduleId);
  }

  private int interruptCount(final UUID scheduleId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM interrupt WHERE schedule_id = ?", Integer.class, scheduleId);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private record InvocationResult(Throwable failure) {
    static InvocationResult success() {
      return new InvocationResult(null);
    }

    static InvocationResult failure(final Throwable failure) {
      return new InvocationResult(failure);
    }

    boolean succeeded() {
      return failure == null;
    }
  }
}
