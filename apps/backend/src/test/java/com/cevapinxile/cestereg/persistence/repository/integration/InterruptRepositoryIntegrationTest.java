package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class InterruptRepositoryIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 2, 1, 20, 0, 0);

  @Autowired private InterruptRepository interruptRepository;
  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;
  private UUID gameId;
  private UUID teamId;
  private UUID scheduleId;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
    gameId = fixture.game("IRPT", 2, 5, 3);
    teamId = fixture.team(gameId, "Red", "red.png");
    final UUID albumId = fixture.album("Interrupt album", "Question?");
    final UUID songId = fixture.song("Artist", "Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, "Answer");
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    scheduleId = fixture.schedule(categoryId, trackId, 1, START, null);
  }

  @Test
  void findInterruptsReturnsDisjointFramesInArrivalOrder() {
    fixture.interrupt(
        scheduleId, null, START.plusSeconds(2), START.plusSeconds(4), null, 1);
    fixture.interrupt(
        scheduleId, teamId, START.plusSeconds(7), START.plusSeconds(8), false, -10);

    final List<InterruptFrame> frames = interruptRepository.findInterrupts(START, scheduleId);

    assertEquals(2, frames.size());
    assertEquals(START.plusSeconds(2), frames.get(0).getStart());
    assertEquals(START.plusSeconds(4), frames.get(0).getEnd());
    assertEquals(START.plusSeconds(7), frames.get(1).getStart());
    assertEquals(START.plusSeconds(8), frames.get(1).getEnd());
  }

  @Test
  void findInterruptsCollapsesNestedFramesToOutermostIntervals() {
    fixture.interrupt(
        scheduleId, null, START.plusSeconds(2), START.plusSeconds(9), null, 1);
    fixture.interrupt(
        scheduleId, teamId, START.plusSeconds(4), START.plusSeconds(6), false, -10);
    fixture.interrupt(
        scheduleId, null, START.plusSeconds(5), START.plusSeconds(5).plusNanos(500_000_000), null, 3);

    final List<InterruptFrame> frames = interruptRepository.findInterrupts(START, scheduleId);

    assertEquals(1, frames.size());
    assertEquals(START.plusSeconds(2), frames.getFirst().getStart());
    assertEquals(START.plusSeconds(9), frames.getFirst().getEnd());
  }

  @Test
  void findInterruptsKeepsOngoingOutermostFrameAndSuppressesNestedFrames() {
    fixture.interrupt(scheduleId, null, START.plusSeconds(2), null, null, 1);
    fixture.interrupt(
        scheduleId, teamId, START.plusSeconds(4), START.plusSeconds(5), false, -10);

    final List<InterruptFrame> frames = interruptRepository.findInterrupts(START, scheduleId);

    assertEquals(1, frames.size());
    assertEquals(START.plusSeconds(2), frames.getFirst().getStart());
    assertNull(frames.getFirst().getEnd());
  }

  @Test
  void findInterruptsIgnoresOtherSchedulesAndRowsAtOrBeforeSongStart() {
    fixture.interrupt(
        scheduleId, null, START.minusSeconds(1), START.plusSeconds(1), null, 1);
    fixture.interrupt(scheduleId, null, START, START.plusSeconds(1), null, 1);
    fixture.interrupt(
        scheduleId, null, START.plusSeconds(2), START.plusSeconds(3), null, 1);

    final UUID albumId = fixture.album("Other album");
    final UUID songId = fixture.song("Other", "Song", 20.0, 5.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 2, false);
    final UUID otherSchedule = fixture.schedule(categoryId, trackId, 1, START, null);
    fixture.interrupt(
        otherSchedule, null, START.plusSeconds(1), START.plusSeconds(10), null, 1);

    final List<InterruptFrame> frames = interruptRepository.findInterrupts(START, scheduleId);

    assertEquals(1, frames.size());
    assertEquals(START.plusSeconds(2), frames.getFirst().getStart());
  }

  @Test
  void latestTeamAndSystemInterruptQueriesRemainIndependentAndScheduleScoped() {
    fixture.interrupt(
        scheduleId, null, START.plusSeconds(1), START.plusSeconds(2), null, 1);
    fixture.interrupt(
        scheduleId, teamId, START.plusSeconds(3), START.plusSeconds(4), false, -10);
    final UUID expectedSystem =
        fixture.interrupt(
            scheduleId, null, START.plusSeconds(5), START.plusSeconds(6), null, 2);
    final UUID expectedTeam =
        fixture.interrupt(
            scheduleId, teamId, START.plusSeconds(7), START.plusSeconds(8), false, -20);

    final UUID otherGame = fixture.game("IRP2", 2, 3, 1);
    final UUID otherTeam = fixture.team(otherGame, "Other team", "other.png");
    final UUID otherAlbum = fixture.album("Other game album");
    final UUID otherSong = fixture.song("Other", "Later", 15.0, 5.0);
    final UUID otherTrack = fixture.track(otherAlbum, otherSong, null);
    final UUID otherCategory = fixture.category(otherGame, otherAlbum, otherTeam, 1, false);
    final UUID otherSchedule =
        fixture.schedule(otherCategory, otherTrack, 1, START.plusSeconds(10), null);
    fixture.interrupt(otherSchedule, otherTeam, START.plusSeconds(11), null, null, null);
    fixture.interrupt(otherSchedule, null, START.plusSeconds(12), null, null, 4);

    final InterruptEntity latestTeam = interruptRepository.findLastAnswer(START, scheduleId);
    final InterruptEntity latestSystem = interruptRepository.findLastPause(START, scheduleId);

    assertEquals(expectedTeam, latestTeam.getId());
    assertEquals(expectedSystem, latestSystem.getId());
    assertEquals(teamId, latestTeam.getTeamId().getId());
    assertNull(latestSystem.getTeamId());
  }

  @Test
  void resolveErrorsUpdatesOnlyUnresolvedSystemInterruptsForRequestedSchedule() {
    final UUID unresolvedSystem =
        fixture.interrupt(scheduleId, null, START.plusSeconds(2), null, null, 1);
    final UUID alreadyResolvedSystem =
        fixture.interrupt(
            scheduleId,
            null,
            START.plusSeconds(3),
            START.plusSeconds(4),
            null,
            2);
    final UUID teamAnswer =
        fixture.interrupt(scheduleId, teamId, START.plusSeconds(5), null, null, null);
    final UUID otherAlbumId = fixture.album("Other error album");
    final UUID otherSongId = fixture.song("Other", "Error", 20.0, 5.0);
    final UUID otherTrackId = fixture.track(otherAlbumId, otherSongId, null);
    final UUID otherCategoryId = fixture.category(gameId, otherAlbumId, teamId, 2, false);
    final UUID otherScheduleId = fixture.schedule(otherCategoryId, otherTrackId, 1, START, null);
    final UUID otherScheduleSystem =
        fixture.interrupt(otherScheduleId, null, START.plusSeconds(6), null, null, 1);
    final LocalDateTime resolvedAt = START.plusSeconds(12);

    interruptRepository.resolveErrors(scheduleId, resolvedAt);
    interruptRepository.flush();

    assertEquals(resolvedAt, resolvedAt(unresolvedSystem));
    assertEquals(START.plusSeconds(4), resolvedAt(alreadyResolvedSystem));
    assertNull(resolvedAt(teamAnswer));
    assertNull(resolvedAt(otherScheduleSystem));
  }

  @Test
  void findCorrectAnswerReturnsCorrectTeamAndIgnoresWrongAnswers() {
    final UUID otherTeam = fixture.team(gameId, "Blue", "blue.png");
    fixture.interrupt(
        scheduleId, teamId, START.plusSeconds(2), START.plusSeconds(3), false, -10);
    fixture.interrupt(
        scheduleId, otherTeam, START.plusSeconds(5), START.plusSeconds(6), true, 30);

    final UUID otherAlbum = fixture.album("Other correct-answer album");
    final UUID otherSong = fixture.song("Other", "Correct", 20.0, 5.0);
    final UUID otherTrack = fixture.track(otherAlbum, otherSong, null);
    final UUID otherCategory = fixture.category(gameId, otherAlbum, teamId, 2, false);
    final UUID otherSchedule = fixture.schedule(otherCategory, otherTrack, 1, START, null);
    fixture.interrupt(
        otherSchedule, teamId, START.plusSeconds(8), START.plusSeconds(9), true, 999);

    assertEquals(otherTeam, interruptRepository.findCorrectAnswer(scheduleId));
  }


  @Test
  void didTeamAnswerOnlyCountsStartedSchedulesInPickedCategories() {
    final UUID freshTeam = fixture.team(gameId, "Green", "green.png");
    final UUID albumId = fixture.album("Unpicked album");
    final UUID songId = fixture.song("Artist", "Next", 20.0, 5.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID unpickedCategory = fixture.category(gameId, albumId, null, null, false);
    final UUID unstartedSchedule = fixture.schedule(unpickedCategory, trackId, 1, null, null);
    fixture.interrupt(unstartedSchedule, freshTeam, START.plusSeconds(2), null, null, null);

    assertTrue(interruptRepository.didTeamAnswer(teamId).isEmpty());
    assertTrue(interruptRepository.didTeamAnswer(freshTeam).isEmpty());

    final UUID pickedCategory = fixture.category(gameId, albumId, freshTeam, 2, false);
    final UUID startedSchedule = fixture.schedule(pickedCategory, trackId, 2, START, null);
    fixture.interrupt(startedSchedule, freshTeam, START.plusSeconds(3), null, null, null);

    assertEquals(Boolean.TRUE, interruptRepository.didTeamAnswer(freshTeam).orElseThrow());
  }

  @Test
  void findPreviousScenarioReturnsStoredScenarioForCurrentUnresolvedSystemPause() {
    fixture.interrupt(
        scheduleId, teamId, START.plusSeconds(1), START.plusSeconds(2), false, -10);
    fixture.interrupt(
        scheduleId, null, START.plusSeconds(3), START.plusSeconds(4), null, 1);
    fixture.interrupt(scheduleId, null, START.plusSeconds(5), null, null, 4);

    assertEquals(4, interruptRepository.findPreviousScenarioId(scheduleId));
  }

  private LocalDateTime resolvedAt(final UUID interruptId) {
    return jdbc.queryForObject(
        "SELECT resolved_at FROM interrupt WHERE id = ?", LocalDateTime.class, interruptId);
  }
}
