package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.MissingArgumentException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

public class CategoryServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CategoryCoreFlow {
    @Mock private GameService gameService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private CategoryServiceImpl categoryService;

    @Test
    void pickAlbumRejectsUnknownTeam() {
      final CategoryEntity category = category("AKKU", 1, 5);
      final UUID teamId = UUID.randomUUID();
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(teamId), "AKKU"));

      assertEquals("Team with with id " + teamId + " does not exist", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsTeamRoomMismatch() {
      final CategoryEntity category = category("AKKU", 1, 5);
      final TeamEntity team = team(game("ZZZZ", 1), "Blue");
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () ->
                  categoryService.pickAlbum(
                      category.getId(), new TeamIdRequest(team.getId()), "AKKU"));

      assertEquals(
          "Room code AKKU isn't consistent with the provided team", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsCategoryRoomMismatch() {
      final CategoryEntity category = category("ZZZZ", 1, 5);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the category", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsWrongStage() {
      final CategoryEntity category = category("AKKU", 2, 5);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("Game AKKU doesn't choose albums now", exception.getMessage());
    }

    @Test
    void pickAlbumRequiresTvPresence() {
      final CategoryEntity category = category("AKKU", 1, 5);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("TV app has to be connected to proceed", exception.getMessage());
    }

    @Test
    void pickAlbumAssignsPickerOrdinalPersistsAndBroadcasts() throws Exception {
      final CategoryEntity category = category("AKKU", 1, 5);
      final TeamEntity team = team(category.getGameId(), "Blue");
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(categoryRepository.findNextId(category.getGameId().getId())).thenReturn(3);

      final LastCategory result =
          categoryService.pickAlbum(category.getId(), new TeamIdRequest(team.getId()), "AKKU");

      assertSame(team, category.getPickedByTeamId());
      assertEquals(3, category.getOrdinalNumber());
      assertEquals(category.getId(), result.getCategoryId());
      verify(categoryRepository).saveAndFlush(category);
      verify(broadcastGateway).toTv(any(), any());
    }

    @Test
    void startCategoryRejectsMissingCategory() {
      final UUID categoryId = UUID.randomUUID();
      when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> categoryService.startCategory(categoryId, "AKKU"));

      assertEquals(
          "Category with with id " + categoryId + " does not exist", exception.getMessage());
    }

    @Test
    void startCategoryRejectsRoomMismatch() {
      final CategoryEntity category = category("ZZZZ", 1, 5);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the category", exception.getMessage());
    }

    @Test
    void startCategoryRejectsAlbumWithTooFewSongs() throws Exception {
      final CategoryEntity category = category("AKKU", 1, 2);
      category.getAlbumId().setTrackList(new ArrayList<>(List.of(track(20.0))));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(category.getGameId());

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertTrue(exception.getMessage().contains("doesn't have enough songs"));
    }

    @Test
    void startCategoryCreatesSchedulesStartsFirstSongAndChangesStage() throws Exception {
      final CategoryEntity category = category("AKKU", 1, 2);
      category
          .getAlbumId()
          .setTrackList(new ArrayList<>(List.of(track(20.0), track(21.0), track(22.0))));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(category.getGameId());

      categoryService.startCategory(category.getId(), "AKKU");

      final ArgumentCaptor<List<ScheduleEntity>> schedules = ArgumentCaptor.forClass(List.class);
      verify(scheduleRepository).saveAllAndFlush(schedules.capture());
      assertEquals(2, schedules.getValue().size());
      assertEquals(1, schedules.getValue().get(0).getOrdinalNumber());
      assertNotNull(schedules.getValue().get(0).getStartedAt());
      verify(gameService).changeStage(2, "AKKU");
    }

    @Test
    void finishAndNextMarksLastCategoryDoneAndReturnsAlbumStageWhenMoreRemain() throws Exception {
      final GameEntity game = game("AKKU", 2);
      game.setMaxAlbums(4);
      final CategoryEntity category = category("AKKU", 1, 3);
      category.setOrdinalNumber(2);
      final LastCategory dto = new LastCategory(category);
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(dto);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final int nextState = categoryService.finishAndNext(game);

      assertEquals(1, nextState);
      assertEquals(Boolean.TRUE, category.isDone());
      verify(categoryRepository).saveAndFlush(category);
    }

    @Test
    void finishAndNextReturnsWinnerStageAfterLastAlbum() throws Exception {
      final GameEntity game = game("AKKU", 2);
      game.setMaxAlbums(2);
      final CategoryEntity category = category("AKKU", 1, 3);
      category.setOrdinalNumber(2);
      final LastCategory dto = new LastCategory(category);
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(dto);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final int nextState = categoryService.finishAndNext(game);

      assertEquals(3, nextState);
    }

    private GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setMaxSongs(2);
      game.setMaxAlbums(3);
      return game;
    }

    private CategoryEntity category(final String code, final int stage, final int maxSongs) {
      final GameEntity game = game(code, stage);
      game.setMaxSongs(maxSongs);
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      category.setAlbumId(new AlbumEntity(UUID.randomUUID(), "Album"));
      return category;
    }

    private TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name + ".png");
      return team;
    }

    private TrackEntity track(final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setSongId(song);
      return track;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CategorySelectionValidation {
    @Mock private GameService gameService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private CategoryServiceImpl categoryService;

    @Test
    void pickAlbumRequiresRequestBody() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> categoryService.pickAlbum(UUID.randomUUID(), null, "AKKU"));

      assertEquals("The request's body is missing", exception.getMessage());
    }

    @Test
    void pickAlbumRequiresCategoryId() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> categoryService.pickAlbum(null, new TeamIdRequest(UUID.randomUUID()), "AKKU"));

      assertEquals("The request's body is missing category_id", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsUnknownTeam() {
      final UUID categoryId = UUID.randomUUID();
      final UUID teamId = UUID.randomUUID();
      when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category("AKKU", 1, 2)));
      when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> categoryService.pickAlbum(categoryId, new TeamIdRequest(teamId), "AKKU"));

      assertEquals("Team with with id " + teamId + " does not exist", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsRoomMismatchBetweenTeamAndGame() {
      final CategoryEntity category = category("AKKU", 1, 2);
      final TeamEntity team = team(game("ZZZZ", 1), "Blue");
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () ->
                  categoryService.pickAlbum(
                      category.getId(), new TeamIdRequest(team.getId()), "AKKU"));

      assertEquals(
          "Room code AKKU isn't consistent with the provided team", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsWrongGameStage() {
      final CategoryEntity category = category("AKKU", 2, 2);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("Game AKKU doesn't choose albums now", exception.getMessage());
    }

    @Test
    void pickAlbumRequiresTvPresence() {
      final CategoryEntity category = category("AKKU", 1, 2);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(categoryRepository.findNextId(category.getGameId().getId())).thenReturn(1);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("TV app has to be connected to proceed", exception.getMessage());
    }

    @Test
    void startCategoryRejectsRoomMismatch() {
      final CategoryEntity category = category("ZZZZ", 1, 3);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the category", exception.getMessage());
    }

    @Test
    void startCategoryRejectsCategoryWithTooFewSongs() throws Exception {
      final CategoryEntity category = category("AKKU", 1, 2);
      category.getAlbumId().setTrackList(new ArrayList<>(List.of(track(), track())));
      final GameEntity game = category.getGameId();
      game.setMaxSongs(3);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(game);

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertEquals("The category (len:2) doesn't have enough songs (3)", exception.getMessage());
      verify(scheduleRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void startCategoryCreatesSchedulesAndTransitionsToStageTwo() throws Exception {
      final CategoryEntity category = category("AKKU", 1, 5);
      final GameEntity game = category.getGameId();
      game.setMaxSongs(3);
      category
          .getAlbumId()
          .setTrackList(new ArrayList<>(List.of(track(), track(), track(), track())));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(game);

      categoryService.startCategory(category.getId(), "AKKU");

      final ArgumentCaptor<List> schedules = ArgumentCaptor.forClass(List.class);
      verify(scheduleRepository).saveAllAndFlush(schedules.capture());
      assertEquals(3, schedules.getValue().size());
      assertNotNull(
          ((com.cevapinxile.cestereg.persistence.entity.ScheduleEntity) schedules.getValue().get(0))
              .getStartedAt());
      verify(gameService).changeStage(2, "AKKU");
    }

    @Test
    void finishAndNextRejectsMissingPersistedCategory() {
      final GameEntity game = game("AKKU", 2);
      final LastCategory dto = new LastCategory();
      dto.setCategoryId(UUID.randomUUID());
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(dto);
      when(categoryRepository.findById(dto.getCategoryId())).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class, () -> categoryService.finishAndNext(game));

      assertEquals(
          "Category with with id " + dto.getCategoryId() + " does not exist",
          exception.getMessage());
    }

    @Test
    void finishAndNextReturnsWinnerStageWhenLastAlbumWasCompleted() throws Exception {
      final GameEntity game = game("AKKU", 2);
      game.setMaxAlbums(2);
      final CategoryEntity category = category("AKKU", 2, 2);
      final LastCategory dto = new LastCategory();
      dto.setCategoryId(category.getId());
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(dto);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final int result = categoryService.finishAndNext(game);

      assertEquals(3, result);
      assertEquals(Boolean.TRUE, category.isDone());
      verify(categoryRepository).saveAndFlush(category);
    }

    private GameEntity game(String code, int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setDate(LocalDateTime.now());
      game.setMaxAlbums(3);
      game.setMaxSongs(3);
      return game;
    }

    private TeamEntity team(GameEntity game, String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name.toLowerCase() + ".png");
      return team;
    }

    private CategoryEntity category(String code, int stage, int ordinal) {
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      final GameEntity game = game(code, stage);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      album.setTrackList(new ArrayList<>());
      category.setGameId(game);
      category.setAlbumId(album);
      category.setOrdinalNumber(ordinal);
      return category;
    }

    private TrackEntity track() {
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(new AlbumEntity(UUID.randomUUID()));
      track.setSongId(
          new com.cevapinxile.cestereg.persistence.entity.SongEntity(UUID.randomUUID()));
      return track;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CategoryLifecycleAndBoundaries {
    @Mock private GameService gameService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private CategoryServiceImpl categoryService;

    @Test
    void pickAlbumRejectsMissingBody() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> categoryService.pickAlbum(UUID.randomUUID(), null, "AKKU"));

      assertEquals("The request's body is missing", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsMissingCategoryId() {
      final MissingArgumentException exception =
          assertThrows(
              MissingArgumentException.class,
              () -> categoryService.pickAlbum(null, new TeamIdRequest(null), "AKKU"));

      assertEquals("The request's body is missing category_id", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsUnknownCategory() {
      final UUID categoryId = UUID.randomUUID();
      when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> categoryService.pickAlbum(categoryId, new TeamIdRequest(null), "AKKU"));

      assertEquals(
          "Category with with id " + categoryId + " does not exist", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsUnknownTeam() {
      final CategoryEntity category = category("AKKU", 1);
      final UUID teamId = UUID.randomUUID();
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(teamId), "AKKU"));

      assertEquals("Team with with id " + teamId + " does not exist", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsCategoryFromDifferentRoom() {
      final CategoryEntity category = category("BLAH", 1);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the category", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsWhenGameIsNotChoosingAlbums() {
      final CategoryEntity category = category("AKKU", 2);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("Game AKKU doesn't choose albums now", exception.getMessage());
    }

    @Test
    void pickAlbumRejectsWhenTvMissing() {
      final CategoryEntity category = category("AKKU", 1);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(categoryRepository.findNextId(category.getGameId().getId())).thenReturn(2);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU"));

      assertEquals("TV app has to be connected to proceed", exception.getMessage());
      verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void pickAlbumSupportsNullPickerAndBroadcastsSelectedAlbum() throws Exception {
      final CategoryEntity category = category("AKKU", 1);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(categoryRepository.findNextId(category.getGameId().getId())).thenReturn(3);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      final LastCategory result =
          categoryService.pickAlbum(category.getId(), new TeamIdRequest(null), "AKKU");

      assertNull(category.getPickedByTeamId());
      assertEquals(3, category.getOrdinalNumber());
      assertSame(category.getId(), result.getCategoryId());
      verify(categoryRepository).saveAndFlush(category);
      verify(broadcastGateway)
          .toTv(
              org.mockito.Mockito.eq("AKKU"),
              org.mockito.ArgumentMatchers.contains("\"type\":\"album_picked\""));
    }

    @Test
    void startCategoryRejectsUnknownCategory() {
      final UUID categoryId = UUID.randomUUID();
      when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> categoryService.startCategory(categoryId, "AKKU"));

      assertEquals(
          "Category with with id " + categoryId + " does not exist", exception.getMessage());
    }

    @Test
    void startCategoryRejectsCategoryFromDifferentRoom() {
      final CategoryEntity category = category("BLAH", 1);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the category", exception.getMessage());
    }

    @Test
    void startCategoryRejectsWhenAlbumHasTooFewTracks() throws Exception {
      final CategoryEntity category = category("AKKU", 1);
      category.getAlbumId().setTrackList(new ArrayList<>(List.of(track(), track())));
      final GameEntity game = category.getGameId();
      game.setMaxSongs(3);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(game);

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertEquals("The category (len:2) doesn't have enough songs (3)", exception.getMessage());
    }

    @Test
    void startCategoryCreatesSchedulesStartsFirstSongAndChangesStage() throws Exception {
      final CategoryEntity category = category("AKKU", 1);
      final GameEntity game = category.getGameId();
      game.setMaxSongs(2);
      category.getAlbumId().setTrackList(new ArrayList<>(List.of(track(), track(), track())));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(game);

      categoryService.startCategory(category.getId(), "AKKU");

      verify(scheduleRepository).saveAllAndFlush(any());
      verify(gameService).changeStage(2, "AKKU");
    }

    @Test
    void finishAndNextMarksLastCategoryDoneAndReturnsWinnerStageAtEnd() throws Exception {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setMaxAlbums(2);
      final CategoryEntity category = category("AKKU", 1);
      category.setOrdinalNumber(2);
      final LastCategory lastCategory = new LastCategory(category);

      when(categoryRepository.findLastCategory(game.getId())).thenReturn(lastCategory);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final int nextState = categoryService.finishAndNext(game);

      assertEquals(3, nextState);
      assertEquals(Boolean.TRUE, category.isDone());
      verify(categoryRepository).saveAndFlush(category);
    }

    @Test
    void finishAndNextRejectsWhenLastCategoryProjectionPointsToMissingCategory() {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      final CategoryEntity category = category("AKKU", 1);
      final LastCategory lastCategory = new LastCategory(category);
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(lastCategory);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class, () -> categoryService.finishAndNext(game));

      assertEquals(
          "Category with with id " + category.getId() + " does not exist", exception.getMessage());
    }

    private CategoryEntity category(final String roomCode, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(roomCode);
      game.setStage(stage);
      game.setMaxAlbums(4);
      game.setMaxSongs(2);

      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      album.setTrackList(new ArrayList<>());
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      category.setAlbumId(album);
      category.setOrdinalNumber(1);
      return category;
    }

    private TrackEntity track() {
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      return track;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CategoryRegressionAndFailureCases {
    @Mock private GameService gameService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private CategoryServiceImpl categoryService;

    @Test
    void pickAlbumRejectsTeamFromDifferentRoom() {
      final CategoryEntity category = category(game("AKKU", 1));
      final TeamEntity team = team(game("BLAH", 1), "Green");
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () ->
                  categoryService.pickAlbum(
                      category.getId(), new TeamIdRequest(team.getId()), "AKKU"));

      assertEquals(
          "Room code AKKU isn't consistent with the provided team", exception.getMessage());
    }

    @Test
    void pickAlbumPersistsOrdinalAndPickerOnSuccess() throws Exception {
      final GameEntity game = game("AKKU", 1);
      final CategoryEntity category = category(game);
      final TeamEntity team = team(game, "Green");
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
      when(categoryRepository.findNextId(game.getId())).thenReturn(5);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

      final LastCategory result =
          categoryService.pickAlbum(category.getId(), new TeamIdRequest(team.getId()), "AKKU");

      assertEquals(5, category.getOrdinalNumber());
      assertSame(team, category.getPickedByTeamId());
      assertEquals(category.getId(), result.getCategoryId());
      verify(categoryRepository).saveAndFlush(category);
    }

    @Test
    void startCategoryPropagatesWrongStageFromGameService() throws Exception {
      final CategoryEntity category = category(game("AKKU", 1));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU"))
          .thenThrow(
              new WrongGameStateException(
                  "Album selection is in progress. We can only move to song listening (stage 2)"));

      final WrongGameStateException exception =
          assertThrows(
              WrongGameStateException.class,
              () -> categoryService.startCategory(category.getId(), "AKKU"));

      assertEquals(
          "Album selection is in progress. We can only move to song listening (stage 2)",
          exception.getMessage());
    }

    @Test
    void finishAndNextRejectsMissingPersistedLastCategory() {
      final GameEntity game = game("AKKU", 2);
      final LastCategory lastCategory = new LastCategory(category(game));
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(lastCategory);
      when(categoryRepository.findById(lastCategory.getCategoryId())).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class, () -> categoryService.finishAndNext(game));

      assertEquals(
          "Category with with id " + lastCategory.getCategoryId() + " does not exist",
          exception.getMessage());
    }

    @Test
    void finishAndNextReturnsAlbumSelectionWhenMoreAlbumsRemain() throws Exception {
      final GameEntity game = game("AKKU", 2);
      game.setMaxAlbums(3);
      final CategoryEntity category = category(game);
      category.setOrdinalNumber(2);
      when(categoryRepository.findLastCategory(game.getId()))
          .thenReturn(new LastCategory(category));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final int state = categoryService.finishAndNext(game);

      assertEquals(1, state);
      assertEquals(true, category.isDone());
      verify(categoryRepository).saveAndFlush(category);
    }

    private static GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static CategoryEntity category(final GameEntity game) {
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      album.setCustomQuestion("Question");
      final List<TrackEntity> tracks = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        final TrackEntity track = new TrackEntity(UUID.randomUUID());
        track.setAlbumId(album);
        track.setSongId(
            new com.cevapinxile.cestereg.persistence.entity.SongEntity(UUID.randomUUID()));
        tracks.add(track);
      }
      album.setTrackList(tracks);
      category.setAlbumId(album);
      category.setOrdinalNumber(1);
      return category;
    }

    private static TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name + ".png");
      return team;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class CategoryOrderingAndFinalization {
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
    void startCategoryRejectsForeignRoomWithoutCreatingScheduleOrChangingStage()
        throws DerivedException {
      final CategoryEntity category = category(game("BLAH", 1));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> categoryServiceImpl.startCategory(category.getId(), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the category", exception.getMessage());
      verify(scheduleRepository, never()).saveAllAndFlush(any());
      verify(gameService, never()).changeStage(any(Integer.class), anyString());
    }

    @Test
    void startCategoryPersistsSchedulesBeforeChangingStage() throws Exception {
      final GameEntity game = game("AKKU", 1);
      game.setMaxSongs(2);
      final CategoryEntity category = category(game);
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
      when(gameService.isChangeStageLegal(2, "AKKU")).thenReturn(game);

      categoryServiceImpl.startCategory(category.getId(), "AKKU");

      final InOrder inOrder = inOrder(scheduleRepository, gameService);
      inOrder.verify(scheduleRepository).saveAllAndFlush(any());
      inOrder.verify(gameService).changeStage(2, "AKKU");
    }

    @Test
    void finishAndNextOnFinalAlbumReturnsWinnerStateAndPersistsDoneFlag() throws Exception {
      final GameEntity game = game("AKKU", 2);
      game.setMaxAlbums(2);
      final CategoryEntity category = category(game);
      category.setOrdinalNumber(2);
      when(categoryRepository.findLastCategory(game.getId()))
          .thenReturn(new com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory(category));
      when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

      final int nextState = categoryServiceImpl.finishAndNext(game);

      assertEquals(3, nextState);
      assertEquals(true, category.isDone());
      verify(categoryRepository).saveAndFlush(category);
    }

    @Test
    void finishAndNextFailsWhenNoPersistedLastCategoryExists() {
      final GameEntity game = game("AKKU", 2);
      when(categoryRepository.findLastCategory(game.getId())).thenReturn(null);

      assertThrows(NullPointerException.class, () -> categoryServiceImpl.finishAndNext(game));

      verify(categoryRepository, never()).saveAndFlush(any());
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

    private static CategoryEntity category(final GameEntity game) {
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      album.setCustomQuestion("Question");
      final List<TrackEntity> tracks = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        final SongEntity song = new SongEntity(UUID.randomUUID());
        song.setName("Song" + i);
        song.setAuthors("Artist");
        song.setSnippetDuration(10.0 + i);
        song.setAnswerDuration(5.0 + i);
        final TrackEntity track = new TrackEntity(UUID.randomUUID());
        track.setAlbumId(album);
        track.setSongId(song);
        tracks.add(track);
      }
      album.setTrackList(tracks);
      category.setAlbumId(album);
      return category;
    }
  }
}
