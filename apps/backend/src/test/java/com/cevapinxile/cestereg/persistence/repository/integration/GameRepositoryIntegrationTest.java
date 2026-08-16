package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GameRepositoryIntegrationTest extends PostgresJpaIntegrationTest {

  @Autowired private GameRepository gameRepository;
  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void stageAwareFindByCodeReturnsPersistedGameOnlyForExpectedStage() throws Exception {
    final UUID gameId = fixture.game("GSTG", 2, 3, 2);

    final GameEntity game = gameRepository.findByCode("GSTG", 2);

    assertEquals(gameId, game.getId());
    assertEquals(2, game.getStage());
    assertThrows(WrongGameStateException.class, () -> gameRepository.findByCode("GSTG", 1));
  }

  @Test
  void stageAwareFindByCodeRejectsUnknownRoomWithDomainReferenceError() {
    assertThrows(
        InvalidReferencedObjectException.class,
        () -> gameRepository.findByCode("MISS", 2));
  }

  @Test
  void deleteByCodeCascadesGameRuntimeStateWithoutDeletingSharedCatalogData() {
    final UUID gameToDelete = fixture.game("GDEL", 2, 2, 1);
    final UUID teamToDelete = fixture.team(gameToDelete, "Delete", "delete.png");
    final UUID sharedAlbum = fixture.album("Shared Album");
    final UUID sharedSong = fixture.song("Artist", "Shared Song", 20.0, 8.0);
    final UUID sharedTrack = fixture.track(sharedAlbum, sharedSong, null);
    final UUID categoryToDelete =
        fixture.category(gameToDelete, sharedAlbum, teamToDelete, 1, false);
    final UUID scheduleToDelete =
        fixture.schedule(
            categoryToDelete,
            sharedTrack,
            1,
            LocalDateTime.of(2026, 2, 7, 20, 0),
            null);
    fixture.interrupt(
        scheduleToDelete,
        teamToDelete,
        LocalDateTime.of(2026, 2, 7, 20, 0, 2),
        null,
        null,
        null);

    final UUID otherGame = fixture.game("GKEP", 2, 2, 1);
    final UUID otherTeam = fixture.team(otherGame, "Keep", "keep.png");
    final UUID otherCategory = fixture.category(otherGame, sharedAlbum, otherTeam, 1, false);
    final UUID otherSchedule =
        fixture.schedule(
            otherCategory,
            sharedTrack,
            1,
            LocalDateTime.of(2026, 2, 7, 21, 0),
            null);
    fixture.interrupt(
        otherSchedule,
        otherTeam,
        LocalDateTime.of(2026, 2, 7, 21, 0, 2),
        null,
        null,
        null);

    gameRepository.deleteByCode("GDEL");
    gameRepository.flush();

    assertEquals(0, count("game", "id", gameToDelete));
    assertEquals(0, count("team", "game_id", gameToDelete));
    assertEquals(0, count("category", "game_id", gameToDelete));
    assertEquals(0, count("schedule", "id", scheduleToDelete));
    assertEquals(0, count("interrupt", "schedule_id", scheduleToDelete));

    assertEquals(1, count("game", "id", otherGame));
    assertEquals(1, count("team", "game_id", otherGame));
    assertEquals(1, count("category", "game_id", otherGame));
    assertEquals(1, count("schedule", "id", otherSchedule));
    assertEquals(1, count("interrupt", "schedule_id", otherSchedule));

    assertEquals(1, count("album", "id", sharedAlbum));
    assertEquals(1, count("song", "id", sharedSong));
    assertEquals(1, count("track", "id", sharedTrack));
  }

  private int count(final String table, final String column, final UUID id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
  }
}
