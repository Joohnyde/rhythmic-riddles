package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

public class ScheduleServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ScheduleCoreFlow {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;

    @InjectMocks private ScheduleServiceImpl scheduleService;

    @Test
    void replaySongRejectsMissingSchedule() throws Exception {
      final GameEntity game = game();
      final UUID scheduleId = UUID.randomUUID();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> scheduleService.replaySong(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void replaySongRequiresBothApps() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity schedule = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> scheduleService.replaySong(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void replaySongResetsStartTimeResolvesErrorsAndBroadcasts() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity schedule = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.replaySong(schedule.getId(), "AKKU");

      assertNotNull(schedule.getStartedAt());
      verify(interruptRepository).resolveErrors(any(UUID.class), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(schedule);
      verify(broadcastGateway).broadcast(any(), any());
    }

    @Test
    void revealAnswerRejectsMissingSchedule() throws Exception {
      final GameEntity game = game();
      final UUID scheduleId = UUID.randomUUID();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> scheduleService.revealAnswer(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void revealAnswerMarksSongRevealedAndBroadcasts() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity schedule = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.revealAnswer(schedule.getId(), "AKKU");

      assertNotNull(schedule.getRevealedAt());
      verify(interruptRepository).resolveErrors(any(UUID.class), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(schedule);
      verify(broadcastGateway).broadcast(any(), any());
    }

    @Test
    void progressRequiresBothApps() throws Exception {
      final GameEntity game = game();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(AppNotRegisteredException.class, () -> scheduleService.progress("AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void progressStartsNextSongAndBroadcastsSongNextPayload() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity lastPlayed = schedule(game, 25.0);
      final ScheduleEntity next = schedule(game, 18.0);
      next.setStartedAt(null);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(next));

      scheduleService.progress("AKKU");

      assertNotNull(next.getStartedAt());
      verify(interruptRepository).resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(next);
      verify(broadcastGateway).broadcast(any(), any());
      verify(gameService, never()).changeStage(org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void progressFinishesCategoryAndChangesStageWhenNoMoreSongsRemain() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity lastPlayed = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.empty());
      when(categoryService.finishAndNext(game)).thenReturn(3);

      scheduleService.progress("AKKU");

      verify(categoryService).finishAndNext(game);
      verify(gameService).changeStage(3, "AKKU");
      verify(scheduleRepository, never()).saveAndFlush(any(ScheduleEntity.class));
    }

    private GameEntity game() {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(2);
      return game;
    }

    private ScheduleEntity schedule(final GameEntity game, final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(7.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setCategoryId(category);
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(8));
      return schedule;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ScheduleValidationAndPresence {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;

    @InjectMocks private ScheduleServiceImpl scheduleService;

    @Test
    void replaySongRejectsUnknownSchedule() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final UUID scheduleId = UUID.randomUUID();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> scheduleService.replaySong(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void replaySongRequiresBothApps() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(30.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> scheduleService.replaySong(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void replaySongResetsStartTimeAndBroadcastsRepeat() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(22.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.replaySong(schedule.getId(), "AKKU");

      assertNotNull(schedule.getStartedAt());
      verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(schedule);
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("song_repeat"));
    }

    @Test
    void revealAnswerRejectsUnknownSchedule() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final UUID scheduleId = UUID.randomUUID();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> scheduleService.revealAnswer(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void revealAnswerRequiresBothApps() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(22.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> scheduleService.revealAnswer(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void revealAnswerMarksScheduleAndBroadcastsReveal() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(22.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.revealAnswer(schedule.getId(), "AKKU");

      assertNotNull(schedule.getRevealedAt());
      verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(schedule);
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("song_reveal"));
    }

    @Test
    void progressRequiresBothApps() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity last = schedule(20.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);

      final AppNotRegisteredException exception =
          assertThrows(AppNotRegisteredException.class, () -> scheduleService.progress("AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
      verify(interruptRepository, never()).resolveErrors(any(), any());
    }

    @Test
    void progressFinishesCategoryAndChangesStageWhenNoNextSongExists() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity last = schedule(20.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(last);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.empty());
      when(categoryService.finishAndNext(game)).thenReturn(3);

      scheduleService.progress("AKKU");

      verify(interruptRepository).resolveErrors(eq(last.getId()), any(LocalDateTime.class));
      verify(categoryService).finishAndNext(game);
      verify(gameService).changeStage(3, "AKKU");
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    private GameEntity game(String code, int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      return game;
    }

    private ScheduleEntity schedule(double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setAuthors("Artist");
      song.setName("Track");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(9.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(5));
      return schedule;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ScheduleReplayRevealAndProgression {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;

    @InjectMocks private ScheduleServiceImpl scheduleService;

    @Test
    void replaySongUpdatesTimestampResolvesErrorsBeforePersistingAndBroadcastsRemaining()
        throws Exception {
      final GameEntity game = game();
      final ScheduleEntity schedule = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.replaySong(schedule.getId(), "AKKU");

      assertNotNull(schedule.getStartedAt());
      final org.mockito.InOrder inOrder =
          inOrder(interruptRepository, scheduleRepository, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      inOrder.verify(scheduleRepository).saveAndFlush(schedule);
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"song_repeat\""));
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"remaining\":25.0"));
    }

    @Test
    void revealAnswerRequiresBothApps() throws DerivedException {
      final GameEntity game = game();
      final ScheduleEntity schedule = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> scheduleService.revealAnswer(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
      verify(interruptRepository, never()).resolveErrors(any(), any());
    }

    @Test
    void revealAnswerResolvesErrorsPersistsRevealAndBroadcastsSongReveal() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity schedule = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.revealAnswer(schedule.getId(), "AKKU");

      assertNotNull(schedule.getRevealedAt());
      final org.mockito.InOrder inOrder =
          inOrder(interruptRepository, scheduleRepository, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      inOrder.verify(scheduleRepository).saveAndFlush(schedule);
      inOrder.verify(broadcastGateway).broadcast("AKKU", "{\"type\":\"song_reveal\"}");
    }

    @Test
    void progressBlowsUpWhenThereIsNoLastPlayedSong() throws Exception {
      final GameEntity game = game();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      assertThrows(NullPointerException.class, () -> scheduleService.progress("AKKU"));

      verify(interruptRepository, never()).resolveErrors(any(), any());
    }

    @Test
    void progressStartsNextSongResolvesErrorsAndDoesNotChangeStage() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity lastPlayed = schedule(game, 25.0);
      final ScheduleEntity next = schedule(game, 14.0);
      next.setStartedAt(null);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(next));

      scheduleService.progress("AKKU");

      assertNotNull(next.getStartedAt());
      final org.mockito.InOrder inOrder =
          inOrder(interruptRepository, scheduleRepository, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
      inOrder.verify(scheduleRepository).saveAndFlush(next);
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"song_next\""));
      verify(gameService, never()).changeStage(org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void progressCanBeCalledRepeatedlyAndResolvesErrorsForEachCurrentSong() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity first = schedule(game, 20.0);
      final ScheduleEntity second = schedule(game, 18.0);
      final ScheduleEntity third = schedule(game, 16.0);
      second.setStartedAt(null);
      third.setStartedAt(null);

      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(first).thenReturn(second);
      when(scheduleRepository.findNext(game.getId()))
          .thenReturn(Optional.of(second))
          .thenReturn(Optional.of(third));

      scheduleService.progress("AKKU");
      scheduleService.progress("AKKU");

      verify(interruptRepository).resolveErrors(eq(first.getId()), any(LocalDateTime.class));
      verify(interruptRepository).resolveErrors(eq(second.getId()), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(second);
      verify(scheduleRepository).saveAndFlush(third);
    }

    @Test
    void progressFinishesCategoryAndMovesBackToAlbumsWhenMoreAlbumsRemain() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity lastPlayed = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.empty());
      when(categoryService.finishAndNext(game)).thenReturn(1);

      scheduleService.progress("AKKU");

      verify(categoryService).finishAndNext(game);
      verify(gameService).changeStage(1, "AKKU");
      verify(broadcastGateway, never())
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("song_next"));
    }

    @Test
    void progressFinishesCategoryAndMovesToWinnerAtEndOfGame() throws Exception {
      final GameEntity game = game();
      final ScheduleEntity lastPlayed = schedule(game, 25.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.empty());
      when(categoryService.finishAndNext(game)).thenReturn(3);

      scheduleService.progress("AKKU");

      verify(categoryService).finishAndNext(game);
      verify(gameService).changeStage(3, "AKKU");
      verify(scheduleRepository, never()).saveAndFlush(any(ScheduleEntity.class));
    }

    private GameEntity game() {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode("AKKU");
      game.setStage(2);
      return game;
    }

    private ScheduleEntity schedule(final GameEntity game, final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(6.0);
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

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ScheduleRegressionAndExceptionHandling {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;

    @InjectMocks private ScheduleServiceImpl scheduleService;

    @Test
    void replaySongRejectsUnknownSchedule() throws Exception {
      final GameEntity game = game("AKKU");
      final UUID scheduleId = UUID.randomUUID();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> scheduleService.replaySong(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void replaySongRejectsWhenBothAppsMissing() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(12.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> scheduleService.replaySong(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
      verify(interruptRepository, never()).resolveErrors(eq(schedule.getId()), any());
    }

    @Test
    void replaySongResolvesThenSavesThenBroadcasts() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(12.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.replaySong(schedule.getId(), "AKKU");

      final InOrder inOrder = inOrder(interruptRepository, scheduleRepository, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      inOrder.verify(scheduleRepository).saveAndFlush(schedule);
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"song_repeat\""));
    }

    @Test
    void revealAnswerRejectsUnknownSchedule() throws Exception {
      final GameEntity game = game("AKKU");
      final UUID scheduleId = UUID.randomUUID();
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> scheduleService.revealAnswer(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void revealAnswerResolvesThenSavesThenBroadcasts() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(12.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      scheduleService.revealAnswer(schedule.getId(), "AKKU");

      final InOrder inOrder = inOrder(interruptRepository, scheduleRepository, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      inOrder.verify(scheduleRepository).saveAndFlush(schedule);
      inOrder.verify(broadcastGateway).broadcast("AKKU", "{\"type\":\"song_reveal\"}");
    }

    @Test
    void progressWithoutNextSongFinishesCategoryThenChangesStage() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity lastPlayed = schedule(14.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.empty());
      when(categoryService.finishAndNext(game)).thenReturn(3);

      scheduleService.progress("AKKU");

      final InOrder inOrder = inOrder(interruptRepository, categoryService, gameService);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
      inOrder.verify(categoryService).finishAndNext(game);
      inOrder.verify(gameService).changeStage(3, "AKKU");
      verify(broadcastGateway, never())
          .broadcast(anyString(), org.mockito.ArgumentMatchers.contains("song_next"));
    }

    @Test
    void progressWithNullLastPlayedSongBlowsUpBeforeAnySideEffects() throws Exception {
      final GameEntity game = game("AKKU");
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      assertThrows(NullPointerException.class, () -> scheduleService.progress("AKKU"));

      verify(scheduleRepository, never()).findNext(game.getId());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void progressStartsNextSongAndBroadcastsPayloadWithCoreFields() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity lastPlayed = schedule(14.0);
      final ScheduleEntity nextSong = schedule(20.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(nextSong));

      scheduleService.progress("AKKU");

      verify(scheduleRepository).saveAndFlush(nextSong);
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"song_next\""));
      verify(broadcastGateway)
          .broadcast(
              eq("AKKU"), org.mockito.ArgumentMatchers.contains(nextSong.getId().toString()));
      verify(broadcastGateway)
          .broadcast(
              eq("AKKU"),
              org.mockito.ArgumentMatchers.contains(
                  nextSong.getTrackId().getSongId().getId().toString()));
    }

    private static GameEntity game(final String code) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(2);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static ScheduleEntity schedule(final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setName("Song");
      song.setAuthors("Artist");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(9.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setCustomQuestion("Question");
      album.setName("Album");
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

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ScheduleOrderingAndRepeatedProgression {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TeamRepository teamRepository;

    @InjectMocks private ScheduleServiceImpl scheduleService;
    @InjectMocks private CategoryServiceImpl categoryServiceImpl;

    @Test
    void replaySongRejectsWhenAppsMissingAndDoesNotResolveSaveOrBroadcast()
        throws DerivedException {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(15.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> scheduleService.replaySong(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
      verify(interruptRepository, never()).resolveErrors(eq(schedule.getId()), any());
      verify(scheduleRepository, never()).saveAndFlush(schedule);
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void progressTwiceFirstBroadcastsNextSongThenFinishesCategory() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity current = schedule(14.0);
      final ScheduleEntity nextSong = schedule(20.0);
      final ScheduleEntity secondCurrent = schedule(12.0);
      when(gameService.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(scheduleRepository.findLastPlayed(game.getId()))
          .thenReturn(current)
          .thenReturn(secondCurrent);
      when(scheduleRepository.findNext(game.getId()))
          .thenReturn(Optional.of(nextSong))
          .thenReturn(Optional.empty());
      when(categoryService.finishAndNext(game)).thenReturn(3);

      scheduleService.progress("AKKU");
      scheduleService.progress("AKKU");

      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"song_next\""));
      verify(gameService).changeStage(3, "AKKU");
      verify(interruptRepository).resolveErrors(eq(current.getId()), any(LocalDateTime.class));
      verify(interruptRepository)
          .resolveErrors(eq(secondCurrent.getId()), any(LocalDateTime.class));
    }

    private static GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setDate(LocalDateTime.now().minusHours(1));
      game.setMaxAlbums(3);
      game.setMaxSongs(3);
      return game;
    }

    private static ScheduleEntity schedule(final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setName("Song");
      song.setAuthors("Artist");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(6.0);
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
