package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ScheduleRepositoryIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 2, 2, 20, 0);

  @Autowired private ScheduleRepository scheduleRepository;
  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void findLastPlayedOrdersByPickedCategoryBeforeSongOrdinal() {
    final UUID gameId = fixture.game("SLST", 2, 4, 3);
    final UUID teamId = fixture.team(gameId, "Red", "red.png");
    final UUID firstCategory = categoryWithAlbum(gameId, teamId, 1, "First");
    final UUID secondCategory = categoryWithAlbum(gameId, teamId, 2, "Second");

    schedule(firstCategory, 99, START.minusMinutes(10));
    final UUID expected = schedule(secondCategory, 1, START.minusMinutes(2));
    schedule(secondCategory, 0, START.minusMinutes(3));

    final ScheduleEntity result = scheduleRepository.findLastPlayed(gameId);

    assertEquals(expected, result.getId());
    assertEquals(2, result.getCategoryId().getOrdinalNumber());
  }

  @Test
  void findLastPlayedIgnoresUnpickedCategoriesAndUnstartedSchedules() {
    final UUID gameId = fixture.game("SIGN", 2, 4, 3);
    final UUID teamId = fixture.team(gameId, "Red", "red.png");
    final UUID picked = categoryWithAlbum(gameId, teamId, 1, "Picked");
    final UUID unpicked = categoryWithAlbum(gameId, null, null, "Unpicked");

    final UUID expected = schedule(picked, 1, START.minusMinutes(1));
    schedule(picked, 2, null);
    schedule(unpicked, 100, START);

    final UUID otherGame = fixture.game("SIG2", 2, 4, 3);
    final UUID otherTeam = fixture.team(otherGame, "Other", "other.png");
    final UUID otherCategory = categoryWithAlbum(otherGame, otherTeam, 99, "Other");
    schedule(otherCategory, 100, START.plusMinutes(1));

    final ScheduleEntity result = scheduleRepository.findLastPlayed(gameId);

    assertEquals(expected, result.getId());
  }

  @Test
  void findNextReturnsLowestUnstartedOrdinalWithinGame() {
    final UUID gameId = fixture.game("SNXT", 2, 4, 2);
    final UUID teamId = fixture.team(gameId, "Red", "red.png");
    final UUID category = categoryWithAlbum(gameId, teamId, 1, "Current");

    schedule(category, 1, START);
    final UUID expected = schedule(category, 2, null);
    schedule(category, 3, null);

    final Optional<ScheduleEntity> result = scheduleRepository.findNext(gameId);

    assertTrue(result.isPresent());
    assertEquals(expected, result.orElseThrow().getId());
  }

  @Test
  void findNextIsGameScopedAndReturnsEmptyWhenCurrentGameHasNoUnstartedRows() {
    final UUID gameA = fixture.game("SGA1", 2, 2, 1);
    final UUID teamA = fixture.team(gameA, "A", "a.png");
    final UUID categoryA = categoryWithAlbum(gameA, teamA, 1, "A");
    schedule(categoryA, 1, START);

    final UUID gameB = fixture.game("SGB2", 2, 2, 1);
    final UUID teamB = fixture.team(gameB, "B", "b.png");
    final UUID categoryB = categoryWithAlbum(gameB, teamB, 1, "B");
    schedule(categoryB, 1, null);

    assertTrue(scheduleRepository.findNext(gameA).isEmpty());
    assertTrue(scheduleRepository.findNext(gameB).isPresent());
  }

  private UUID categoryWithAlbum(
      final UUID gameId,
      final UUID teamId,
      final Integer ordinal,
      final String albumName) {
    final UUID albumId = fixture.album(albumName);
    return fixture.category(gameId, albumId, teamId, ordinal, false);
  }

  private UUID schedule(final UUID categoryId, final int ordinal, final LocalDateTime startedAt) {
    final UUID albumId =
        jdbc.queryForObject(
            "SELECT album_id FROM category WHERE id = ?", UUID.class, categoryId);
    final UUID songId = fixture.song("Artist", "Song " + ordinal, 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    return fixture.schedule(categoryId, trackId, ordinal, startedAt, null);
  }
}
