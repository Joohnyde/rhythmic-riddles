package com.cevapinxile.cestereg.persistence.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cevapinxile.cestereg.persistence.integration.support.EmbeddedPostgresTestDatabase;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.ProductionSchemaSql;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseSchemaIntegrationTest extends PostgresJpaIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void databaseInitializerAppliesBaseSchemaAndRuntimeInvariants() {
    assertEquals(
        List.of("db_01_create_schema.sql", "db_04_add_runtime_invariants.sql"),
        EmbeddedPostgresTestDatabase.initializerScripts());

    assertEquals(
        2,
        jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'uq_category_game_ordinal',
                    'uq_interrupt_schedule_active_team'
                  )
                """,
            Integer.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname IN ('ck_game_stage', 'ck_game_positive_limits')
                """,
            Integer.class));
  }

  @Test
  void schemaBootstrapCanBeReappliedWithoutDestroyingExistingData() {
    final UUID gameId = fixture.game("IDEM", 2, 2, 1);
    final UUID teamId = fixture.team(gameId, "Persistent Team", "team.png");
    final UUID albumId = fixture.album("Persistent Album");
    final UUID songId = fixture.song("Artist", "Persistent Song", 20.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId =
        fixture.schedule(
            categoryId,
            trackId,
            1,
            LocalDateTime.of(2026, 2, 8, 20, 0),
            null);
    final UUID interruptId =
        fixture.interrupt(
            scheduleId,
            teamId,
            LocalDateTime.of(2026, 2, 8, 20, 0, 2),
            null,
            null,
            null);

    executeProductionSql("db_01_create_schema.sql");
    executeProductionSql("db_04_add_runtime_invariants.sql");

    assertEquals(1, count("game", gameId));
    assertEquals(1, count("team", teamId));
    assertEquals(1, count("album", albumId));
    assertEquals(1, count("song", songId));
    assertEquals(1, count("track", trackId));
    assertEquals(1, count("category", categoryId));
    assertEquals(1, count("schedule", scheduleId));
    assertEquals(1, count("interrupt", interruptId));
  }

  @Test
  void databaseRejectsDuplicateRoomCodes() {
    fixture.game("DUPE", 0, 3, 2);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> fixture.game("DUPE", 0, 3, 2));
  }

  private void executeProductionSql(final String fileName) {
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (Statement statement = connection.createStatement()) {
                statement.execute(ProductionSchemaSql.read(fileName));
              }
              return null;
            });
  }

  private int count(final String table, final UUID id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
  }
}
