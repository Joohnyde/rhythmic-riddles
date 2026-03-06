package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.MissingArgumentException;
import com.cevapinxile.cestereg.core.gateway.AssetGateway;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

public class TeamServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class TeamCreationAndCaching {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;

    @InjectMocks private TeamServiceImpl teamService;

    @Test
    void createTeamRejectsBlankNameOrImage() throws DerivedException {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> teamService.createTeam(new CreateTeamRequest(" ", "A1", ""), "AKKU"));

      assertEquals("The request's body is missing a name and/or a picture", exception.getMessage());
      verify(gameRepository, never()).findByCode(any(), any());
      verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTeamPersistsTeamAndBroadcastsToTv() throws Exception {
      final UUID gameId = UUID.randomUUID();
      final GameEntity game = new GameEntity(gameId);
      final CreateTeamRequest request = new CreateTeamRequest("Wolves", "B1", "wolf.png");
      when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);

      final var response = teamService.createTeam(request, "AKKU");
      final ArgumentCaptor<TeamEntity> savedTeam = ArgumentCaptor.forClass(TeamEntity.class);
      final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

      verify(teamRepository).saveAndFlush(savedTeam.capture());
      verify(broadcastGateway).toTv(org.mockito.Mockito.eq("AKKU"), payload.capture());

      assertEquals("Wolves", savedTeam.getValue().getName());
      assertEquals("wolf.png", savedTeam.getValue().getImage());
      assertEquals("B1", savedTeam.getValue().getButtonCode());
      assertEquals(gameId, savedTeam.getValue().getGameId().getId());
      assertEquals(savedTeam.getValue().getId(), response.getId());
      assertEquals("Wolves", response.getName());
      assertEquals("wolf.png", response.getImage());
      org.junit.jupiter.api.Assertions.assertTrue(
          payload.getValue().contains("\"type\":\"new_team\""));
      org.junit.jupiter.api.Assertions.assertTrue(
          payload.getValue().contains(response.getId().toString()));
    }

    @Test
    void getTeamScoresUsesCacheAfterFirstLoad() throws Exception {
      final List<TeamScoreProjection> projections =
          new ArrayList<>(
              List.of(new Projection(UUID.randomUUID(), "fox.png", "Foxes", 9, UUID.randomUUID())));
      when(teamRepository.getTeamScores("AKKU")).thenReturn(projections);

      final Object first = teamService.getTeamScores("AKKU");
      final Object second = teamService.getTeamScores("AKKU");

      assertSame(first, second);
      verify(teamRepository).getTeamScores("AKKU");
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

  @Nested
  @ExtendWith(MockitoExtension.class)
  class TeamScoreCacheAndValidation {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;

    @InjectMocks private TeamServiceImpl teamService;

    @Test
    void createTeamRejectsBlankPayloadFields() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> teamService.createTeam(new CreateTeamRequest(" ", "BTN", " "), "AKKU"));

      assertEquals("The request's body is missing a name and/or a picture", exception.getMessage());
    }

    @Test
    void getTeamPointsLoadsCacheOnlyOnceAndThenReusesIt() throws Exception {
      final UUID teamId = UUID.randomUUID();
      when(teamRepository.getTeamScores("AKKU"))
          .thenReturn(new ArrayList<>(List.of(projection(teamId, 10))));

      assertEquals(10, teamService.getTeamPoints(teamId, "AKKU"));
      assertEquals(10, teamService.getTeamPoints(teamId, "AKKU"));
      verify(teamRepository).getTeamScores("AKKU");
    }

    @Test
    void saveTeamAnswerUpdatesCachedValue() throws Exception {
      final UUID teamId = UUID.randomUUID();
      final UUID scheduleId = UUID.randomUUID();
      when(teamRepository.getTeamScores("AKKU"))
          .thenReturn(new ArrayList<>(List.of(projection(teamId, 10))));

      teamService.saveTeamAnswer(teamId, scheduleId, 40, "AKKU");

      assertEquals(40, teamService.getTeamPoints(teamId, "AKKU"));
    }

    @Test
    void getTeamScoresRejectsUnknownRoomWhenRepositoryReturnsNull() {
      when(teamRepository.getTeamScores("MISS")).thenReturn(null);

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class, () -> teamService.getTeamScores("MISS"));

      assertEquals("Game with code MISS could not be found", exception.getMessage());
    }

    private GameEntity game() {
      return ServiceImplTestDataFactory.game();
    }

    private TeamScoreProjection projection(UUID teamId, Integer score) {
      return new TeamScoreProjection() {
        @Override
        public UUID getTeam() {
          return teamId;
        }

        @Override
        public String getImage() {
          return "img.png";
        }

        @Override
        public String getName() {
          return "Blue";
        }

        @Override
        public Integer getScore() {
          return score;
        }

        @Override
        public UUID getSchedule() {
          return UUID.randomUUID();
        }
      };
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class TeamLookupAndCacheConsistency {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;
    @InjectMocks private TeamServiceImpl teamService;

    @Mock private AssetGateway assetGateway;
    @InjectMocks private SongServiceImpl songService;

    @Test
    void createTeamRejectsBlankNameOrImage() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> teamService.createTeam(new CreateTeamRequest("  ", "BTN1", " "), "AKKU"));

      assertEquals("The request's body is missing a name and/or a picture", exception.getMessage());
    }

    @Test
    void kickTeamRejectsUnknownTeam() throws DerivedException {
      final GameEntity game = game();
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);
      when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> teamService.kickTeam(teamId.toString(), "AKKU"));

      assertEquals("Team with id " + teamId + " does not exist", exception.getMessage());
    }

    @Test
    void getTeamScoresCachesRepositoryResultAndReusesIt() throws Exception {
      final UUID teamId = UUID.randomUUID();
      final List<TeamScoreProjection> projections = new ArrayList<>();
      projections.add(projection(teamId, "Red", "red.png", null, 12));
      when(teamRepository.getTeamScores("AKKU")).thenReturn(projections);

      final Object first = teamService.getTeamScores("AKKU");
      final Object second = teamService.getTeamScores("AKKU");

      assertSame(first, second);
      verify(teamRepository).getTeamScores("AKKU");
    }

    @Test
    void getTeamScoresRejectsMissingGameWhenRepositoryReturnsNull() {
      when(teamRepository.getTeamScores("MISS")).thenReturn(null);

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class, () -> teamService.getTeamScores("MISS"));

      assertEquals("Game with code MISS could not be found", exception.getMessage());
    }

    @Test
    void saveTeamAnswerUpdatesCacheAndThenPointsCanBeReadBack() throws Exception {
      final UUID teamId = UUID.randomUUID();
      final UUID scheduleId = UUID.randomUUID();
      final List<TeamScoreProjection> projections = new ArrayList<>();
      projections.add(projection(teamId, "Red", "red.png", null, 10));
      when(teamRepository.getTeamScores("AKKU")).thenReturn(projections);

      teamService.saveTeamAnswer(teamId, scheduleId, 40, "AKKU");

      assertEquals(40, teamService.getTeamPoints(teamId, "AKKU"));
    }

    @Test
    void findByRoomCodeAndFindNextChoosingTeamDelegateToRepository() {
      final List<CreateTeamResponse> teams = new ArrayList<>();
      teams.add(new CreateTeamResponse(UUID.randomUUID(), "Red", "red.png"));
      final ChoosingTeam choosingTeam =
          new ChoosingTeam() {
            @Override
            public String getId() {
              return UUID.randomUUID().toString();
            }

            @Override
            public String getName() {
              return "Blue";
            }

            @Override
            public String getImage() {
              return "blue.png";
            }
          };
      final UUID gameId = UUID.randomUUID();
      when(teamRepository.findByGameId("AKKU")).thenReturn(teams);
      when(teamRepository.findNext(gameId, 4)).thenReturn(choosingTeam);

      assertSame(teams, teamService.findByRoomCode("AKKU"));
      assertSame(choosingTeam, teamService.findNextChoosingTeam(gameId, 4));
    }

    private GameEntity game() {
      return ServiceImplTestDataFactory.game();
    }

    private TeamScoreProjection projection(
        final UUID id,
        final String name,
        final String image,
        final UUID scheduleId,
        final Integer score) {
      return new TeamScoreProjection() {
        @Override
        public UUID getTeam() {
          return id;
        }

        @Override
        public String getName() {
          return name;
        }

        @Override
        public String getImage() {
          return image;
        }

        @Override
        public UUID getSchedule() {
          return scheduleId;
        }

        @Override
        public Integer getScore() {
          return score;
        }
      };
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class TeamValidationAndRepositoryFailures {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;
    @Mock private AssetGateway assetGateway;

    @InjectMocks private TeamServiceImpl teamService;
    @InjectMocks private SongServiceImpl songService;

    @Test
    void createTeamRejectsBlankNameOrImage() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> teamService.createTeam(new CreateTeamRequest(" ", "A", " "), "AKKU"));

      assertEquals("The request's body is missing a name and/or a picture", exception.getMessage());
    }

    @Test
    void kickTeamRejectsUnknownTeamId() throws Exception {
      final GameEntity game = game();
      final String teamId = UUID.randomUUID().toString();
      when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);
      when(teamRepository.findById(UUID.fromString(teamId))).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class, () -> teamService.kickTeam(teamId, "AKKU"));

      assertEquals("Team with id " + teamId + " does not exist", exception.getMessage());
      verify(broadcastGateway, never()).toTv(anyString(), anyString());
    }

    @Test
    void getTeamScoresCachesFirstLookupAndReusesIt() throws Exception {
      final UUID teamId = UUID.randomUUID();
      when(teamRepository.getTeamScores("AKKU"))
          .thenReturn(new ArrayList<>(List.of(projection(teamId, 10, UUID.randomUUID()))));

      final Object first = teamService.getTeamScores("AKKU");
      final Object second = teamService.getTeamScores("AKKU");

      assertSame(first, second);
      verify(teamRepository).getTeamScores("AKKU");
    }

    @Test
    void getTeamPointsRejectsUnknownGameWhenRepositoryReturnsNullScores() {
      when(teamRepository.getTeamScores("AKKU")).thenReturn(null);

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> teamService.getTeamPoints(UUID.randomUUID(), "AKKU"));

      assertEquals("Game with code AKKU could not be found", exception.getMessage());
    }

    private static GameEntity game() {
      return ServiceImplTestDataFactory.game();
    }

    private static com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection projection(
        final UUID teamId, final Integer score, final UUID scheduleId) {
      return new com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection() {
        @Override
        public UUID getTeam() {
          return teamId;
        }

        @Override
        public String getImage() {
          return "img.png";
        }

        @Override
        public String getName() {
          return "Name";
        }

        @Override
        public Integer getScore() {
          return score;
        }

        @Override
        public UUID getSchedule() {
          return scheduleId;
        }
      };
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class TeamOrderingAndRepositoryDelegation {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;
    @Mock private AssetGateway assetGateway;

    @InjectMocks private TeamServiceImpl teamService;
    @InjectMocks private SongServiceImpl songService;

    @Test
    void createTeamPersistsBeforeBroadcasting() throws Exception {
      final GameEntity game = game();
      final CreateTeamRequest request = new CreateTeamRequest("Blue", "captain", "blue.png");
      when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);

      final CreateTeamResponse response = teamService.createTeam(request, "AKKU");

      final var inOrder = inOrder(teamRepository, broadcastGateway);
      inOrder
          .verify(teamRepository)
          .saveAndFlush(org.mockito.ArgumentMatchers.any(TeamEntity.class));
      inOrder.verify(broadcastGateway).toTv(org.mockito.ArgumentMatchers.eq("AKKU"), anyString());
      assertEquals("Blue", response.getName());
      assertEquals("blue.png", response.getImage());
    }

    @Test
    void kickTeamDeletesFlushesThenBroadcasts() throws Exception {
      final GameEntity game = game();
      final TeamEntity team = team(game, "Red");
      when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

      teamService.kickTeam(team.getId().toString(), "AKKU");

      final var inOrder = inOrder(teamRepository, broadcastGateway);
      inOrder.verify(teamRepository).delete(team);
      inOrder.verify(teamRepository).flush();
      inOrder
          .verify(broadcastGateway)
          .toTv("AKKU", "{\"type\":\"kick_team\",\"uuid\":\"" + team.getId() + "\"}");
    }

    @Test
    void saveTeamAnswerReusesCacheAcrossCalls() throws Exception {
      final UUID teamId = UUID.randomUUID();
      final UUID scheduleOne = UUID.randomUUID();
      final UUID scheduleTwo = UUID.randomUUID();
      when(teamRepository.getTeamScores("AKKU"))
          .thenReturn(new ArrayList<>(List.of(projection(teamId, 10, scheduleOne))));

      teamService.saveTeamAnswer(teamId, scheduleOne, 40, "AKKU");
      teamService.saveTeamAnswer(teamId, scheduleTwo, 20, "AKKU");
      final Object scores = teamService.getTeamScores("AKKU");

      verify(teamRepository).getTeamScores("AKKU");
      assertSame(scores, teamService.getTeamScores("AKKU"));
    }

    @Test
    void findByRoomCodeAndFindByIdDelegateToRepository() {
      final TeamEntity team = team(game(), "Green");
      final List<CreateTeamResponse> teams = new ArrayList<>(List.of(new CreateTeamResponse(team)));
      when(teamRepository.findByGameId("AKKU")).thenReturn(teams);
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

      assertSame(teams, teamService.findByRoomCode("AKKU"));
      assertSame(team, teamService.findById(team.getId()).orElseThrow());
    }

    @Test
    void findNextChoosingTeamDelegatesToRepository() {
      final ChoosingTeam choosingTeam =
          new ChoosingTeam() {
            @Override
            public String getId() {
              return UUID.randomUUID().toString();
            }

            @Override
            public String getName() {
              return "Yellow";
            }

            @Override
            public String getImage() {
              return "yellow.png";
            }
          };
      final UUID gameId = UUID.randomUUID();
      when(teamRepository.findNext(gameId, 5)).thenReturn(choosingTeam);

      assertSame(choosingTeam, teamService.findNextChoosingTeam(gameId, 5));
    }

    private static GameEntity game() {
      return ServiceImplTestDataFactory.game();
    }

    private static TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name + ".png");
      return team;
    }

    private static com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection projection(
        final UUID teamId, final Integer score, final UUID scheduleId) {
      return new com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection() {
        @Override
        public UUID getTeam() {
          return teamId;
        }

        @Override
        public String getImage() {
          return "img.png";
        }

        @Override
        public String getName() {
          return "Name";
        }

        @Override
        public Integer getScore() {
          return score;
        }

        @Override
        public UUID getSchedule() {
          return scheduleId;
        }
      };
    }
  }
}
