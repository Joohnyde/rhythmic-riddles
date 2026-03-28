package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cevapinxile.cestereg.api.quiz.dto.response.CategoryPreview;
import com.cevapinxile.cestereg.api.quiz.dto.response.CategorySimple;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameServiceImplWebSocketTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameServiceImplIllegalTransitionNonEmissionTest {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    private GameServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new GameServiceImpl();
      ReflectionTestUtils.setField(service, "teamService", teamService);
      ReflectionTestUtils.setField(service, "interruptService", interruptService);
      ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
      ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
    }

    @Nested
    class IsChangeStageLegalNonEmissionSignals {
      @Test
      void unknownRoomThrowsAndDoesNotTouchPresenceOrBroadcasting() {
        when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

        assertThrows(
            InvalidReferencedObjectException.class, () -> service.isChangeStageLegal(1, "MISS"));

        verify(presenceGateway, never()).areBothPresent(anyString());
        verifyNoInteractions(broadcastGateway);
      }

      @Test
      void invalidTargetStageThrowsBeforePresenceCheck() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);

        assertThrows(InvalidArgumentException.class, () -> service.isChangeStageLegal(9, "ROOM"));

        verify(presenceGateway, never()).areBothPresent(anyString());
        verifyNoInteractions(broadcastGateway);
      }

      @Test
      void wrongCurrentStateThrowsAndDoesNotCheckPresence() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);

        assertThrows(WrongGameStateException.class, () -> service.isChangeStageLegal(3, "ROOM"));

        verify(presenceGateway, never()).areBothPresent(anyString());
        verifyNoInteractions(broadcastGateway);
      }

      @Test
      void missingAppsThrowsAndDoesNotPersistOrBroadcast() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(false);

        assertThrows(AppNotRegisteredException.class, () -> service.isChangeStageLegal(2, "ROOM"));

        verify(gameRepository, never()).saveAndFlush(any());
        verifyNoInteractions(broadcastGateway);
      }
    }

    @Nested
    class ChangeStageNonEmission {
      @Test
      void failedLegalityCheckNeverPersistsOrBroadcasts() throws Exception {
        final GameServiceImpl failingSpy = spy(service);
        doThrow(new WrongGameStateException("nope")).when(failingSpy).isChangeStageLegal(2, "ROOM");

        assertThrows(WrongGameStateException.class, () -> failingSpy.changeStage(2, "ROOM"));

        verify(gameRepository, never()).saveAndFlush(any());
        verifyNoInteractions(broadcastGateway);
      }

      @Test
      void contextFetchFailureAfterPersistMustNotBroadcastHalfBuiltSnapshot() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final GameServiceImpl failingSpy = spy(service);
        doReturn(game).when(failingSpy).isChangeStageLegal(1, "ROOM");
        doThrow(new RuntimeException("boom")).when(failingSpy).contextFetch("ROOM");

        assertThrows(RuntimeException.class, () -> failingSpy.changeStage(1, "ROOM"));

        verify(game).setStage(1);
        verify(gameRepository).saveAndFlush(game);
        verifyNoInteractions(broadcastGateway);
      }

      @Test
      void successfulTransitionBroadcastsExactlyOnceToRoomSnapshotPath() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final GameServiceImpl okSpy = spy(service);
        doReturn(game).when(okSpy).isChangeStageLegal(0, "ROOM");
        doReturn(new HashMap<String, Object>()).when(okSpy).contextFetch("ROOM");

        okSpy.changeStage(0, "ROOM");

        verify(gameRepository).saveAndFlush(game);
        verify(broadcastGateway, times(1)).broadcast(eq("ROOM"), anyString());
      }
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameServiceImplIteration7Stage2RecoveryTest {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    private GameServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new GameServiceImpl();
      ReflectionTestUtils.setField(service, "teamService", teamService);
      ReflectionTestUtils.setField(service, "interruptService", interruptService);
      ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
      ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
    }

    @Nested
    class RecoveryMatrix {
      @Test
      void reconnectDuringNormalPlaybackReturnsSeekRemainingAndNoPauseFlags() throws Exception {
        final GameEntity game = game();
        final ScheduleEntity schedule = schedule(game, 25.0, 7.0);
        final Object scores = List.of("Blue:10", "Red:0");

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.getTeamScores("AKKU")).thenReturn(scores);
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(4000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {null, null});

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals("welcome", payload.get("type"));
        assertEquals("songs", payload.get("stage"));
        assertSame(scores, payload.get("scores"));
        assertEquals(4.0, (Double) payload.get("seek"));
        assertEquals(21.0, (Double) payload.get("remaining"));
        assertFalse(payload.containsKey("answeringTeam"));
        assertFalse(payload.containsKey("interruptId"));
        assertFalse(payload.containsKey("error"));
        assertFalse(payload.containsKey("revealed"));
      }

      @Test
      void reconnectAfterTeamInterruptReturnsAnsweringSnapshotAndPrefersItOverSystemPause()
          throws Exception {
        final GameEntity game = game();
        final ScheduleEntity schedule = schedule(game, 25.0, 7.0);
        final TeamEntity team = team(game, "Blue");
        final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
        answer.setTeamId(team);
        answer.setScheduleId(schedule);
        answer.setCorrect(null);
        final InterruptEntity systemPause = new InterruptEntity(UUID.randomUUID());
        systemPause.setResolvedAt(null);

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.getTeamScores("AKKU")).thenReturn(List.of());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(2000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {answer, systemPause});

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals("songs", payload.get("stage"));
        assertEquals(2.0, (Double) payload.get("seek"));
        assertEquals(23.0, (Double) payload.get("remaining"));
        assertEquals(answer.getId(), payload.get("interruptId"));
        final CreateTeamResponse answeringTeam = (CreateTeamResponse) payload.get("answeringTeam");
        assertEquals(team.getId(), answeringTeam.getId());
        assertEquals(team.getName(), answeringTeam.getName());
        assertFalse(payload.containsKey("error"));
      }

      @Test
      void reconnectAfterWrongAnswerResolutionReturnsPlaybackRecoveryWithoutAnsweringState()
          throws Exception {
        final GameEntity game = game();
        final ScheduleEntity schedule = schedule(game, 25.0, 7.0);
        final TeamEntity team = team(game, "Blue");
        final InterruptEntity resolvedAnswer = new InterruptEntity(UUID.randomUUID());
        resolvedAnswer.setTeamId(team);
        resolvedAnswer.setScheduleId(schedule);
        resolvedAnswer.setCorrect(false);
        resolvedAnswer.setResolvedAt(LocalDateTime.now().minusSeconds(1));
        final InterruptEntity resolvedSystemPause = new InterruptEntity(UUID.randomUUID());
        resolvedSystemPause.setResolvedAt(LocalDateTime.now().minusSeconds(1));

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.getTeamScores("AKKU")).thenReturn(List.of());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(9000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {resolvedAnswer, resolvedSystemPause});

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals(9.0, (Double) payload.get("seek"));
        assertEquals(16.0, (Double) payload.get("remaining"));
        assertFalse(payload.containsKey("answeringTeam"));
        assertFalse(payload.containsKey("interruptId"));
        assertFalse(payload.containsKey("error"));
        assertFalse(payload.containsKey("revealed"));
      }

      @Test
      void reconnectWhileSystemPauseIsActiveReturnsErrorSnapshotWithoutAnsweringTeam()
          throws Exception {
        final GameEntity game = game();
        final ScheduleEntity schedule = schedule(game, 25.0, 7.0);
        final InterruptEntity systemPause = new InterruptEntity(UUID.randomUUID());
        systemPause.setResolvedAt(null);

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.getTeamScores("AKKU")).thenReturn(List.of());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(3000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {null, systemPause});

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals(Boolean.TRUE, payload.get("error"));
        assertEquals(3.0, (Double) payload.get("seek"));
        assertEquals(22.0, (Double) payload.get("remaining"));
        assertFalse(payload.containsKey("answeringTeam"));
        assertFalse(payload.containsKey("interruptId"));
      }

      @Test
      void reconnectAfterSnippetEndsButBeforeRevealReturnsEndedNotRevealedSnapshot()
          throws Exception {
        final GameEntity game = game();
        final ScheduleEntity schedule = schedule(game, 5.0, 7.0);

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.getTeamScores("AKKU")).thenReturn(List.of());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(7000L);

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals(Boolean.FALSE, payload.get("revealed"));
        assertFalse(payload.containsKey("seek"));
        assertFalse(payload.containsKey("remaining"));
        assertFalse(payload.containsKey("answeringTeam"));
        assertFalse(payload.containsKey("error"));
      }

      @Test
      void reconnectAfterRevealReturnsPostSongSnapshotWithCorrectTeam() throws Exception {
        final GameEntity game = game();
        final ScheduleEntity schedule = schedule(game, 25.0, 7.0);
        final UUID bravo = UUID.randomUUID();
        schedule.setRevealedAt(LocalDateTime.now());

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.getTeamScores("AKKU")).thenReturn(List.of());
        when(interruptService.findCorrectAnswer(schedule.getId(), "AKKU")).thenReturn(bravo);

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals(Boolean.TRUE, payload.get("revealed"));
        assertEquals(bravo, payload.get("bravo"));
        assertFalse(payload.containsKey("seek"));
        assertFalse(payload.containsKey("remaining"));
      }

      @Test
      void reconnectAfterProgressToNextSongReturnsWelcomeForNewSchedule() throws Exception {
        final GameEntity game = game();
        final ScheduleEntity nextSong = schedule(game, 31.0, 9.0);
        final Object scores = List.of("Blue:40");

        when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(nextSong);
        when(teamService.getTeamScores("AKKU")).thenReturn(scores);
        when(interruptService.calculateSeek(nextSong.getStartedAt(), nextSong.getId()))
            .thenReturn(0L);
        when(interruptService.getLastTwoInterrupts(nextSong.getStartedAt(), nextSong.getId()))
            .thenReturn(new InterruptEntity[] {null, null});

        final HashMap<String, Object> payload = service.contextFetch("AKKU");

        assertEquals(nextSong.getId(), payload.get("scheduleId"));
        assertEquals(nextSong.getTrackId().getSongId().getId(), payload.get("songId"));
        assertSame(scores, payload.get("scores"));
        assertEquals(0.0, (Double) payload.get("seek"));
        assertEquals(31.0, (Double) payload.get("remaining"));
        assertFalse(payload.containsKey("revealed"));
      }

      private GameEntity game() {
        final GameEntity game = new GameEntity(UUID.randomUUID());
        game.setCode("AKKU");
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

      private ScheduleEntity schedule(
          final GameEntity game, final double snippetDuration, final double answerDuration) {
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
        schedule.setStartedAt(LocalDateTime.now().minusSeconds(10));
        return schedule;
      }
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameServiceImplNonStage2RecoveryTest {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    private GameServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new GameServiceImpl();
      ReflectionTestUtils.setField(service, "teamService", teamService);
      ReflectionTestUtils.setField(service, "interruptService", interruptService);
      ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
      ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
    }

    @Nested
    class UnknownRoom {
      @Test
      void contextFetchReturnsEmptyPayloadWhenRoomDoesNotExist() throws Exception {
        when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

        final HashMap<?, ?> payload = service.contextFetch("MISS");

        assertTrue(payload.isEmpty());
      }
    }

    @Nested
    class LobbyRecovery {
      @Test
      void contextFetchLobbyReturnsWelcomeStageAndCurrentTeamList() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final List<CreateTeamResponse> teams =
            List.of(mock(CreateTeamResponse.class), mock(CreateTeamResponse.class));

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);
        when(teamService.findByRoomCode("ROOM")).thenReturn(teams);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("lobby", payload.get("stage"));
        assertSame(teams, payload.get("teams"));
        assertFalse(payload.containsKey("albums"));
        assertFalse(payload.containsKey("selected"));
        assertFalse(payload.containsKey("scores"));
      }
    }

    @Nested
    class AlbumsRecovery {
      @Test
      void contextFetchAlbumsPickerStateWhenNoLastCategoryExists() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final ChoosingTeam choosingTeam = mock(ChoosingTeam.class);
        final List<CategorySimple> albums =
            List.of(mock(CategorySimple.class), mock(CategorySimple.class));
        final UUID gameId = UUID.randomUUID();
        final UUID teamId = UUID.randomUUID();

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(game.getMaxAlbums()).thenReturn(6);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(null);
        when(categoryRepository.findByGameId(gameId)).thenReturn(albums);
        when(teamService.findNextChoosingTeam(gameId, 6)).thenReturn(choosingTeam);
        when(choosingTeam.getId()).thenReturn(teamId.toString());
        when(choosingTeam.getName()).thenReturn("Pickers");
        when(choosingTeam.getImage()).thenReturn("pickers.png");

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertSame(albums, payload.get("albums"));
        assertTrue(payload.containsKey("team"));
        assertFalse(payload.containsKey("selected"));

        final CreateTeamResponse team =
            assertInstanceOf(CreateTeamResponse.class, payload.get("team"));
        assertEquals(teamId, team.getId());
        assertEquals("Pickers", team.getName());
        assertEquals("pickers.png", team.getImage());
      }

      @Test
      void contextFetchAlbumsPickerStateWhenPreviousCategoryAlreadyStartedAndNotAtAlbumLimit()
          throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final LastCategory last = mock(LastCategory.class);
        final List<CategorySimple> albums = List.of(mock(CategorySimple.class));
        final UUID gameId = UUID.randomUUID();

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(game.getMaxAlbums()).thenReturn(5);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(last);
        when(last.isStarted()).thenReturn(true);
        when(last.getOrdinalNumber()).thenReturn(3);
        when(categoryRepository.findByGameId(gameId)).thenReturn(albums);
        when(teamService.findNextChoosingTeam(gameId, 5)).thenReturn(null);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("albums", payload.get("stage"));
        assertSame(albums, payload.get("albums"));
        assertTrue(payload.containsKey("team"));
        assertNull(payload.get("team"));
        assertFalse(payload.containsKey("selected"));
      }

      @Test
      void contextFetchAlbumsSelectedStateWhenCategoryPickedButNotStarted() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final LastCategory selected = new LastCategory();
        final UUID gameId = UUID.randomUUID();

        selected.setCategoryId(UUID.randomUUID());
        selected.setStarted(false);
        selected.setOrdinalNumber(2);
        selected.setChosenCategoryPreview(new CategoryPreview("Rock", "rock.png"));

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(selected);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertSame(selected, payload.get("selected"));
        assertFalse(payload.containsKey("albums"));
        assertFalse(payload.containsKey("team"));
      }

      @Test
      void contextFetchAlbumsImpossibleEndBranchStillKeepsStageStableWithoutPickerOrSelected()
          throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final LastCategory last = mock(LastCategory.class);
        final UUID gameId = UUID.randomUUID();

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(game.getMaxAlbums()).thenReturn(4);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(last);
        when(last.isStarted()).thenReturn(true);
        when(last.getOrdinalNumber()).thenReturn(4);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertFalse(payload.containsKey("albums"));
        assertFalse(payload.containsKey("team"));
        assertFalse(payload.containsKey("selected"));
      }
    }

    @Nested
    class WinnerRecovery {
      @Test
      void contextFetchWinnerReturnsWelcomeStageAndFinalScores() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final List<String> scores = List.of("A:10", "B:5");

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(3);
        when(teamService.getTeamScores("ROOM")).thenReturn(scores);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("winner", payload.get("stage"));
        assertSame(scores, payload.get("scores"));
        assertFalse(payload.containsKey("teams"));
        assertFalse(payload.containsKey("albums"));
        assertFalse(payload.containsKey("selected"));
      }
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameServiceImplNonStage2SchemaRegressionTest {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    private GameServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new GameServiceImpl();
      ReflectionTestUtils.setField(service, "teamService", teamService);
      ReflectionTestUtils.setField(service, "interruptService", interruptService);
      ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
      ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
    }

    @Nested
    class LobbySchema {
      @Test
      void lobbyWelcomeContainsOnlyLobbyFieldsAndNoStageSpecificLeaks() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final CreateTeamResponse teamA = mock(CreateTeamResponse.class);
        final CreateTeamResponse teamB = mock(CreateTeamResponse.class);
        final List<CreateTeamResponse> teams = List.of(teamA, teamB);

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);
        when(teamService.findByRoomCode("ROOM")).thenReturn(teams);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("lobby", payload.get("stage"));
        assertSame(teams, payload.get("teams"));
        assertForbiddenNonStage2LeakKeys(payload);
      }
    }

    @Nested
    class AlbumsSchema {
      @Test
      void albumsPickerStateContainsAlbumsAndChoosingTeamButNoSongFields() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final UUID gameId = UUID.randomUUID();
        final UUID choosingTeamId = UUID.randomUUID();
        final ChoosingTeam choosingTeam = mock(ChoosingTeam.class);
        final CategorySimple album = new CategorySimple();
        album.setId(UUID.randomUUID());
        album.setName("Album 1");
        album.setImage("album1.png");
        album.setOrdinalNumber(1);
        final List<CategorySimple> albums = List.of(album);

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(game.getMaxAlbums()).thenReturn(6);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(null);
        when(categoryRepository.findByGameId(gameId)).thenReturn(albums);
        when(teamService.findNextChoosingTeam(gameId, 6)).thenReturn(choosingTeam);
        when(choosingTeam.getId()).thenReturn(choosingTeamId.toString());
        when(choosingTeam.getName()).thenReturn("Pickers");
        when(choosingTeam.getImage()).thenReturn("pickers.png");

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertSame(albums, payload.get("albums"));
        assertTrue(payload.containsKey("team"));
        assertFalse(payload.containsKey("selected"));
        final CreateTeamResponse team =
            assertInstanceOf(CreateTeamResponse.class, payload.get("team"));
        assertEquals(choosingTeamId, team.getId());
        assertEquals("Pickers", team.getName());
        assertEquals("pickers.png", team.getImage());
        assertForbiddenPlaybackKeys(payload);
      }

      @Test
      void albumsSelectedStateContainsSelectedAlbumButNoPlaybackFields() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final UUID gameId = UUID.randomUUID();
        final LastCategory selected = new LastCategory();
        selected.setCategoryId(UUID.randomUUID());
        selected.setStarted(false);
        selected.setOrdinalNumber(2);
        selected.setChosenCategoryPreview(new CategoryPreview("Rock", "rock.png"));

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(selected);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertTrue(payload.containsKey("selected"));
        assertSame(selected, payload.get("selected"));
        assertFalse(payload.containsKey("team"));
        assertFalse(payload.containsKey("albums"));
        assertForbiddenPlaybackKeys(payload);
      }

      @Test
      void albumsImpossibleEndBranchContainsNoPickerNoSelectedAndNoPlaybackFields()
          throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final UUID gameId = UUID.randomUUID();
        final LastCategory last = mock(LastCategory.class);

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(game.getMaxAlbums()).thenReturn(4);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(last);
        when(last.isStarted()).thenReturn(true);
        when(last.getOrdinalNumber()).thenReturn(4);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertFalse(payload.containsKey("albums"));
        assertFalse(payload.containsKey("team"));
        assertFalse(payload.containsKey("selected"));
        assertForbiddenPlaybackKeys(payload);
      }

      @Test
      void albumsStartedPreviousCategoryBranchCanExposeNullPickerButNoPlaybackFields()
          throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final UUID gameId = UUID.randomUUID();
        final LastCategory last = mock(LastCategory.class);
        final CategorySimple albumA = new CategorySimple();
        albumA.setId(UUID.randomUUID());
        albumA.setName("Album A");
        albumA.setImage("a.png");
        albumA.setOrdinalNumber(1);
        final CategorySimple albumB = new CategorySimple();
        albumB.setId(UUID.randomUUID());
        albumB.setName("Album B");
        albumB.setImage("b.png");
        albumB.setOrdinalNumber(2);
        final List<CategorySimple> albums = List.of(albumA, albumB);

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(game.getId()).thenReturn(gameId);
        when(game.getMaxAlbums()).thenReturn(5);
        when(categoryRepository.findLastCategory(gameId)).thenReturn(last);
        when(last.isStarted()).thenReturn(true);
        when(last.getOrdinalNumber()).thenReturn(3);
        when(categoryRepository.findByGameId(gameId)).thenReturn(albums);
        when(teamService.findNextChoosingTeam(gameId, 5)).thenReturn(null);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("albums", payload.get("stage"));
        assertSame(albums, payload.get("albums"));
        assertTrue(payload.containsKey("team"));
        assertNull(payload.get("team"));
        assertFalse(payload.containsKey("selected"));
        assertForbiddenPlaybackKeys(payload);
      }
    }

    @Nested
    class WinnerSchema {
      @Test
      void winnerStateContainsScoresAndNoAlbumOrPlaybackFields() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final List<CreateTeamResponse> scores = List.of(mock(CreateTeamResponse.class));

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(3);
        when(teamService.getTeamScores("ROOM")).thenReturn(scores);

        final HashMap<?, ?> payload = service.contextFetch("ROOM");

        assertEquals("welcome", payload.get("type"));
        assertEquals("winner", payload.get("stage"));
        assertSame(scores, payload.get("scores"));
        assertFalse(payload.containsKey("albums"));
        assertFalse(payload.containsKey("selected"));
        assertFalse(payload.containsKey("team"));
        assertFalse(payload.containsKey("teams"));
        assertForbiddenPlaybackKeys(payload);
      }
    }

    private static void assertForbiddenNonStage2LeakKeys(final HashMap<?, ?> payload) {
      assertFalse(payload.containsKey("albums"));
      assertFalse(payload.containsKey("selected"));
      assertFalse(payload.containsKey("team"));
      assertFalse(payload.containsKey("scores"));
      assertForbiddenPlaybackKeys(payload);
    }

    private static void assertForbiddenPlaybackKeys(final HashMap<?, ?> payload) {
      assertFalse(payload.containsKey("songId"));
      assertFalse(payload.containsKey("question"));
      assertFalse(payload.containsKey("answer"));
      assertFalse(payload.containsKey("scheduleId"));
      assertFalse(payload.containsKey("answerDuration"));
      assertFalse(payload.containsKey("seek"));
      assertFalse(payload.containsKey("remaining"));
      assertFalse(payload.containsKey("revealed"));
      assertFalse(payload.containsKey("bravo"));
      assertFalse(payload.containsKey("answeringTeam"));
      assertFalse(payload.containsKey("interruptId"));
      assertFalse(payload.containsKey("error"));
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameServiceImplNonStage2TransitionTest {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    private GameServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new GameServiceImpl();
      ReflectionTestUtils.setField(service, "teamService", teamService);
      ReflectionTestUtils.setField(service, "interruptService", interruptService);
      ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
      ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
    }

    @Nested
    class IsChangeStageLegal {
      @Test
      void rejectsUnknownRoom() {
        when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

        final InvalidReferencedObjectException ex =
            assertThrows(
                InvalidReferencedObjectException.class,
                () -> service.isChangeStageLegal(1, "MISS"));

        assertEquals("Game with code MISS does not exist", ex.getMessage());
      }

      @Test
      void rejectsStageBelowRange() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);

        final InvalidArgumentException ex =
            assertThrows(
                InvalidArgumentException.class, () -> service.isChangeStageLegal(-1, "ROOM"));

        assertEquals("Game state has to be a number between 0 and 3", ex.getMessage());
      }

      @Test
      void rejectsStageAboveRange() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);

        final InvalidArgumentException ex =
            assertThrows(
                InvalidArgumentException.class, () -> service.isChangeStageLegal(4, "ROOM"));

        assertEquals("Game state has to be a number between 0 and 3", ex.getMessage());
      }

      @Test
      void rejectsSameStage() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);

        final InvalidArgumentException ex =
            assertThrows(
                InvalidArgumentException.class, () -> service.isChangeStageLegal(1, "ROOM"));

        assertEquals("Game is already in that state", ex.getMessage());
      }

      @Test
      void rejectsLobbyToAnythingExceptAlbums() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);

        final WrongGameStateException ex =
            assertThrows(
                WrongGameStateException.class, () -> service.isChangeStageLegal(2, "ROOM"));

        assertEquals(
            "This game is in lobby state. The only allowed state transition is to album selection (stage 1)",
            ex.getMessage());
      }

      @Test
      void rejectsAlbumsToAnythingExceptSongs() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);

        final WrongGameStateException ex =
            assertThrows(
                WrongGameStateException.class, () -> service.isChangeStageLegal(3, "ROOM"));

        assertEquals(
            "Album selection is in progress. We can only move to song listening (stage 2)",
            ex.getMessage());
      }

      @Test
      void rejectsSongsToLobby() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(2);

        final WrongGameStateException ex =
            assertThrows(
                WrongGameStateException.class, () -> service.isChangeStageLegal(0, "ROOM"));

        assertEquals(
            "We're listening to a song. Stage has to be 1 (album selection) or 3 (finish)",
            ex.getMessage());
      }

      @Test
      void rejectsAnyOtherwiseLegalTransitionWhenTvIsMissing() {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(false);

        final AppNotRegisteredException ex =
            assertThrows(
                AppNotRegisteredException.class, () -> service.isChangeStageLegal(1, "ROOM"));

        assertEquals("TV app has to be connected to proceed", ex.getMessage());
      }

      @Test
      void allowsLobbyToAlbumsWhenBothAppsPresent() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(0);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

        assertSame(game, service.isChangeStageLegal(1, "ROOM"));
      }

      @Test
      void allowsAlbumsToSongsWhenBothAppsPresent() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(1);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

        assertSame(game, service.isChangeStageLegal(2, "ROOM"));
      }

      @Test
      void allowsWinnerToLobbyWhenBothAppsPresentBecauseNoExtraGuardExists() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(3);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

        assertSame(game, service.isChangeStageLegal(0, "ROOM"));
      }

      @Test
      void allowsWinnerToAlbumsWhenBothAppsPresentBecauseNoExtraGuardExists() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(game.getStage()).thenReturn(3);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

        assertSame(game, service.isChangeStageLegal(1, "ROOM"));
      }
    }

    @Nested
    class ChangeStageBroadcasting {
      @Test
      void changeStagePersistsNewStateAndBroadcastsFreshWelcomeSnapshot() throws Exception {
        final GameEntity game = mock(GameEntity.class);
        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("type", "welcome");
        payload.put("stage", "albums");

        final GameServiceImpl spy = spy(service);
        doReturn(game).when(spy).isChangeStageLegal(1, "ROOM");
        doReturn(payload).when(spy).contextFetch("ROOM");

        spy.changeStage(1, "ROOM");

        verify(game).setStage(1);
        verify(gameRepository).saveAndFlush(game);
        verify(broadcastGateway)
            .broadcast(eq("ROOM"), eq(new ObjectMapper().writeValueAsString(payload)));
      }

      @Test
      void changeStageDoesNotBroadcastWhenLegalityCheckThrows() throws Exception {
        final GameServiceImpl spy = spy(service);

        // switch to an actually failing spy branch
        final GameServiceImpl failingSpy = spy(service);
        doThrow(new AppNotRegisteredException("TV app has to be connected to proceed"))
            .when(failingSpy)
            .isChangeStageLegal(1, "ROOM");

        assertThrows(AppNotRegisteredException.class, () -> failingSpy.changeStage(1, "ROOM"));

        verifyNoInteractions(broadcastGateway);
      }
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameServiceStageTwoRecoveryIteration2Test {
    private static final String ROOM = "AKKU";

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final TeamService teamService = mock(TeamService.class);
    private final InterruptService interruptService = mock(InterruptService.class);
    private final BroadcastGateway broadcastGateway = mock(BroadcastGateway.class);
    private final PresenceGateway presenceGateway = mock(PresenceGateway.class);

    private GameServiceImpl gameService;
    private GameEntity game;
    private ScheduleEntity schedule;
    private UUID gameId;
    private UUID scheduleId;
    private UUID songId;
    private final Object scores = List.of("scoreboard");
    private final LocalDateTime startedAt = LocalDateTime.now().minusSeconds(12);

    @BeforeEach
    void setUp() throws DerivedException {
      gameService = new GameServiceImpl();
      ReflectionTestUtils.setField(gameService, "teamService", teamService);
      ReflectionTestUtils.setField(gameService, "interruptService", interruptService);
      ReflectionTestUtils.setField(gameService, "gameRepository", gameRepository);
      ReflectionTestUtils.setField(gameService, "categoryRepository", categoryRepository);
      ReflectionTestUtils.setField(gameService, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(gameService, "broadcastGateway", broadcastGateway);
      ReflectionTestUtils.setField(gameService, "presenceGateway", presenceGateway);

      gameId = UUID.randomUUID();
      scheduleId = UUID.randomUUID();
      songId = UUID.randomUUID();

      game = mock(GameEntity.class);
      when(game.getStage()).thenReturn(2);
      when(game.getId()).thenReturn(gameId);

      schedule = mock(ScheduleEntity.class, RETURNS_DEEP_STUBS);
      when(schedule.getId()).thenReturn(scheduleId);
      when(schedule.getStartedAt()).thenReturn(startedAt);
      when(schedule.getRevealedAt()).thenReturn(null);
      when(schedule.getTrackId().getSongId().getId()).thenReturn(songId);
      when(schedule.getTrackId().getAlbumId().getCustomQuestion()).thenReturn("Who sings this?");
      when(schedule.getTrackId().getCustomAnswer()).thenReturn("Artist - Track");
      lenient().when(schedule.getTrackId().getSongId().getSnippetDuration()).thenReturn(25.0);
      when(schedule.getTrackId().getSongId().getAnswerDuration()).thenReturn(8.0);

      when(gameRepository.findByCode(ROOM)).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
      when(teamService.getTeamScores(ROOM)).thenReturn(scores);
    }

    @Test
    void contextFetchStageTwoRevealedScenarioContainsRevealFieldsAndOmitsPlaybackFields()
        throws Exception {
      final UUID bravo = UUID.randomUUID();
      when(schedule.getRevealedAt()).thenReturn(LocalDateTime.now());
      when(interruptService.findCorrectAnswer(scheduleId, ROOM)).thenReturn(bravo);

      final HashMap<String, Object> json = gameService.contextFetch(ROOM);

      assertBaseSongFields(json);
      assertEquals("welcome", json.get("type"));
      assertEquals("songs", json.get("stage"));
      assertSame(scores, json.get("scores"));
      assertEquals(Boolean.TRUE, json.get("revealed"));
      assertEquals(bravo, json.get("bravo"));
      assertFalse(json.containsKey("seek"));
      assertFalse(json.containsKey("remaining"));
      assertFalse(json.containsKey("error"));
      assertFalse(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("interruptId"));
    }

    @Test
    void contextFetchStageTwoFinishedButNotRevealedScenarioOmitsRecoveryPlaybackFields()
        throws Exception {
      when(interruptService.calculateSeek(startedAt, scheduleId)).thenReturn(26000L);

      final HashMap<String, Object> json = gameService.contextFetch(ROOM);

      assertBaseSongFields(json);
      assertEquals(Boolean.FALSE, json.get("revealed"));
      assertFalse(json.containsKey("seek"));
      assertFalse(json.containsKey("remaining"));
      assertFalse(json.containsKey("error"));
      assertFalse(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("interruptId"));
      assertFalse(json.containsKey("bravo"));
    }

    @Test
    void contextFetchStageTwoTeamAnsweringScenarioContainsPauseRecoveryFieldsAndNoErrorFlag()
        throws Exception {
      final UUID teamId = UUID.randomUUID();
      final UUID interruptId = UUID.randomUUID();
      final TeamEntity team = mock(TeamEntity.class);
      final InterruptEntity teamInterrupt = mock(InterruptEntity.class);
      when(team.getId()).thenReturn(teamId);
      when(team.getName()).thenReturn("Sharks");
      when(team.getImage()).thenReturn("sharks.png");
      when(teamInterrupt.getTeamId()).thenReturn(team);
      when(teamInterrupt.getId()).thenReturn(interruptId);
      when(teamInterrupt.isCorrect()).thenReturn(null);
      when(interruptService.calculateSeek(startedAt, scheduleId)).thenReturn(4000L);
      when(interruptService.getLastTwoInterrupts(startedAt, scheduleId))
          .thenReturn(new InterruptEntity[] {teamInterrupt, null});

      final HashMap<String, Object> json = gameService.contextFetch(ROOM);

      assertBaseSongFields(json);
      assertEquals(4.0, ((Number) json.get("seek")).doubleValue());
      assertEquals(21.0, ((Number) json.get("remaining")).doubleValue());
      final CreateTeamResponse answeringTeam = (CreateTeamResponse) json.get("answeringTeam");
      assertEquals(teamId, answeringTeam.getId());
      assertEquals("Sharks", answeringTeam.getName());
      assertEquals("sharks.png", answeringTeam.getImage());
      assertEquals(interruptId, json.get("interruptId"));
      assertFalse(json.containsKey("error"));
      assertFalse(json.containsKey("revealed"));
      assertFalse(json.containsKey("bravo"));
    }

    @Test
    void contextFetchStageTwoSystemPauseScenarioContainsErrorFlagAndNoTeamAnswerPayload()
        throws Exception {
      final InterruptEntity systemPause = mock(InterruptEntity.class);
      when(systemPause.getResolvedAt()).thenReturn(null);
      when(interruptService.calculateSeek(startedAt, scheduleId)).thenReturn(3000L);
      when(interruptService.getLastTwoInterrupts(startedAt, scheduleId))
          .thenReturn(new InterruptEntity[] {null, systemPause});

      final HashMap<String, Object> json = gameService.contextFetch(ROOM);

      assertBaseSongFields(json);
      assertEquals(3.0, ((Number) json.get("seek")).doubleValue());
      assertEquals(22.0, ((Number) json.get("remaining")).doubleValue());
      assertEquals(Boolean.TRUE, json.get("error"));
      assertFalse(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("interruptId"));
      assertFalse(json.containsKey("revealed"));
      assertFalse(json.containsKey("bravo"));
    }

    @Test
    void contextFetchStageTwoNormalPlaybackScenarioContainsSeekAndRemainingOnly() throws Exception {
      when(interruptService.calculateSeek(startedAt, scheduleId)).thenReturn(2500L);
      when(interruptService.getLastTwoInterrupts(startedAt, scheduleId))
          .thenReturn(new InterruptEntity[] {null, null});

      final HashMap<String, Object> json = gameService.contextFetch(ROOM);

      assertBaseSongFields(json);
      assertEquals(2.5, ((Number) json.get("seek")).doubleValue());
      assertEquals(22.5, ((Number) json.get("remaining")).doubleValue());
      assertFalse(json.containsKey("error"));
      assertFalse(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("interruptId"));
      assertFalse(json.containsKey("revealed"));
      assertFalse(json.containsKey("bravo"));
    }

    @Test
    void contextFetchStageTwoBoundaryAtZeroRemainingStillReturnsPlaybackRecoveryFields()
        throws Exception {
      when(schedule.getTrackId().getSongId().getSnippetDuration()).thenReturn(4.0);
      when(interruptService.calculateSeek(startedAt, scheduleId)).thenReturn(4000L);
      when(interruptService.getLastTwoInterrupts(startedAt, scheduleId))
          .thenReturn(new InterruptEntity[] {null, null});

      final HashMap<String, Object> json = gameService.contextFetch(ROOM);

      assertBaseSongFields(json);
      assertEquals(4.0, ((Number) json.get("seek")).doubleValue());
      assertEquals(0.0, ((Number) json.get("remaining")).doubleValue());
      assertFalse(json.containsKey("revealed"));
      assertFalse(json.containsKey("error"));
    }

    private void assertBaseSongFields(final Map<String, Object> json) {
      assertEquals(songId, json.get("songId"));
      assertEquals("Who sings this?", json.get("question"));
      assertEquals("Artist - Track", json.get("answer"));
      assertEquals(scheduleId, json.get("scheduleId"));
      assertEquals(8.0, ((Number) json.get("answerDuration")).doubleValue());
      assertSame(scores, json.get("scores"));
      assertNull(json.get("team"));
    }
  }
}
