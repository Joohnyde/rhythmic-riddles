package com.cevapinxile.cestereg.core.service.integration.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.core.service.impl.CategoryServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.ScheduleServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Import({
  InterruptServiceImpl.class,
  TeamServiceImpl.class,
  CategoryServiceImpl.class,
  ScheduleServiceImpl.class,
  FixedTestClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameConcurrencyIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;
  private static final int RACE_ROUNDS = 12;

  @Autowired private CategoryService categoryService;
  @Autowired private ScheduleService scheduleService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private GameService gameService;
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
  void concurrentAlbumPicksNeverPersistDuplicateOrdinalNumbers() throws Exception {
    for (int roundIndex = 0; roundIndex < RACE_ROUNDS; roundIndex++) {
      final String roomCode = String.format("C%03d", roundIndex);
      final UUID gameId = fixture.game(roomCode, 1, 3, 4);
      final UUID teamId = fixture.team(gameId, "Picker " + roundIndex, "picker.png");
      final UUID firstCategory =
          fixture.category(gameId, fixture.album("First " + roundIndex), null, null, false);
      final UUID secondCategory =
          fixture.category(gameId, fixture.album("Second " + roundIndex), null, null, false);
      clearInvocations(broadcastGateway);

      final List<InvocationResult> results =
          invokeSimultaneously(
              () -> categoryService.pickAlbum(firstCategory, new TeamIdRequest(teamId), roomCode),
              () -> categoryService.pickAlbum(secondCategory, new TeamIdRequest(teamId), roomCode));

      final List<Integer> ordinals =
          jdbc.queryForList(
              """
                  SELECT ordinal_number
                  FROM category
                  WHERE id IN (?, ?)
                    AND ordinal_number IS NOT NULL
                  ORDER BY ordinal_number
                  """,
              Integer.class,
              firstCategory,
              secondCategory);

      assertFalse(ordinals.isEmpty(), "album race round " + roundIndex);
      assertEquals(
          successCount(results),
          ordinals.size(),
          "album race round " + roundIndex + " results=" + results);
      assertEquals(
          ordinals.size(),
          new HashSet<>(ordinals).size(),
          "duplicate category ordinal in race round " + roundIndex);
    }
  }

  @Test
  void simultaneousProgressRequestsAdvanceCurrentSongExactlyOnce() throws Exception {
    for (int roundIndex = 0; roundIndex < RACE_ROUNDS; roundIndex++) {
      final String roomCode = String.format("P%03d", roundIndex);
      final UUID gameId = fixture.game(roomCode, 2, 2, 2);
      final UUID teamId = fixture.team(gameId, "Progress " + roundIndex, "progress.png");
      final UUID albumId = fixture.album("Progress Album " + roundIndex);
      final UUID firstSong = fixture.song("Artist", "First " + roundIndex, 30.0, 8.0);
      final UUID secondSong = fixture.song("Artist", "Second " + roundIndex, 30.0, 8.0);
      final UUID firstTrack = fixture.track(albumId, firstSong, null);
      final UUID secondTrack = fixture.track(albumId, secondSong, null);
      final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
      fixture.schedule(categoryId, firstTrack, 1, NOW.minusSeconds(12), NOW.minusSeconds(1));
      final UUID nextSchedule = fixture.schedule(categoryId, secondTrack, 2, null, null);
      final GameEntity game = new GameEntity(gameId);
      game.setStage(2);
      game.setMaxSongs(2);
      game.setMaxAlbums(2);
      when(gameService.findByCode(roomCode, 2)).thenReturn(game);
      clearInvocations(broadcastGateway);

      final List<InvocationResult> results =
          invokeSimultaneously(
              () -> scheduleService.progress(roomCode), () -> scheduleService.progress(roomCode));

      assertEquals(
          1L, successCount(results), "progress race round " + roundIndex + " results=" + results);
      assertNotNull(
          jdbc.queryForObject(
              "SELECT started_at FROM schedule WHERE id = ?", LocalDateTime.class, nextSchedule));
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM schedule WHERE category_id = ? AND started_at IS NOT NULL",
              Integer.class,
              categoryId));
      assertEquals(
          Boolean.FALSE,
          jdbc.queryForObject(
              "SELECT is_done FROM category WHERE id = ?", Boolean.class, categoryId),
          "duplicate progress must not prematurely finish the current category");
    }
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

  private long successCount(final List<InvocationResult> results) {
    return results.stream().filter(InvocationResult::succeeded).count();
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
