package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.GuessNotAllowedException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InterruptServiceImplWebSocketTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class InterruptServiceImplIteration7TransitionWsTest {
          @Nested
          @ExtendWith(MockitoExtension.class)
          class Stage2TransitionContracts {
            @Mock private TeamService teamService;
            @Mock private InterruptRepository interruptRepository;
            @Mock private GameRepository gameRepository;
            @Mock private ScheduleRepository scheduleRepository;
            @Mock private BroadcastGateway broadcastGateway;
            @Mock private PresenceGateway presenceGateway;

            @InjectMocks private InterruptServiceImpl service;

            @Test
            void teamInterruptPersistsBeforeBroadcastingPauseFrame() throws Exception {
              final GameEntity game = game("AKKU");
              final TeamEntity team = team(game, "Blue");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);
              final ArgumentCaptor<InterruptEntity> savedInterrupt = ArgumentCaptor.forClass(InterruptEntity.class);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
              when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
              when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
                  .thenReturn(List.of(new InterruptFrame(LocalDateTime.now(), null)));
              when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId())).thenReturn(null);
              when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId())).thenReturn(null);

              service.interrupt("AKKU", team.getId());

              final InOrder inOrder = inOrder(interruptRepository, broadcastGateway);
              inOrder.verify(interruptRepository).saveAndFlush(savedInterrupt.capture());
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());
              final InterruptEntity persisted = savedInterrupt.getValue();
              assertEquals(schedule.getId(), persisted.getScheduleId().getId());
              assertEquals(team.getId(), persisted.getTeamId().getId());
              assertNotNull(persisted.getArrivedAt());

              final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
              verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
              assertTrue(payload.getValue().contains("\"type\":\"pause\""));
              assertTrue(payload.getValue().contains("\"answeringTeamId\":\"" + team.getId() + "\""));
              assertTrue(payload.getValue().contains("\"interruptId\":\"" + persisted.getId() + "\""));
            }

            @Test
            void systemPausePersistsBeforeBroadcastingPauseFrameWithNullTeam() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);
              final ArgumentCaptor<InterruptEntity> savedInterrupt = ArgumentCaptor.forClass(InterruptEntity.class);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);

              service.interrupt("AKKU", null);

              final InOrder inOrder = inOrder(interruptRepository, broadcastGateway);
              inOrder.verify(interruptRepository).saveAndFlush(savedInterrupt.capture());
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());
              assertNull(savedInterrupt.getValue().getTeamId());

              final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
              verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
              assertTrue(payload.getValue().contains("\"answeringTeamId\":\"null\""));
              assertTrue(payload.getValue().contains("\"interruptId\":\"" + savedInterrupt.getValue().getId() + "\""));
            }

            @Test
            void teamInterruptIsRejectedWhenAnotherTeamIsAlreadyAnsweringAndDoesNotBroadcast() throws Exception {
              final GameEntity game = game("AKKU");
              final TeamEntity first = team(game, "Blue");
              final TeamEntity second = team(game, "Red");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);
              final InterruptEntity activeTeamInterrupt = new InterruptEntity(UUID.randomUUID());
              activeTeamInterrupt.setTeamId(first);
              activeTeamInterrupt.setCorrect(null);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
              when(teamService.findById(second.getId())).thenReturn(Optional.of(second));
              when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
                  .thenReturn(List.of(new InterruptFrame(LocalDateTime.now(), null)));
              when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
                  .thenReturn(activeTeamInterrupt);
              when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId())).thenReturn(null);

              final GuessNotAllowedException exception =
                  assertThrows(GuessNotAllowedException.class, () -> service.interrupt("AKKU", second.getId()));

              assertEquals("Someone's already guessing", exception.getMessage());
              verify(interruptRepository, never()).saveAndFlush(any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            @Test
            void teamInterruptIsRejectedWhenSystemPauseIsStillActiveAndDoesNotBroadcast() throws Exception {
              final GameEntity game = game("AKKU");
              final TeamEntity team = team(game, "Blue");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);
              final InterruptEntity systemPause = new InterruptEntity(UUID.randomUUID());
              systemPause.setResolvedAt(null);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
              when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
              when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
                  .thenReturn(List.of(new InterruptFrame(LocalDateTime.now(), null)));
              when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId())).thenReturn(null);
              when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId())).thenReturn(systemPause);

              final GuessNotAllowedException exception =
                  assertThrows(GuessNotAllowedException.class, () -> service.interrupt("AKKU", team.getId()));

              assertEquals("The game is paused", exception.getMessage());
              verify(interruptRepository, never()).saveAndFlush(any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            @Test
            void answerWrongResolvesInterruptSavesTeamAnswerAndBroadcastsExactlyOneAnswerFrame() throws Exception {
              final GameEntity game = game("AKKU");
              final TeamEntity team = team(game, "Blue");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);
              final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
              answer.setTeamId(team);
              answer.setScheduleId(schedule);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
              when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
              when(teamService.getTeamPoints(team.getId(), "AKKU")).thenReturn(50);

              service.answer(answer.getId(), new AnswerRequest(false), "AKKU");

              final InOrder inOrder = inOrder(interruptRepository, teamService, broadcastGateway);
              inOrder.verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
              inOrder.verify(interruptRepository).save(answer);
              inOrder.verify(teamService).saveTeamAnswer(team.getId(), schedule.getId(), 40, "AKKU");
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());

              assertEquals(Boolean.FALSE, answer.isCorrect());
              assertNotNull(answer.getResolvedAt());
              assertEquals(40, answer.getScoreOrScenarioId());
              assertNull(schedule.getRevealedAt());
              verify(scheduleRepository, never()).saveAndFlush(any());

              final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
              verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
              assertTrue(payload.getValue().contains("\"type\":\"answer\""));
              assertTrue(payload.getValue().contains("\"teamId\":\"" + team.getId() + "\""));
              assertTrue(payload.getValue().contains("\"scheduleId\":\"" + schedule.getId() + "\""));
              assertTrue(payload.getValue().contains("\"correct\":false"));
            }

            @Test
            void answerCorrectRevealsSongBeforeBroadcastingAnswerFrame() throws Exception {
              final GameEntity game = game("AKKU");
              final TeamEntity team = team(game, "Blue");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);
              final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
              answer.setTeamId(team);
              answer.setScheduleId(schedule);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
              when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
              when(teamService.getTeamPoints(team.getId(), "AKKU")).thenReturn(5);

              service.answer(answer.getId(), new AnswerRequest(true), "AKKU");

              final InOrder inOrder = inOrder(interruptRepository, scheduleRepository, teamService, broadcastGateway);
              inOrder.verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
              inOrder.verify(interruptRepository).save(answer);
              inOrder.verify(scheduleRepository).saveAndFlush(schedule);
              inOrder.verify(teamService).saveTeamAnswer(team.getId(), schedule.getId(), 35, "AKKU");
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());

              assertEquals(Boolean.TRUE, answer.isCorrect());
              assertNotNull(answer.getResolvedAt());
              assertEquals(35, answer.getScoreOrScenarioId());
              assertNotNull(schedule.getRevealedAt());
            }

            @Test
            void answerRequiresBothAppsAndDoesNotBroadcastFalseSuccess() throws Exception {
              final GameEntity game = game("AKKU");
              final UUID answerId = UUID.randomUUID();
              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

              final AppNotRegisteredException exception =
                  assertThrows(
                      AppNotRegisteredException.class,
                      () -> service.answer(answerId, new AnswerRequest(true), "AKKU"));

              assertEquals("Both apps need to be present in order to continue", exception.getMessage());
              verify(interruptRepository, never()).findById(any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            @Test
            void resolveErrorsBroadcastsPreviousScenarioAfterRepositoryResolution() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
              when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

              service.resolveErrors(schedule.getId(), "AKKU");

              final InOrder inOrder = inOrder(interruptRepository, broadcastGateway);
              inOrder.verify(interruptRepository).findPreviousScenarioId(schedule.getId());
              inOrder.verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), eq("{\"type\":\"error_solved\",\"previousScenario\":4}"));
            }

            @Test
            void resolveErrorsRequiresBothAppsAndDoesNotBroadcastFalseResume() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = playingSchedule(game, 20.0, 8.0);

              when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

              final AppNotRegisteredException exception =
                  assertThrows(
                      AppNotRegisteredException.class,
                      () -> service.resolveErrors(schedule.getId(), "AKKU"));

              assertEquals("Both apps need to be present in order to continue", exception.getMessage());
              verify(interruptRepository, never()).resolveErrors(any(), any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            private GameEntity game(final String code) {
              final GameEntity game = new GameEntity(UUID.randomUUID());
              game.setCode(code);
              game.setStage(2);
              return game;
            }

            private TeamEntity team(final GameEntity game, final String name) {
              final TeamEntity team = new TeamEntity(UUID.randomUUID());
              team.setGameId(game);
              team.setName(name);
              team.setImage(name.toLowerCase() + ".png");
              return team;
            }

            private ScheduleEntity playingSchedule(final GameEntity game, final double snippetDuration, final double answerDuration) {
              final SongEntity song = new SongEntity(UUID.randomUUID());
              song.setSnippetDuration(snippetDuration);
              song.setAnswerDuration(answerDuration);
              song.setName("Track");
              song.setAuthors("Artist");
              final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
              final TrackEntity track = new TrackEntity(UUID.randomUUID());
              track.setAlbumId(album);
              track.setSongId(song);
              final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
              category.setGameId(game);
              final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
              schedule.setCategoryId(category);
              schedule.setTrackId(track);
              schedule.setStartedAt(LocalDateTime.now().minusSeconds(4));
              return schedule;
            }
          }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class InterruptServiceImplStage2BroadcastSerializationTest {
            @Mock
            private TeamService teamService;
            @Mock
            private InterruptRepository interruptRepository;
            @Mock
            private GameRepository gameRepository;
            @Mock
            private ScheduleRepository scheduleRepository;
            @Mock
            private BroadcastGateway broadcastGateway;
            @Mock
            private PresenceGateway presenceGateway;

            private InterruptServiceImpl service;
            private final ObjectMapper mapper = new ObjectMapper();

            @BeforeEach
            void setUp() {
                service = new InterruptServiceImpl();
                ReflectionTestUtils.setField(service, "teamService", teamService);
                ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
                ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
                ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
                ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
                ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
            }

            @Test
            void teamInterruptBroadcastUsesStablePauseSchemaWithStringIds() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final UUID teamId = UUID.randomUUID();
                final UUID scheduleId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final TeamEntity team = mock(TeamEntity.class);
                final GameEntity teamGame = new GameEntity(gameId);
                final ScheduleEntity schedule = mock(ScheduleEntity.class);
                final TrackEntity track = mock(TrackEntity.class);
                final SongEntity song = mock(SongEntity.class);
                final LocalDateTime startedAt = LocalDateTime.now().minusSeconds(5);

                when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
                when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
                when(teamService.findById(teamId)).thenReturn(Optional.of(team));
                when(team.getGameId()).thenReturn(teamGame);
                when(team.getId()).thenReturn(teamId);
                when(schedule.getStartedAt()).thenReturn(startedAt);
                when(schedule.getId()).thenReturn(scheduleId);
                when(schedule.getTrackId()).thenReturn(track);
                when(track.getSongId()).thenReturn(song);
                when(song.getSnippetDuration()).thenReturn(25.0d);

                when(schedule.getInterruptList()).thenReturn(new ArrayList<>());
                when(interruptRepository.findInterrupts(startedAt, scheduleId)).thenReturn(new ArrayList<>());
                when(interruptRepository.findLastAnswer(startedAt, scheduleId)).thenReturn(null);
                when(interruptRepository.findLastPause(startedAt, scheduleId)).thenReturn(null);

                service.interrupt("ROOM", teamId);

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("pause", parsed.get("type"));
                assertEquals(teamId.toString(), parsed.get("answeringTeamId"));
                assertNotNull(parsed.get("interruptId"));
                assertEquals(3, parsed.size());
            }

            @Test
            void systemInterruptBroadcastKeepsCurrentLiteralNullStringContract() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());

                when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
                when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);

                service.interrupt("ROOM", null);

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("pause", parsed.get("type"));
                assertEquals("null", parsed.get("answeringTeamId"));
                assertNotNull(parsed.get("interruptId"));
            }

            @Test
            void answerBroadcastPreservesBooleanAndIdentifierFieldsWithoutRevealLeak() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final UUID teamId = UUID.randomUUID();
                final UUID scheduleId = UUID.randomUUID();
                final UUID answerId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final InterruptEntity answer = mock(InterruptEntity.class);
                final TeamEntity team = mock(TeamEntity.class);
                final GameEntity teamGame = mock(GameEntity.class);
                final ScheduleEntity schedule = mock(ScheduleEntity.class);

                when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
                when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
                when(interruptRepository.findById(answerId)).thenReturn(Optional.of(answer));
                when(answer.getTeamId()).thenReturn(team);
                when(team.getGameId()).thenReturn(teamGame);
                when(teamGame.getCode()).thenReturn("ROOM");
                when(answer.isCorrect()).thenReturn(null);
                when(answer.getResolvedAt()).thenReturn(null);
                when(team.getId()).thenReturn(teamId);
                when(answer.getScheduleId()).thenReturn(schedule);
                when(schedule.getId()).thenReturn(scheduleId);
                when(teamService.getTeamPoints(teamId, "ROOM")).thenReturn(10);

                service.answer(answerId, new AnswerRequest(false), "ROOM");

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("answer", parsed.get("type"));
                assertEquals(teamId.toString(), parsed.get("teamId"));
                assertEquals(scheduleId.toString(), parsed.get("scheduleId"));
                assertEquals(Boolean.FALSE, parsed.get("correct"));
                assertFalse(parsed.containsKey("revealed"));
            }

            @Test
            void resolveErrorsBroadcastKeepsPreviousScenarioNumericContract() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final UUID scheduleId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final ScheduleEntity schedule = new ScheduleEntity(scheduleId);

                when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
                when(interruptRepository.findPreviousScenarioId(scheduleId)).thenReturn(4);

                service.resolveErrors(scheduleId, "ROOM");

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("error_solved", parsed.get("type"));
                assertEquals(4, ((Number) parsed.get("previousScenario")).intValue());
                assertEquals(2, parsed.size());
                verify(interruptRepository).resolveErrors(eq(scheduleId), any());
            }
    }

}
