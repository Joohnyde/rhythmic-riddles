package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateGameRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.CategorySimple;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

public class GameServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CoreGameFlow {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private GameServiceImpl gameService;

    @Test
    void createGameRejectsNegativeAlbumCount() {
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> gameService.createGame(new CreateGameRequest(10, -1)));

      assertEquals("Number of albums must be a positive integer", exception.getMessage());
      verify(gameRepository, never()).saveAndFlush(any());
    }

    @Test
    void createGameRejectsNegativeSongCount() {
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> gameService.createGame(new CreateGameRequest(-1, 4)));

      assertEquals("Number of songs must be a positive integer", exception.getMessage());
      verify(gameRepository, never()).saveAndFlush(any());
    }

    @Test
    void createGameRegeneratesRoomCodeUntilUniqueAndPersistsGame() throws Exception {
      when(gameRepository.findByCode(anyString()))
          .thenReturn(Optional.of(new GameEntity(UUID.randomUUID())))
          .thenReturn(Optional.empty());

      final String roomCode = gameService.createGame(new CreateGameRequest(12, 4));
      final ArgumentCaptor<GameEntity> savedGame = ArgumentCaptor.forClass(GameEntity.class);

      verify(gameRepository).saveAndFlush(savedGame.capture());
      verify(gameRepository, times(2)).findByCode(anyString());
      assertEquals(savedGame.getValue().getCode(), roomCode);
      assertNotNull(roomCode);
      assertEquals(4, roomCode.length());
      assertTrue(roomCode.chars().allMatch(Character::isUpperCase));
      assertEquals(12, savedGame.getValue().getMaxSongs());
      assertEquals(4, savedGame.getValue().getMaxAlbums());
    }

    @Test
    void putDefaultFieldsUsesCustomQuestionAndAnswerWhenPresent() {
      final ScheduleEntity schedule = songSchedule(30.0, 11.0);
      schedule.getTrackId().getAlbumId().setCustomQuestion("Who sings this?");
      schedule.getTrackId().setCustomAnswer("Custom answer");
      final Map<String, Object> json = new HashMap<>();

      GameServiceImpl.putDefaultFields(schedule, (HashMap<String, Object>) json);

      assertEquals(schedule.getTrackId().getSongId().getId(), json.get("songId"));
      assertEquals("Who sings this?", json.get("question"));
      assertEquals("Custom answer", json.get("answer"));
      assertEquals(schedule.getId(), json.get("scheduleId"));
      assertEquals(11.0, json.get("answerDuration"));
    }

    @Test
    void putDefaultFieldsFallsBackToDefaultQuestionAndSongToString() {
      final ScheduleEntity schedule = songSchedule(30.0, 9.0);
      final SongEntity song = schedule.getTrackId().getSongId();
      song.setAuthors("Artist");
      song.setName("Track");
      final HashMap<String, Object> json = new HashMap<>();

      GameServiceImpl.putDefaultFields(schedule, json);

      assertEquals("Prepoznaj ovu pjesmu!", json.get("question"));
      assertEquals("Artist - Track", json.get("answer"));
    }

    @Test
    void contextFetchReturnsEmptyPayloadWhenRoomDoesNotExist() throws Exception {
      when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

      final HashMap<String, Object> json = gameService.contextFetch("MISS");

      assertTrue(json.isEmpty());
    }

    @Test
    void contextFetchStageZeroReturnsLobbyTeams() throws Exception {
      final GameEntity game = gameWithStage(0);
      final List<CreateTeamResponse> teams =
          new ArrayList<>(List.of(new CreateTeamResponse(UUID.randomUUID(), "Red", "r.png")));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.findByRoomCode("AKKU")).thenReturn(teams);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("welcome", json.get("type"));
      assertEquals("lobby", json.get("stage"));
      assertSame(teams, json.get("teams"));
    }

    @Test
    void contextFetchStageOneReturnsAlbumsAndChoosingTeamWhenNothingChosenYet() throws Exception {
      final GameEntity game = gameWithStage(1);
      final UUID gameId = game.getId();
      final List<CategorySimple> albums =
          new ArrayList<>(List.of(categorySimple(UUID.randomUUID(), "Album A")));
      final ChoosingTeam choosingTeam = choosingTeam(UUID.randomUUID(), "Blue", "b.png");
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(gameId)).thenReturn(null);
      when(categoryRepository.findByGameId(gameId)).thenReturn(albums);
      when(teamService.findNextChoosingTeam(gameId, game.getMaxAlbums())).thenReturn(choosingTeam);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("albums", json.get("stage"));
      assertSame(albums, json.get("albums"));
      final CreateTeamResponse team = (CreateTeamResponse) json.get("team");
      assertEquals(UUID.fromString(choosingTeam.getId()), team.getId());
      assertEquals("Blue", team.getName());
    }

    @Test
    void contextFetchStageOneReturnsAlbumsAndNullTeamWhenChooserIsNull() throws Exception {
      final GameEntity game = gameWithStage(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(null);
      when(categoryRepository.findByGameId(game.getId())).thenReturn(new ArrayList<>());
      when(teamService.findNextChoosingTeam(game.getId(), game.getMaxAlbums())).thenReturn(null);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("albums", json.get("stage"));
      assertNull(json.get("team"));
    }

    @Test
    void contextFetchStageOneReturnsSelectedAlbumWhenChoiceWasMadeButNotStarted() throws Exception {
      final GameEntity game = gameWithStage(1);
      final LastCategory selected = new LastCategory();
      selected.setStarted(false);
      selected.setOrdinalNumber(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(selected);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("albums", json.get("stage"));
      assertSame(selected, json.get("selected"));
    }

    @Test
    void contextFetchStageTwoReturnsRevealedPayloadWhenSongWasRevealed() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = songSchedule(30.0, 8.0);
      schedule.setRevealedAt(LocalDateTime.now());
      final Object scores = new ArrayList<>(List.of("scores"));
      final UUID bravo = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);
      when(interruptService.findCorrectAnswer(schedule.getId(), "AKKU")).thenReturn(bravo);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("songs", json.get("stage"));
      assertEquals(Boolean.TRUE, json.get("revealed"));
      assertEquals(bravo, json.get("bravo"));
      assertSame(scores, json.get("scores"));
    }

    @Test
    void contextFetchStageTwoReturnsFinishedSongWhenSeekPassedSnippetEnd() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = songSchedule(5.0, 8.0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(6000L);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(Boolean.FALSE, json.get("revealed"));
      assertFalse(json.containsKey("seek"));
      assertFalse(json.containsKey("remaining"));
    }

    @Test
    void contextFetchStageTwoReturnsTeamAnsweringState() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = songSchedule(25.0, 8.0);
      final TeamEntity team = teamEntity(game, "Sharks", "s.png");
      final InterruptEntity teamInterrupt = new InterruptEntity(UUID.randomUUID());
      teamInterrupt.setTeamId(team);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(4000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {teamInterrupt, null});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(4.0, json.get("seek"));
      assertEquals(21.0, json.get("remaining"));
      final CreateTeamResponse answeringTeam = (CreateTeamResponse) json.get("answeringTeam");
      assertEquals(team.getId(), answeringTeam.getId());
      assertEquals(teamInterrupt.getId(), json.get("interruptId"));
    }

    @Test
    void contextFetchStageTwoReturnsSystemErrorState() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = songSchedule(25.0, 8.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(3000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {null, pause});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(Boolean.TRUE, json.get("error"));
    }

    @Test
    void contextFetchStageTwoReturnsActivePlaybackStateWhenNothingInterruptsSong()
        throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = songSchedule(25.0, 8.0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(2500L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {null, null});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(2.5, json.get("seek"));
      assertEquals(22.5, json.get("remaining"));
      assertFalse(json.containsKey("error"));
      assertFalse(json.containsKey("answeringTeam"));
    }

    @Test
    void contextFetchStageThreeReturnsWinnerScoreboard() throws Exception {
      final GameEntity game = gameWithStage(3);
      final Object scores = new ArrayList<>(List.of("scoreboard"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("winner", json.get("stage"));
      assertSame(scores, json.get("scores"));
    }

    @Test
    void contextFetchRejectsUnknownInternalStage() {
      final GameEntity game = gameWithStage(7);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(WrongGameStateException.class, () -> gameService.contextFetch("AKKU"));

      assertEquals("Game state has to be between 0 and 3", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsUnknownRoomCode() {
      when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> gameService.isChangeStageLegal(1, "MISS"));

      assertEquals("Game with code MISS does not exist", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsInvalidStageId() {
      final GameEntity game = gameWithStage(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class, () -> gameService.isChangeStageLegal(5, "AKKU"));

      assertEquals("Game state has to be a number between 0 and 3", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsTransitionToSameStage() {
      final GameEntity game = gameWithStage(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class, () -> gameService.isChangeStageLegal(1, "AKKU"));

      assertEquals("Game is already in that state", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsLobbyToNonAlbumTransition() {
      final GameEntity game = gameWithStage(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(2, "AKKU"));

      assertEquals(
          "This game is in lobby state. The only allowed state transition is to album selection (stage 1)",
          exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsIllegalStageTransitions() {
      final GameEntity game = gameWithStage(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(3, "AKKU"));

      assertEquals(
          "Album selection is in progress. We can only move to song listening (stage 2)",
          exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsZeroAsInvalidTargetStage() {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(0, "AKKU"));

      assertEquals(
          "We're listening to a song. Stage has to be 1 (album selection) or 3 (finish)",
          exception.getMessage());
    }

    @Test
    void isChangeStageLegalRequiresBothAppsToBePresent() {
      final GameEntity game = gameWithStage(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class, () -> gameService.isChangeStageLegal(1, "AKKU"));

      assertEquals("TV app has to be connected to proceed", exception.getMessage());
    }

    @Test
    void isChangeStageLegalReturnsGameForAllowedTransition() throws Exception {
      final GameEntity game = gameWithStage(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      final GameEntity result = gameService.isChangeStageLegal(1, "AKKU");

      assertEquals(game, result);
    }

    @Test
    void changeStagePersistsStateAndBroadcastsRefreshedContext() throws Exception {
      final GameEntity game = gameWithStage(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      gameService.changeStage(1, "AKKU");

      assertEquals(1, game.getStage());
      verify(gameRepository).saveAndFlush(game);
      verify(broadcastGateway).broadcast(anyString(), anyString());
    }

    @Test
    void getStageReturnsMinusOneForMissingRoom() {
      when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

      assertEquals(-1, gameService.getStage("MISS"));
    }

    @Test
    void getStageReturnsPersistedStage() {
      final GameEntity game = gameWithStage(3);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      assertEquals(3, gameService.getStage("AKKU"));
    }

    @Test
    void findByCodeDelegatesToRepositoryStageAwareLookup() throws Exception {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);

      final GameEntity result = gameService.findByCode("AKKU", 2);

      assertSame(game, result);
    }

    private GameEntity gameWithStage(final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(stage);
      game.setMaxAlbums(4);
      game.setMaxSongs(3);
      return game;
    }

    private ScheduleEntity songSchedule(final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(10));
      return schedule;
    }

    private CategorySimple categorySimple(final UUID id, final String name) {
      final CategorySimple simple = new CategorySimple();
      simple.setId(id);
      simple.setName(name);
      simple.setImage(id + ".png");
      return simple;
    }

    private ChoosingTeam choosingTeam(final UUID id, final String name, final String image) {
      return new ChoosingTeam() {
        @Override
        public String getId() {
          return id.toString();
        }

        @Override
        public String getName() {
          return name;
        }

        @Override
        public String getImage() {
          return image;
        }
      };
    }

    private TeamEntity teamEntity(final GameEntity game, final String name, final String image) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(image);
      return team;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ContextFetchVariants {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private GameServiceImpl gameService;

    @Test
    void contextFetchStageOneReturnsAlbumSelectionAgainWhenPreviousCategoryAlreadyStarted()
        throws Exception {
      final GameEntity game = gameWithStage(1);
      final LastCategory last = new LastCategory();
      last.setStarted(true);
      last.setOrdinalNumber(1);
      final List<CategorySimple> albums =
          new ArrayList<>(List.of(categorySimple(UUID.randomUUID(), "Retro")));
      final ChoosingTeam chooser = choosingTeam(UUID.randomUUID(), "Blue", "blue.png");
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(last);
      when(categoryRepository.findByGameId(game.getId())).thenReturn(albums);
      when(teamService.findNextChoosingTeam(game.getId(), game.getMaxAlbums())).thenReturn(chooser);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("welcome", json.get("type"));
      assertEquals("albums", json.get("stage"));
      assertSame(albums, json.get("albums"));
      assertTrue(json.get("team") instanceof CreateTeamResponse);
      assertNull(json.get("selected"));
    }

    @Test
    void contextFetchStageOneStopsAddingAlbumSelectionDataWhenAllAlbumsWereAlreadyPlayed()
        throws Exception {
      final GameEntity game = gameWithStage(1);
      final LastCategory last = new LastCategory();
      last.setStarted(true);
      last.setOrdinalNumber(game.getMaxAlbums());
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(last);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("welcome", json.get("type"));
      assertEquals("albums", json.get("stage"));
      assertFalse(json.containsKey("albums"));
      assertFalse(json.containsKey("team"));
      assertFalse(json.containsKey("selected"));
    }

    @Test
    void contextFetchStageTwoKeepsPlayingStateWhenPreviousInterruptsAreResolved() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = songSchedule(30.0, 6.0);
      final InterruptEntity teamInterrupt = new InterruptEntity(UUID.randomUUID());
      final TeamEntity answeringTeam = team(game, "Green");
      teamInterrupt.setTeamId(answeringTeam);
      teamInterrupt.setCorrect(true);
      teamInterrupt.setResolvedAt(LocalDateTime.now().minusSeconds(1));
      final InterruptEntity systemInterrupt = new InterruptEntity(UUID.randomUUID());
      systemInterrupt.setResolvedAt(LocalDateTime.now().minusSeconds(1));
      final Object scores = new ArrayList<>(List.of("score"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(8_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {teamInterrupt, systemInterrupt});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("songs", json.get("stage"));
      assertEquals(schedule.getId(), json.get("scheduleId"));
      assertEquals(8.0, json.get("seek"));
      assertEquals(22.0, json.get("remaining"));
      assertSame(scores, json.get("scores"));
      assertFalse(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("interruptId"));
      assertFalse(json.containsKey("error"));
    }

    @Test
    void contextFetchStageThreeReturnsWinnerPayloadWithScores() throws Exception {
      final GameEntity game = gameWithStage(3);
      final Object scores = new ArrayList<>(List.of("A", "B"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("welcome", json.get("type"));
      assertEquals("winner", json.get("stage"));
      assertSame(scores, json.get("scores"));
      verify(teamService).getTeamScores("AKKU");
    }

    @Test
    void contextFetchRejectsInvalidStoredStage() {
      final GameEntity game = gameWithStage(99);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(WrongGameStateException.class, () -> gameService.contextFetch("AKKU"));

      assertEquals("Game state has to be between 0 and 3", exception.getMessage());
    }

    @Test
    void getStageReturnsMinusOneWhenGameIsMissing() {
      when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

      assertEquals(-1, gameService.getStage("MISS"));
    }

    @Test
    void getStageReturnsPersistedStageWhenGameExists() {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      assertEquals(2, gameService.getStage("AKKU"));
    }

    private GameEntity gameWithStage(int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(stage);
      game.setMaxAlbums(3);
      game.setMaxSongs(5);
      game.setDate(LocalDateTime.now());
      return game;
    }

    private ScheduleEntity songSchedule(double snippetDuration, double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setAuthors("Artist");
      song.setName("Name");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);

      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");

      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setSongId(song);
      track.setAlbumId(album);

      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(5));
      return schedule;
    }

    private TeamEntity team(GameEntity game, String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name.toLowerCase() + ".png");
      return team;
    }

    private CategorySimple categorySimple(UUID id, String name) {
      final CategorySimple category = new CategorySimple();
      category.setId(id);
      category.setName(name);
      category.setImage(id + ".png");
      return category;
    }

    private ChoosingTeam choosingTeam(UUID id, String name, String image) {
      return new ChoosingTeam() {
        @Override
        public String getId() {
          return id.toString();
        }

        @Override
        public String getName() {
          return name;
        }

        @Override
        public String getImage() {
          return image;
        }
      };
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ContextFetchCorruptionAndCoverage {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private GameServiceImpl gameService;

    @Test
    void contextFetchStageZeroReturnsEmptyTeamListWhenNoTeamsExist() throws Exception {
      final GameEntity game = gameWithStage(0);
      final List<CreateTeamResponse> teams = new ArrayList<>();
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.findByRoomCode("AKKU")).thenReturn(teams);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("welcome", json.get("type"));
      assertEquals("lobby", json.get("stage"));
      assertSame(teams, json.get("teams"));
      verify(categoryRepository, never()).findLastCategory(game.getId());
    }

    @Test
    void contextFetchStageOneReturnsSelectionPayloadWhenPreviousStartedButAlbumsRemain()
        throws Exception {
      final GameEntity game = gameWithStage(1);
      game.setMaxAlbums(4);
      final LastCategory lastCategory = new LastCategory();
      lastCategory.setStarted(true);
      lastCategory.setOrdinalNumber(2);
      final List<CategorySimple> albums =
          new ArrayList<>(List.of(categorySimple(UUID.randomUUID(), "Synthwave")));
      final ChoosingTeam chooser = choosingTeam(UUID.randomUUID(), "Blue", "blue.png");

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(lastCategory);
      when(categoryRepository.findByGameId(game.getId())).thenReturn(albums);
      when(teamService.findNextChoosingTeam(game.getId(), game.getMaxAlbums())).thenReturn(chooser);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("albums", json.get("stage"));
      assertSame(albums, json.get("albums"));
      assertFalse(json.containsKey("selected"));
      final CreateTeamResponse team = (CreateTeamResponse) json.get("team");
      assertEquals(UUID.fromString(chooser.getId()), team.getId());
      assertEquals("Blue", team.getName());
    }

    @Test
    void contextFetchStageOneFinalStartedCategoryKeepsAlbumsStageWithoutChooserOrSelection()
        throws Exception {
      final GameEntity game = gameWithStage(1);
      game.setMaxAlbums(3);
      final LastCategory lastCategory = new LastCategory();
      lastCategory.setStarted(true);
      lastCategory.setOrdinalNumber(3);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(lastCategory);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("albums", json.get("stage"));
      assertFalse(json.containsKey("albums"));
      assertFalse(json.containsKey("team"));
      assertFalse(json.containsKey("selected"));
      verify(teamService, never()).findNextChoosingTeam(game.getId(), game.getMaxAlbums());
    }

    @Test
    void contextFetchStageTwoWithMissingLastPlayedSongCurrentlyFailsFastOnInconsistentState()
        throws Exception {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      assertThrows(NullPointerException.class, () -> gameService.contextFetch("AKKU"));
    }

    @Test
    void contextFetchStageTwoReturnsPlayingPayloadWhenNoInterruptsAndSongStillRunning()
        throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(30.0, 7.0);
      final Object scores = new ArrayList<TeamScoreProjection>();
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(5_500L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {null, null});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("songs", json.get("stage"));
      assertEquals(schedule.getTrackId().getSongId().getId(), json.get("songId"));
      assertEquals(5.5, (Double) json.get("seek"), 0.0001);
      assertEquals(24.5, (Double) json.get("remaining"), 0.0001);
      assertSame(scores, json.get("scores"));
      assertFalse(json.containsKey("revealed"));
      assertFalse(json.containsKey("error"));
      assertFalse(json.containsKey("answeringTeam"));
      verify(interruptService, never()).findCorrectAnswer(schedule.getId(), "AKKU");
    }

    @Test
    void contextFetchStageTwoReturnsAnsweringPayloadWhenTeamInterruptIsPending() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(30.0, 8.0);
      final TeamEntity team = team(game, "Red", "red.png");
      final InterruptEntity pendingAnswer = new InterruptEntity(UUID.randomUUID());
      pendingAnswer.setTeamId(team);
      pendingAnswer.setScheduleId(schedule);
      pendingAnswer.setCorrect(null);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(3_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {pendingAnswer, null});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("songs", json.get("stage"));
      assertEquals(3.0, (Double) json.get("seek"), 0.0001);
      assertEquals(27.0, (Double) json.get("remaining"), 0.0001);
      assertEquals(pendingAnswer.getId(), json.get("interruptId"));
      final CreateTeamResponse answeringTeam = (CreateTeamResponse) json.get("answeringTeam");
      assertEquals(team.getId(), answeringTeam.getId());
      assertEquals("Red", answeringTeam.getName());
      assertFalse(json.containsKey("error"));
    }

    @Test
    void contextFetchStageTwoPrefersPendingTeamAnswerOverUnresolvedSystemPause() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(30.0, 8.0);
      final TeamEntity team = team(game, "Gold", "gold.png");
      final InterruptEntity pendingAnswer = new InterruptEntity(UUID.randomUUID());
      pendingAnswer.setTeamId(team);
      pendingAnswer.setScheduleId(schedule);
      pendingAnswer.setCorrect(null);
      final InterruptEntity unresolvedSystem = new InterruptEntity(UUID.randomUUID());
      unresolvedSystem.setScheduleId(schedule);
      unresolvedSystem.setResolvedAt(null);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(2_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {pendingAnswer, unresolvedSystem});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertTrue(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("error"));
    }

    @Test
    void contextFetchStageTwoReturnsErrorPayloadWhenSystemInterruptIsUnresolved() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(30.0, 9.0);
      final InterruptEntity resolvedTeamAnswer = new InterruptEntity(UUID.randomUUID());
      resolvedTeamAnswer.setCorrect(Boolean.TRUE);
      resolvedTeamAnswer.setResolvedAt(LocalDateTime.now().minusSeconds(2));
      final InterruptEntity unresolvedSystem = new InterruptEntity(UUID.randomUUID());
      unresolvedSystem.setResolvedAt(null);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(6_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {resolvedTeamAnswer, unresolvedSystem});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(Boolean.TRUE, json.get("error"));
      assertFalse(json.containsKey("answeringTeam"));
      assertFalse(json.containsKey("revealed"));
    }

    @Test
    void contextFetchStageTwoIgnoresResolvedSystemInterruptAndKeepsPlaybackState()
        throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(25.0, 6.0);
      final InterruptEntity resolvedSystem = new InterruptEntity(UUID.randomUUID());
      resolvedSystem.setResolvedAt(LocalDateTime.now().minusSeconds(1));

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(4_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {null, resolvedSystem});

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(4.0, (Double) json.get("seek"), 0.0001);
      assertEquals(21.0, (Double) json.get("remaining"), 0.0001);
      assertFalse(json.containsKey("error"));
    }

    @Test
    void contextFetchStageTwoReturnsFinishedPayloadWhenSongAlreadyRanOut() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(10.0, 5.0);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(11_200L);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(Boolean.FALSE, json.get("revealed"));
      assertFalse(json.containsKey("seek"));
      assertFalse(json.containsKey("remaining"));
      verify(interruptService, never())
          .getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId());
    }

    @Test
    void contextFetchStageTwoReturnsRevealedPayloadAndCorrectTeam() throws Exception {
      final GameEntity game = gameWithStage(2);
      final ScheduleEntity schedule = schedule(20.0, 5.0);
      schedule.setRevealedAt(LocalDateTime.now().minusSeconds(1));
      final UUID winnerId = UUID.randomUUID();

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.findCorrectAnswer(schedule.getId(), "AKKU")).thenReturn(winnerId);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals(Boolean.TRUE, json.get("revealed"));
      assertEquals(winnerId, json.get("bravo"));
      verify(interruptService, never()).calculateSeek(schedule.getStartedAt(), schedule.getId());
    }

    @Test
    void contextFetchStageThreeReturnsWinnerScoresEvenWhenEmpty() throws Exception {
      final GameEntity game = gameWithStage(3);
      final List<Object> scores = new ArrayList<>();
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);

      final HashMap<String, Object> json = gameService.contextFetch("AKKU");

      assertEquals("winner", json.get("stage"));
      assertSame(scores, json.get("scores"));
    }

    @Test
    void contextFetchRejectsUnknownStage() {
      final GameEntity game = gameWithStage(99);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(WrongGameStateException.class, () -> gameService.contextFetch("AKKU"));

      assertEquals("Game state has to be between 0 and 3", exception.getMessage());
    }

    private GameEntity gameWithStage(final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(stage);
      game.setMaxAlbums(3);
      game.setMaxSongs(4);
      return game;
    }

    private ScheduleEntity schedule(final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);
      song.setAuthors("Author");
      song.setName("SongName");

      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(4));
      return schedule;
    }

    private TeamEntity team(final GameEntity game, final String name, final String image) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(image);
      return team;
    }

    private CategorySimple categorySimple(final UUID id, final String name) {
      final CategorySimple simple = new CategorySimple();
      simple.setId(id);
      simple.setName(name);
      simple.setImage(id + ".png");
      return simple;
    }

    private ChoosingTeam choosingTeam(final UUID id, final String name, final String image) {
      return new ChoosingTeam() {
        @Override
        public String getId() {
          return id.toString();
        }

        @Override
        public String getName() {
          return name;
        }

        @Override
        public String getImage() {
          return image;
        }
      };
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameRulesAndValidation {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private GameServiceImpl gameService;

    @Test
    void isChangeStageLegalRejectsNegativeStage() {
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(gameWithStage(0)));
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class, () -> gameService.isChangeStageLegal(-1, "AKKU"));

      assertEquals("Game state has to be a number between 0 and 3", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsTransitionFromLobbyToSongs() {
      final GameEntity game = gameWithStage(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(2, "AKKU"));

      assertTrueMessage(
          exception.getMessage(), "only allowed state transition is to album selection");
    }

    @Test
    void isChangeStageLegalRejectsTransitionFromAlbumsToLobby() {
      final GameEntity game = gameWithStage(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(0, "AKKU"));

      assertTrueMessage(exception.getMessage(), "only move to song listening");
    }

    @Test
    void isChangeStageLegalRejectsTransitionFromSongsToLobby() {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(0, "AKKU"));

      assertTrueMessage(
          exception.getMessage(), "Stage has to be 1 (album selection) or 3 (finish)");
    }

    @Test
    void isChangeStageLegalRequiresTvPresenceEvenForOtherwiseValidTransition() {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class, () -> gameService.isChangeStageLegal(3, "AKKU"));

      assertEquals("TV app has to be connected to proceed", exception.getMessage());
    }

    @Test
    void isChangeStageLegalReturnsGameWhenTransitionIsValid() throws Exception {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      final GameEntity result = gameService.isChangeStageLegal(3, "AKKU");

      assertSame(game, result);
    }

    @Test
    void findByCodeDelegatesToRepositoryDefaultValidation() throws Exception {
      final GameEntity game = gameWithStage(1);
      when(gameRepository.findByCode(eq("AKKU"), anyInt())).thenReturn(game);
      final GameEntity result = gameService.findByCode("AKKU", 1);

      assertSame(game, result);
    }

    @Test
    void changeStageDoesNotPersistWhenValidationFails() throws Exception {
      final GameEntity game = gameWithStage(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      assertThrows(AppNotRegisteredException.class, () -> gameService.changeStage(3, "AKKU"));

      verify(gameRepository, never()).saveAndFlush(game);
      verify(broadcastGateway, never())
          .broadcast(
              org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private GameEntity gameWithStage(int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(stage);
      game.setDate(LocalDateTime.now());
      return game;
    }

    private void assertTrueMessage(String message, String expectedPart) {
      if (!message.contains(expectedPart)) {
        throw new AssertionError("Expected <" + message + "> to contain <" + expectedPart + ">");
      }
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class StageTransitionMatrix {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @Spy @InjectMocks private GameServiceImpl gameService;

    @Test
    void isChangeStageLegalRejectsMissingGame() {
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> gameService.isChangeStageLegal(1, "AKKU"));

      assertEquals("Game with code AKKU does not exist", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsStageIdsOutsideSupportedRange() {
      final GameEntity game = game(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final InvalidArgumentException low =
          assertThrows(
              InvalidArgumentException.class, () -> gameService.isChangeStageLegal(-1, "AKKU"));
      final InvalidArgumentException high =
          assertThrows(
              InvalidArgumentException.class, () -> gameService.isChangeStageLegal(4, "AKKU"));

      assertEquals("Game state has to be a number between 0 and 3", low.getMessage());
      assertEquals("Game state has to be a number between 0 and 3", high.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsRepeatedStateTransitions() {
      final GameEntity game = game(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class, () -> gameService.isChangeStageLegal(2, "AKKU"));

      assertEquals("Game is already in that state", exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsLobbyToSongJump() {
      final GameEntity game = game(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(2, "AKKU"));

      assertEquals(
          "This game is in lobby state. The only allowed state transition is to album selection (stage 1)",
          exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsAlbumSelectionToWinnerJump() {
      final GameEntity game = game(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(3, "AKKU"));

      assertEquals(
          "Album selection is in progress. We can only move to song listening (stage 2)",
          exception.getMessage());
    }

    @Test
    void isChangeStageLegalRejectsSongStateBackToLobby() {
      final GameEntity game = game(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class, () -> gameService.isChangeStageLegal(0, "AKKU"));

      assertEquals(
          "We're listening to a song. Stage has to be 1 (album selection) or 3 (finish)",
          exception.getMessage());
    }

    @Test
    void isChangeStageLegalAllowsSongBackToAlbumWhenTvPresent() throws Exception {
      final GameEntity game = game(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      final GameEntity result = gameService.isChangeStageLegal(1, "AKKU");

      assertSame(game, result);
    }

    @Test
    void isChangeStageLegalAllowsSongToWinnerWhenTvPresent() throws Exception {
      final GameEntity game = game(2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      final GameEntity result = gameService.isChangeStageLegal(3, "AKKU");

      assertSame(game, result);
    }

    @Test
    void isChangeStageLegalRequiresTvPresenceForOtherwiseValidTransition() {
      final GameEntity game = game(1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class, () -> gameService.isChangeStageLegal(2, "AKKU"));

      assertEquals("TV app has to be connected to proceed", exception.getMessage());
    }

    @Test
    void changeStagePersistsRequestedStageAndBroadcastsFreshContext() throws Exception {
      final GameEntity game = game(1);
      final com.cevapinxile.cestereg.persistence.entity.ScheduleEntity schedule =
          new com.cevapinxile.cestereg.persistence.entity.ScheduleEntity(
              java.util.UUID.randomUUID());
      final com.cevapinxile.cestereg.persistence.entity.SongEntity song =
          new com.cevapinxile.cestereg.persistence.entity.SongEntity(java.util.UUID.randomUUID());
      song.setSnippetDuration(20.0);
      song.setAnswerDuration(6.0);
      final com.cevapinxile.cestereg.persistence.entity.AlbumEntity album =
          new com.cevapinxile.cestereg.persistence.entity.AlbumEntity(
              java.util.UUID.randomUUID(), "Album");
      final com.cevapinxile.cestereg.persistence.entity.TrackEntity track =
          new com.cevapinxile.cestereg.persistence.entity.TrackEntity(java.util.UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      schedule.setTrackId(track);
      schedule.setStartedAt(java.time.LocalDateTime.now().minusSeconds(2));

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new java.util.ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(1_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(
              new com.cevapinxile.cestereg.persistence.entity.InterruptEntity[] {null, null});

      gameService.changeStage(2, "AKKU");

      assertEquals(2, game.getStage());
      verify(gameRepository).saveAndFlush(game);
      verify(broadcastGateway)
          .broadcast(
              org.mockito.Mockito.eq("AKKU"),
              org.mockito.ArgumentMatchers.contains("\"stage\":\"songs\""));
    }

    @Test
    void changeStageDoesNotPersistOrBroadcastWhenTransitionIsIllegal() {
      final GameEntity game = game(0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      assertThrows(WrongGameStateException.class, () -> gameService.changeStage(3, "AKKU"));

      verify(gameRepository, never()).saveAndFlush(game);
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    private GameEntity game(final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(stage);
      game.setMaxAlbums(4);
      game.setMaxSongs(5);
      return game;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameSupportAndLookupMethods {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private GameServiceImpl gameService;

    @Test
    void createGameRejectsNegativeAlbumCount() {
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> gameService.createGame(new CreateGameRequest(5, -1)));

      assertEquals("Number of albums must be a positive integer", exception.getMessage());
    }

    @Test
    void createGameRejectsNegativeSongCount() {
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> gameService.createGame(new CreateGameRequest(-1, 5)));

      assertEquals("Number of songs must be a positive integer", exception.getMessage());
    }

    @Test
    void putDefaultFieldsFallsBackToDefaultsWhenQuestionAndAnswerMissing() {
      final ScheduleEntity schedule = schedule(game("AKKU", 2), 12.0, 8.0);
      schedule.getTrackId().getAlbumId().setCustomQuestion(null);
      schedule.getTrackId().setCustomAnswer(null);
      final HashMap<String, Object> payload = new HashMap<>();

      GameServiceImpl.putDefaultFields(schedule, payload);

      assertEquals("Prepoznaj ovu pjesmu!", payload.get("question"));
      assertEquals(schedule.getTrackId().getSongId().toString(), payload.get("answer"));
      assertEquals(schedule.getTrackId().getSongId().getId(), payload.get("songId"));
      assertEquals(schedule.getId(), payload.get("scheduleId"));
      assertEquals(8.0, payload.get("answerDuration"));
    }

    @Test
    void contextFetchStageOneReturnsSelectedCategoryWhenAlbumWasPickedButNotStarted()
        throws Exception {
      final GameEntity game = game("AKKU", 1);
      final CategoryEntity category = category(game);
      category.setOrdinalNumber(2);
      category.setDone(false);
      final LastCategory selected = new LastCategory(category);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(selected);

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertEquals("welcome", result.get("type"));
      assertEquals("albums", result.get("stage"));
      assertSame(selected, result.get("selected"));
      assertFalse(result.containsKey("albums"));
      assertFalse(result.containsKey("team"));
    }

    @Test
    void contextFetchStageTwoShowsUnresolvedTeamAnsweringPayload() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(game, 30.0, 6.0);
      final TeamEntity team = team(game, "Blue");
      final InterruptEntity teamInterrupt = new InterruptEntity(UUID.randomUUID());
      teamInterrupt.setTeamId(team);
      teamInterrupt.setCorrect(null);

      final List<String> scores = new ArrayList<>(List.of("score-a", "score-b"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(5_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {teamInterrupt, null});

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertEquals("songs", result.get("stage"));
      assertEquals(schedule.getId(), result.get("scheduleId"));
      assertEquals(5.0, result.get("seek"));
      assertEquals(25.0, result.get("remaining"));
      assertSame(scores, result.get("scores"));
      assertEquals(
          team.getId(),
          ((com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse)
                  result.get("answeringTeam"))
              .getId());
      assertEquals(teamInterrupt.getId(), result.get("interruptId"));
      assertFalse(result.containsKey("error"));
      assertFalse(result.containsKey("revealed"));
    }

    @Test
    void contextFetchStageTwoShowsSystemErrorPayload() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(game, 30.0, 6.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      pause.setResolvedAt(null);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(5_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {null, pause});

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertEquals(true, result.get("error"));
      assertEquals(5.0, result.get("seek"));
      assertEquals(25.0, result.get("remaining"));
      assertFalse(result.containsKey("interruptId"));
      assertFalse(result.containsKey("answeringTeam"));
    }

    @Test
    void contextFetchStageTwoFinishedSongOmitsSeekAndInterruptFields() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(game, 8.0, 6.0);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(8_500L);

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertEquals(false, result.get("revealed"));
      assertFalse(result.containsKey("seek"));
      assertFalse(result.containsKey("remaining"));
      assertFalse(result.containsKey("error"));
      assertFalse(result.containsKey("answeringTeam"));
    }

    @Test
    void contextFetchStageThreeReturnsSameScoresObjectWithoutMutatingIt() throws Exception {
      final GameEntity game = game("AKKU", 3);
      final List<String> scores = new ArrayList<>(List.of("red", "blue"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertEquals("winner", result.get("stage"));
      assertSame(scores, result.get("scores"));
      assertEquals(2, scores.size());
    }

    @Test
    void contextFetchRejectsInvalidPersistedStage() {
      final GameEntity game = game("AKKU", 99);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(WrongGameStateException.class, () -> gameService.contextFetch("AKKU"));

      assertEquals("Game state has to be between 0 and 3", exception.getMessage());
    }

    @Test
    void changeStagePersistsBeforeBroadcastAndBroadcastsRebuiltContext() throws Exception {
      final GameEntity game = game("AKKU", 0);
      final GameServiceImpl spyService = spy(gameService);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      doReturn(new HashMap<>(java.util.Map.of("stage", "albums")))
          .when(spyService)
          .contextFetch("AKKU");

      spyService.changeStage(1, "AKKU");

      final InOrder inOrder = inOrder(gameRepository, broadcastGateway);
      inOrder.verify(gameRepository).saveAndFlush(game);
      inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());
      assertEquals(1, game.getStage());
    }

    @Test
    void getStageReturnsMinusOneWhenRoomMissing() {
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.empty());

      assertEquals(-1, gameService.getStage("AKKU"));
    }

    @Test
    void findByCodeDelegatesToStageAwareRepositoryMethod() throws Exception {
      final GameEntity game = game("AKKU", 2);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);

      final GameEntity found = gameService.findByCode("AKKU", 2);

      assertSame(game, found);
      verify(gameRepository).findByCode("AKKU", 2);
    }

    private static GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setMaxAlbums(3);
      game.setMaxSongs(3);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static CategoryEntity category(final GameEntity game) {
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      category.setAlbumId(album("Question?"));
      return category;
    }

    private static AlbumEntity album(final String question) {
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      album.setCustomQuestion(question);
      return album;
    }

    private static ScheduleEntity schedule(
        final GameEntity game, final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setAuthors("Author");
      song.setName("Song");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);

      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album("Who is this?"));
      track.setSongId(song);
      track.setCustomAnswer("Custom answer");

      final CategoryEntity category = category(game);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setCategoryId(category);
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(4));
      schedule.setInterruptList(new ArrayList<>());
      return schedule;
    }

    private static TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name.toLowerCase() + ".png");
      team.setButtonCode(name.substring(0, 1));
      return team;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameOrderingAndRegressionGuards {
    @Mock private TeamService teamService;
    @Mock private InterruptService interruptService;
    @Mock private GameRepository gameRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private GameServiceImpl gameService;

    @Test
    void contextFetchStageTwoMissingScheduleFailsFastWithoutLoadingScoresOrInterrupts()
        throws DerivedException {
      final GameEntity game = game("AKKU", 2);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      assertThrows(NullPointerException.class, () -> gameService.contextFetch("AKKU"));

      verify(teamService, never()).getTeamScores("AKKU");
      verify(interruptService, never())
          .calculateSeek(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
      verify(interruptService, never())
          .getLastTwoInterrupts(
              org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void contextFetchStageTwoMissingAnsweringTeamFailsBeforePublishingErrorState()
        throws DerivedException {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(game, 20.0, 8.0);
      final InterruptEntity teamInterrupt = new InterruptEntity(UUID.randomUUID());
      teamInterrupt.setCorrect(null);
      teamInterrupt.setTeamId(null);

      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.getTeamScores("AKKU")).thenReturn(new ArrayList<>());
      when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(1_000L);
      when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new InterruptEntity[] {teamInterrupt, null});

      assertThrows(NullPointerException.class, () -> gameService.contextFetch("AKKU"));
    }

    @Test
    void contextFetchStageThreeIgnoresStaleSongStateAndOnlyLoadsScores() throws Exception {
      final GameEntity game = game("AKKU", 3);
      final List<String> scores = new ArrayList<>(List.of("red", "blue"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertEquals("winner", result.get("stage"));
      assertSame(scores, result.get("scores"));
      verify(scheduleRepository, never()).findLastPlayed(game.getId());
      verify(interruptService, never())
          .calculateSeek(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void contextFetchStageZeroDoesNotMutateTeamsListFromService() throws Exception {
      final GameEntity game = game("AKKU", 0);
      final List<CreateTeamResponse> teams =
          new ArrayList<>(List.of(new CreateTeamResponse(UUID.randomUUID(), "Red", "red.png")));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(teamService.findByRoomCode("AKKU")).thenReturn(teams);

      final HashMap<String, Object> result = gameService.contextFetch("AKKU");

      assertSame(teams, result.get("teams"));
      assertEquals(1, teams.size());
      assertEquals("Red", teams.getFirst().getName());
    }

    @Test
    void changeStageSameTargetTwiceOnlyPersistsAndBroadcastsFirstTime() throws Exception {
      final GameEntity game = game("AKKU", 0);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      gameService.changeStage(1, "AKKU");

      final InvalidArgumentException exception =
          assertThrows(InvalidArgumentException.class, () -> gameService.changeStage(1, "AKKU"));

      assertEquals("Game is already in that state", exception.getMessage());
      verify(gameRepository).saveAndFlush(game);
      verify(broadcastGateway).broadcast(org.mockito.ArgumentMatchers.eq("AKKU"), anyString());
    }

    @Test
    void changeStageInvalidTransitionDoesNotPersistOrBroadcast() {
      final GameEntity game = game("AKKU", 1);
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));

      final WrongGameStateException exception =
          assertThrows(WrongGameStateException.class, () -> gameService.changeStage(3, "AKKU"));

      assertEquals(
          "Album selection is in progress. We can only move to song listening (stage 2)",
          exception.getMessage());
      verify(gameRepository, never()).saveAndFlush(game);
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void changeStagePersistsBeforeRebuildingAndBroadcastingContext() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final List<String> scores = new ArrayList<>(List.of("gold"));
      when(gameRepository.findByCode("AKKU")).thenReturn(Optional.of(game));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(teamService.getTeamScores("AKKU")).thenReturn(scores);

      gameService.changeStage(3, "AKKU");

      final var inOrder = inOrder(gameRepository, teamService, broadcastGateway);
      inOrder.verify(gameRepository).saveAndFlush(game);
      inOrder.verify(teamService).getTeamScores("AKKU");
      inOrder
          .verify(broadcastGateway)
          .broadcast(org.mockito.ArgumentMatchers.eq("AKKU"), anyString());
      assertEquals(3, game.getStage());
    }

    private static GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setMaxAlbums(3);
      game.setMaxSongs(3);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static ScheduleEntity schedule(
        final GameEntity game, final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setName("Track");
      song.setAuthors("Artist");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setCustomQuestion("Question");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      track.setCustomAnswer("Answer");
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(2));
      return schedule;
    }
  }
}
