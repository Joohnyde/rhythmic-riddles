/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

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
import com.cevapinxile.cestereg.core.service.support.RoomLocks;
import com.cevapinxile.cestereg.core.service.support.TransactionCallbacks;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/*
 * @author denijal
 */
@Service
public class CategoryServiceImpl implements CategoryService {

  private static final Logger LOG = LoggerFactory.getLogger(CategoryServiceImpl.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private GameService gameService;

  @Autowired private GameRepository gameRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private ScheduleRepository scheduleRepository;

  @Autowired private TeamRepository teamRepository;
  @Autowired private BroadcastGateway broadcastGateway;

  @Autowired private PresenceGateway presenceGateway;

  @Override
  @Transactional(rollbackOn = DerivedException.class)
  public LastCategory pickAlbum(
      final UUID categoryId, final TeamIdRequest par, final String roomCode)
      throws DerivedException {
    // Request validation (fail fast on invalid input before taking the room lock).
    if (par == null) {
      throw new MissingArgumentException("The request's body is missing");
    }
    final UUID teamId = par.teamId();
    if (categoryId == null) {
      throw new MissingArgumentException("The request's body is missing category_id");
    }
    RoomLocks.tryLock(gameRepository, roomCode);
    final Optional<CategoryEntity> maybeCategory = categoryRepository.findById(categoryId);
    if (maybeCategory.isEmpty()) {
      throw new InvalidReferencedObjectException(
          "Category with with id " + categoryId + " does not exist");
    }
    TeamEntity team = null;
    if (teamId != null) {
      final Optional<TeamEntity> maybeTeam = teamRepository.findById(teamId);
      if (maybeTeam.isEmpty()) {
        throw new InvalidReferencedObjectException(
            "Team with with id " + teamId + " does not exist");
      }
      team = maybeTeam.get();
      if (!team.getGameId().getCode().equals(roomCode)) {
        throw new InvalidArgumentException(
            "Room code " + roomCode + " isn't consistent with the provided team");
      }
    }
    final CategoryEntity category = maybeCategory.get();
    if (!category.getGameId().getCode().equals(roomCode)) {
      throw new InvalidArgumentException(
          "Room code " + roomCode + " isn't consistent with the category");
    }
    if (category.getGameId().getStage() != 1) {
      throw new WrongGameStateException("Game " + roomCode + " doesn't choose albums now");
    }
    final LastCategory lockedLastCategory =
        categoryRepository.findLastCategory(category.getGameId().getId());
    if (lockedLastCategory != null && !lockedLastCategory.isStarted()) {
      throw new InvalidArgumentException("An album is already selected and has not started yet");
    }
    category.setPickedByTeamId(team);
    category.setOrdinalNumber(categoryRepository.findNextId(category.getGameId().getId()));

    /* No state change is legal if both apps aren't present.
    This request is made by admin app so their app is obviously
    there, hence the error message. */
    if (!presenceGateway.areBothPresent(roomCode)) {
      throw new AppNotRegisteredException("TV app has to be connected to proceed");
    }
    categoryRepository.saveAndFlush(category);
    final LastCategory result = new LastCategory(category);
    final String payload =
        "{\"type\":\"album_picked\",\"selected\":" + objectMapper.writeValueAsString(result) + "}";
    TransactionCallbacks.afterCommitOrNow(() -> broadcastGateway.toTv(roomCode, payload));
    return result;
  }

  @Override
  @Transactional(rollbackOn = DerivedException.class)
  public void startCategory(final UUID categoryId, final String roomCode) throws DerivedException {
    RoomLocks.tryLock(gameRepository, roomCode);
    final Optional<CategoryEntity> maybeCategory = categoryRepository.findById(categoryId);
    if (maybeCategory.isEmpty()) {
      throw new InvalidReferencedObjectException(
          "Category with with id " + categoryId + " does not exist");
    }
    final CategoryEntity category = maybeCategory.get();
    if (!category.getGameId().getCode().equals(roomCode)) {
      throw new InvalidArgumentException(
          "Room code " + roomCode + " isn't consistent with the category");
    }
    final GameEntity game = gameService.isChangeStageLegal(2, roomCode);
    final int maxSongs = game.getMaxSongs();
    final List<TrackEntity> trackList = category.getAlbumId().getTrackList();
    if (trackList.size() < maxSongs) {
      /* Getting here means that during creationg admin added
      a category that doesn't meet the requirements.
      Preparation tool should make this impossible. */
      throw new InvalidArgumentException(
          "The category (len:"
              + trackList.size()
              + ") doesn't have enough songs ("
              + maxSongs
              + ")");
    }
    /* Pick songs for this category and persist their schedules.
    All timestamps start as null; the first song is started immediately (startedAt = now()).
    TODO: Improve selection logic (prefer songs not already used in other categories). */
    Collections.shuffle(trackList);
    final AtomicInteger index = new AtomicInteger(0);
    final List<ScheduleEntity> schedule =
        trackList.subList(0, Math.min(maxSongs, trackList.size())).stream()
            .map(elem -> new ScheduleEntity(category, elem, index.incrementAndGet()))
            .toList();
    schedule.getFirst().setStartedAt(LocalDateTime.now());
    scheduleRepository.saveAllAndFlush(schedule);
    LOG.info("Starting category {}", categoryId);
    gameService.changeStage(2, roomCode); // Not-optimal. TODO: Broadcast only what you need.
  }

  @Override
  @Modifying
  public int finishAndNext(final GameEntity game) throws DerivedException {
    /* Called from ScheduleService.progress while that transaction already owns the game row lock. */
    final LastCategory lastCategoryDto = categoryRepository.findLastCategory(game.getId());
    // Request validation (fail fast on invalid input).
    final Optional<CategoryEntity> maybeCategory =
        categoryRepository.findById(lastCategoryDto.getCategoryId());
    if (maybeCategory.isEmpty()) {
      throw new InvalidReferencedObjectException(
          "Category with with id " + lastCategoryDto.getCategoryId() + " does not exist");
    }
    final CategoryEntity lastCategory = maybeCategory.get();
    lastCategory.setDone(true);
    categoryRepository.saveAndFlush(lastCategory);
    final int newState = lastCategory.getOrdinalNumber() == game.getMaxAlbums() ? 3 : 1;
    LOG.info("Finished category {}. Now transitioning to {}", lastCategory.getId(), newState);
    return newState;
  }
}
