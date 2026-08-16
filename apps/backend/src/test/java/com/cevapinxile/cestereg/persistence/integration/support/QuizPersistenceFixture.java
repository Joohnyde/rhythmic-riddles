package com.cevapinxile.cestereg.persistence.integration.support;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Direct SQL fixture builder for persistence tests; deliberately bypasses repository save methods. */
public final class QuizPersistenceFixture {

  private final JdbcTemplate jdbc;

  public QuizPersistenceFixture(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public UUID game(final String code, final int stage, final int maxSongs, final int maxAlbums) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO game (id, date, stage, max_songs, max_albums, code, password_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
        id,
        LocalDateTime.of(2026, 1, 1, 12, 0),
        stage,
        maxSongs,
        maxAlbums,
        code,
        null);
    return id;
  }

  public UUID album(final String name) {
    return album(name, null);
  }

  public UUID album(final String name, final String customQuestion) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO album (id, name, custom_question) VALUES (?, ?, ?)",
        id,
        name,
        customQuestion);
    return id;
  }

  public UUID song(
      final String authors,
      final String name,
      final double snippetDuration,
      final double answerDuration) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO song (id, authors, name, snippet_duration, answer_duration) VALUES (?, ?, ?, ?, ?)",
        id,
        authors,
        name,
        snippetDuration,
        answerDuration);
    return id;
  }

  public UUID track(final UUID albumId, final UUID songId, final String customAnswer) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO track (id, album_id, song_id, custom_answer) VALUES (?, ?, ?, ?)",
        id,
        albumId,
        songId,
        customAnswer);
    return id;
  }

  public UUID team(final UUID gameId, final String name, final String image) {
    final UUID id = UUID.randomUUID();
    return team(id, gameId, name, image);
  }

  public UUID team(final UUID id, final UUID gameId, final String name, final String image) {
    jdbc.update(
        "INSERT INTO team (id, button_code, game_id, name, image) VALUES (?, ?, ?, ?, ?)",
        id,
        "button-" + id,
        gameId,
        name,
        image);
    return id;
  }

  public UUID category(
      final UUID gameId,
      final UUID albumId,
      final UUID pickedByTeamId,
      final Integer ordinalNumber,
      final Boolean done) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO category (id, album_id, game_id, picked_by_team_id, ordinal_number, is_done) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        albumId,
        gameId,
        pickedByTeamId,
        ordinalNumber,
        done);
    return id;
  }

  public UUID schedule(
      final UUID categoryId,
      final UUID trackId,
      final int ordinalNumber,
      final LocalDateTime startedAt,
      final LocalDateTime revealedAt) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO schedule (id, ordinal_number, track_id, started_at, revealed_at, category_id) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        ordinalNumber,
        trackId,
        startedAt,
        revealedAt,
        categoryId);
    return id;
  }

  public UUID interrupt(
      final UUID scheduleId,
      final UUID teamId,
      final LocalDateTime arrivedAt,
      final LocalDateTime resolvedAt,
      final Boolean correct,
      final Integer scoreOrScenarioId) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO interrupt
              (id, schedule_id, arrived_at, resolved_at, is_correct, score_or_scenario_id, team_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
        id,
        scheduleId,
        arrivedAt,
        resolvedAt,
        correct,
        scoreOrScenarioId,
        teamId);
    return id;
  }
}
