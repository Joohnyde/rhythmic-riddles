package com.cevapinxile.cestereg.api.quiz.dto.response;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TeamScoreCacheTest {

  @Test
  void returnsAndUpdatesScoresForExistingTeams() throws Exception {
    final UUID teamId = UUID.randomUUID();
    final UUID originalScheduleId = UUID.randomUUID();
    final UUID newScheduleId = UUID.randomUUID();
    final TeamScoreCache cache =
        new TeamScoreCache(
            new ArrayList<>(
                List.of(new Projection(teamId, "wolves", "img.png", 3, originalScheduleId))));

    assertEquals(3, cache.getScore(teamId));
    assertDoesNotThrow(() -> cache.setScore(teamId, newScheduleId, 7));
    assertEquals(7, cache.getScore(teamId));
  }

  @Test
  void rejectsUnknownTeamLookups() {
    final TeamScoreCache cache = new TeamScoreCache(new ArrayList<>());
    final UUID missingTeamId = UUID.randomUUID();

    final InvalidReferencedObjectException getException =
        assertThrows(InvalidReferencedObjectException.class, () -> cache.getScore(missingTeamId));
    final InvalidReferencedObjectException setException =
        assertThrows(
            InvalidReferencedObjectException.class,
            () -> cache.setScore(missingTeamId, UUID.randomUUID(), 5));

    assertEquals(
        "Team with id " + missingTeamId + " could not be found in the cache",
        getException.getMessage());
    assertEquals(getException.getMessage(), setException.getMessage());
  }

  private record Projection(UUID team, String image, String name, Integer score, UUID schedule)
      implements TeamScoreProjection {
    @Override
    public UUID getTeam() {
      return team;
    }

    @Override
    public String getImage() {
      return image;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Integer getScore() {
      return score;
    }

    @Override
    public UUID getSchedule() {
      return schedule;
    }
  }
}
