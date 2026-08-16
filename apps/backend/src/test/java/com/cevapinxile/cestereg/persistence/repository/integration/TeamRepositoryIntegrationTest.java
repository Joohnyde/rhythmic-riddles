package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TeamRepositoryIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 2, 3, 20, 0);

  @Autowired private TeamRepository teamRepository;
  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void getTeamScoresUsesLatestScoringInterruptAndDefaultsTeamsWithoutScoreToZero() {
    final UUID gameId = fixture.game("TSCR", 2, 3, 2);
    final UUID red = fixture.team(gameId, "Red", "red.png");
    final UUID blue = fixture.team(gameId, "Blue", "blue.png");
    final UUID schedule1 = playedSchedule(gameId, red, 1, START);
    final UUID schedule2 = playedSchedule(gameId, red, 2, START.plusMinutes(1));

    fixture.interrupt(schedule1, red, START.plusSeconds(2), START.plusSeconds(3), true, 30);
    fixture.interrupt(schedule2, red, START.plusSeconds(8), START.plusSeconds(9), false, 20);
    fixture.interrupt(schedule2, red, START.plusSeconds(12), null, null, null);

    final Map<UUID, TeamScoreProjection> scores =
        teamRepository.getTeamScores("TSCR").stream()
            .collect(Collectors.toMap(TeamScoreProjection::getTeam, Function.identity()));

    assertEquals(2, scores.size());
    assertEquals(20, scores.get(red).getScore());
    assertEquals(schedule2, scores.get(red).getSchedule());
    assertEquals(0, scores.get(blue).getScore());
    assertNull(scores.get(blue).getSchedule());
  }

  @Test
  void getTeamScoresIsStrictlyRoomScoped() {
    final UUID gameA = fixture.game("TGA1", 2, 2, 1);
    final UUID teamA = fixture.team(gameA, "A", "a.png");
    final UUID scheduleA = playedSchedule(gameA, teamA, 1, START);
    fixture.interrupt(scheduleA, teamA, START.plusSeconds(1), START.plusSeconds(2), true, 30);

    final UUID gameB = fixture.game("TGB2", 2, 2, 1);
    final UUID teamB = fixture.team(gameB, "B", "b.png");
    final UUID scheduleB = playedSchedule(gameB, teamB, 1, START);
    fixture.interrupt(scheduleB, teamB, START.plusSeconds(1), START.plusSeconds(2), true, 90);

    final List<TeamScoreProjection> scores = teamRepository.getTeamScores("TGA1");

    assertEquals(1, scores.size());
    assertEquals(teamA, scores.getFirst().getTeam());
    assertEquals(30, scores.getFirst().getScore());
  }


  @Test
  void findNextChoosesTeamWithFewestCompletedAlbumPicks() {
    final UUID gameId = fixture.game("TNXT", 1, 3, 4);
    final UUID first =
        fixture.team(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            gameId,
            "First",
            "1.png");
    final UUID second =
        fixture.team(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            gameId,
            "Second",
            "2.png");
    pickAlbum(gameId, first, 1);

    final UUID otherGame = fixture.game("TNX2", 1, 3, 4);
    fixture.team(
        UUID.fromString("00000000-0000-0000-0000-000000000000"),
        otherGame,
        "Other game",
        "other.png");

    final ChoosingTeam next = teamRepository.findNext(gameId, 4);

    assertNotNull(next);
    assertEquals(second.toString(), next.getId());
  }

  @Test
  void findNextReturnsNullWhenAutomaticPickerQuotaIsExhausted() {
    final UUID gameId = fixture.game("TODD", 1, 3, 5);
    final UUID first =
        fixture.team(
            UUID.fromString("00000000-0000-0000-0000-000000000011"),
            gameId,
            "First",
            "1.png");
    final UUID second =
        fixture.team(
            UUID.fromString("00000000-0000-0000-0000-000000000012"),
            gameId,
            "Second",
            "2.png");
    pickAlbum(gameId, first, 1);
    pickAlbum(gameId, second, 2);
    pickAlbum(gameId, first, 3);
    pickAlbum(gameId, second, 4);

    assertNull(teamRepository.findNext(gameId, 5));
  }

  @Test
  void findByGameIdProjectsOnlyRequestedRoomTeamsWithClientFields() {
    final UUID gameId = fixture.game("TPRJ", 0, 3, 2);
    final UUID first = fixture.team(gameId, "First", "first.png");
    final UUID second = fixture.team(gameId, "Second", "second.png");
    final UUID otherGame = fixture.game("TPR2", 0, 3, 2);
    fixture.team(otherGame, "Other", "other.png");

    final List<CreateTeamResponse> teams = teamRepository.findByGameId("TPRJ");

    assertEquals(2, teams.size());
    final CreateTeamResponse firstProjection =
        teams.stream().filter(team -> team.getId().equals(first)).findFirst().orElseThrow();
    final CreateTeamResponse secondProjection =
        teams.stream().filter(team -> team.getId().equals(second)).findFirst().orElseThrow();

    assertEquals("First", firstProjection.getName());
    assertEquals("first.png", firstProjection.getImage());
    assertEquals("Second", secondProjection.getName());
    assertEquals("second.png", secondProjection.getImage());
  }

  private void pickAlbum(final UUID gameId, final UUID teamId, final int ordinal) {
    fixture.category(gameId, fixture.album("Album " + UUID.randomUUID()), teamId, ordinal, false);
  }

  private UUID playedSchedule(
      final UUID gameId, final UUID teamId, final int categoryOrdinal, final LocalDateTime startedAt) {
    final UUID albumId = fixture.album("Score album " + categoryOrdinal);
    final UUID songId = fixture.song("Artist", "Song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, categoryOrdinal, false);
    return fixture.schedule(categoryId, trackId, 1, startedAt, null);
  }
}
