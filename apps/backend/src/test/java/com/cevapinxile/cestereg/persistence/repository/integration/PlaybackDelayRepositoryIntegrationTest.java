package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.PlaybackDelayRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exercises the production PostgreSQL constraint and chronological recovery query. */
class PlaybackDelayRepositoryIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 12, 0);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlaybackDelayRepository repository;

  private UUID scheduleId;

  @BeforeEach
  void setUp() {
    final QuizPersistenceFixture fixture = new QuizPersistenceFixture(jdbc);
    final UUID gameId = fixture.game("PDLY", 2, 2, 1);
    final UUID teamId = fixture.team(gameId, "Delay test", "delay.png");
    final UUID albumId = fixture.album("Delay album");
    final UUID songId = fixture.song("Artist", "Song", 20.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    scheduleId = fixture.schedule(categoryId, trackId, 1, START, null);
  }

  @Test
  void permitsManyClosedDelaysButRejectsTwoOpenDelaysPerSchedule() {
    final UUID first = insert(START, START.plusSeconds(3));
    final UUID second = insert(START.plusSeconds(8), START.plusSeconds(10));
    final UUID ongoing = insert(START.plusSeconds(15), null);

    assertEquals(3, repository.findBySchedule(scheduleId).size());
    assertEquals(first, repository.findBySchedule(scheduleId).get(0).getId());
    assertEquals(second, repository.findBySchedule(scheduleId).get(1).getId());
    assertEquals(ongoing, repository.findOpenBySchedule(scheduleId).getId());
    assertThrows(DataIntegrityViolationException.class,
        () -> insert(START.plusSeconds(17), null));
  }

  @Test
  void playbackDelayTableHasNoRedundantDurationColumn() {
    assertEquals(0, jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'playback_delay'
          AND column_name = 'delay'
        """, Integer.class));
  }

  @Test
  void closingAnOpenDelayAllowsTheNextRegistration() {
    final UUID first = insert(START, null);
    jdbc.update("UPDATE playback_delay SET acknowledged_at = ? WHERE id = ?",
        START.plusSeconds(4), first);
    final UUID second = insert(START.plusSeconds(7), null);
    assertEquals(second, repository.findOpenBySchedule(scheduleId).getId());
    assertEquals(2, repository.findBySchedule(scheduleId).size());
  }

  @Test
  void deletingScheduleCascadesToItsPlaybackHistory() {
    final UUID attempt = insert(START, START.plusSeconds(1));
    jdbc.update("DELETE FROM schedule WHERE id = ?", scheduleId);
    assertEquals(0, jdbc.queryForObject(
        "SELECT COUNT(*) FROM playback_delay WHERE id = ?", Integer.class, attempt));
  }

  private UUID insert(final LocalDateTime sent, final LocalDateTime acknowledged) {
    final UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO playback_delay (id, schedule_id, sent_at, acknowledged_at) VALUES (?, ?, ?, ?)",
        id, scheduleId, sent, acknowledged);
    return id;
  }
}
