package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.core.service.impl.CategoryServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.GameServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.ScheduleServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import({
  CategoryServiceImpl.class,
  GameServiceImpl.class,
  InterruptServiceImpl.class,
  ScheduleServiceImpl.class,
  TeamServiceImpl.class,
  FixedTestClockConfiguration.class
})
class GameLifecycleIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 2, 10, 20, 0);

  @Autowired private CategoryService categoryService;
  @Autowired private ScheduleService scheduleService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
    when(presenceGateway.areBothPresent(anyString())).thenReturn(true);
  }

  @Test
  void startCategoryPersistsConfiguredSchedulePlanAndMovesGameToSongStage() throws Exception {
    final String roomCode = "LST1";
    final UUID gameId = fixture.game(roomCode, 1, 3, 2);
    final UUID teamId = fixture.team(gameId, "Starter", "starter.png");
    final UUID albumId = fixture.album("Lifecycle start album");
    createTracks(albumId, 4, "start");
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);

    categoryService.startCategory(categoryId, roomCode);

    assertEquals(2, gameStage(gameId));
    assertEquals(3, countSchedules(categoryId));
    assertEquals(List.of(1, 2, 3), scheduleOrdinals(categoryId));
    assertEquals(3, distinctScheduledTracks(categoryId));
    assertEquals(1, countStartedSchedules(categoryId));
    assertEquals(0, countRevealedSchedules(categoryId));
  }

  @Test
  void progressStartsNextScheduleAndKeepsCurrentCategoryActive() throws Exception {
    final String roomCode = "LNX1";
    final UUID gameId = fixture.game(roomCode, 2, 2, 2);
    final UUID teamId = fixture.team(gameId, "Progressor", "progress.png");
    final UUID albumId = fixture.album("Lifecycle next album");
    final UUID firstTrack = track(albumId, "first");
    final UUID secondTrack = track(albumId, "second");
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID firstSchedule =
        fixture.schedule(categoryId, firstTrack, 1, START, START.plusSeconds(20));
    final UUID secondSchedule = fixture.schedule(categoryId, secondTrack, 2, null, null);
    final UUID systemPause =
        fixture.interrupt(
            firstSchedule, null, START.plusSeconds(5), null, null, 1);

    scheduleService.progress(roomCode);

    assertEquals(2, gameStage(gameId));
    assertFalse(categoryDone(categoryId));
    assertNotNull(startedAt(secondSchedule));
    assertNull(revealedAt(secondSchedule));
    assertNotNull(resolvedAt(systemPause));
  }

  @Test
  void progressFinishesNonFinalCategoryAndReturnsToAlbumSelection() throws Exception {
    final String roomCode = "LAL1";
    final UUID gameId = fixture.game(roomCode, 2, 1, 2);
    final UUID teamId = fixture.team(gameId, "Album Team", "album.png");
    final UUID albumId = fixture.album("First lifecycle album");
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    fixture.schedule(
        categoryId,
        track(albumId, "only"),
        1,
        START,
        START.plusSeconds(20));
    fixture.category(gameId, fixture.album("Unpicked lifecycle album"), null, null, false);

    scheduleService.progress(roomCode);

    assertEquals(1, gameStage(gameId));
    assertEquals(Boolean.TRUE, categoryDoneValue(categoryId));
  }

  @Test
  void progressFinishesFinalCategoryAndMovesGameToWinner() throws Exception {
    final String roomCode = "LWIN";
    final UUID gameId = fixture.game(roomCode, 2, 1, 1);
    final UUID teamId = fixture.team(gameId, "Winner", "winner.png");
    final UUID albumId = fixture.album("Final lifecycle album");
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    fixture.schedule(
        categoryId,
        track(albumId, "final"),
        1,
        START,
        START.plusSeconds(20));

    scheduleService.progress(roomCode);

    assertEquals(3, gameStage(gameId));
    assertEquals(Boolean.TRUE, categoryDoneValue(categoryId));
  }

  private void createTracks(final UUID albumId, final int count, final String prefix) {
    for (int index = 1; index <= count; index++) {
      track(albumId, prefix + "-" + index);
    }
  }

  private UUID track(final UUID albumId, final String discriminator) {
    final UUID songId = fixture.song("Artist", "Lifecycle " + discriminator, 30.0, 8.0);
    return fixture.track(albumId, songId, null);
  }

  private int gameStage(final UUID gameId) {
    return jdbc.queryForObject("SELECT stage FROM game WHERE id = ?", Integer.class, gameId);
  }

  private int countSchedules(final UUID categoryId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM schedule WHERE category_id = ?", Integer.class, categoryId);
  }

  private int countStartedSchedules(final UUID categoryId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM schedule WHERE category_id = ? AND started_at IS NOT NULL",
        Integer.class,
        categoryId);
  }

  private int countRevealedSchedules(final UUID categoryId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM schedule WHERE category_id = ? AND revealed_at IS NOT NULL",
        Integer.class,
        categoryId);
  }

  private int distinctScheduledTracks(final UUID categoryId) {
    return jdbc.queryForObject(
        "SELECT COUNT(DISTINCT track_id) FROM schedule WHERE category_id = ?",
        Integer.class,
        categoryId);
  }

  private List<Integer> scheduleOrdinals(final UUID categoryId) {
    return jdbc.queryForList(
        "SELECT ordinal_number FROM schedule WHERE category_id = ? ORDER BY ordinal_number",
        Integer.class,
        categoryId);
  }

  private boolean categoryDone(final UUID categoryId) {
    return Boolean.TRUE.equals(categoryDoneValue(categoryId));
  }

  private Boolean categoryDoneValue(final UUID categoryId) {
    return jdbc.queryForObject(
        "SELECT is_done FROM category WHERE id = ?", Boolean.class, categoryId);
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
}
