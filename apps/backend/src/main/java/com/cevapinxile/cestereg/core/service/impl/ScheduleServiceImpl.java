/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.core.service.support.RoomLocks;
import com.cevapinxile.cestereg.core.service.support.TransactionCallbacks;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/*
 * @author denijal
 */
@Service
public class ScheduleServiceImpl implements ScheduleService {
  private static final Logger LOG = LoggerFactory.getLogger(ScheduleServiceImpl.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private GameService gameService;

  @Autowired private GameRepository gameRepository;

  @Autowired private CategoryService categoryService;

  @Autowired private ScheduleRepository scheduleRepository;

  @Autowired private InterruptRepository interruptRepository;
  @Autowired private PresenceGateway presenceGateway;
  @Autowired private BroadcastGateway broadcastGateway;

  @Override
  @Transactional(rollbackOn = DerivedException.class)
  public void replaySong(final UUID lastPlayedScheduleId, final String roomCode)
      throws DerivedException {
    RoomLocks.tryLock(gameRepository, roomCode);
    final GameEntity game = gameService.findByCode(roomCode, 2);
    final ScheduleEntity lastPlayedSong = currentSchedule(game, lastPlayedScheduleId, roomCode);
    if (!presenceGateway.areBothPresent(roomCode)) {
      throw new AppNotRegisteredException("Both apps need to be present in order to continue");
    }
    final LocalDateTime now = LocalDateTime.now();
    interruptRepository.resolveErrors(lastPlayedScheduleId, now);
    lastPlayedSong.setStartedAt(now);
    scheduleRepository.saveAndFlush(lastPlayedSong);
    LOG.info("Replaying schedule {}", lastPlayedScheduleId);
    final String payload =
        "{\"type\":\"song_repeat\",\"remaining\":"
            + lastPlayedSong.getTrackId().getSongId().getSnippetDuration()
            + "}";
    TransactionCallbacks.afterCommitOrNow(() -> broadcastGateway.broadcast(roomCode, payload));
  }

  @Override
  @Transactional(rollbackOn = DerivedException.class)
  public void revealAnswer(final UUID lastPlayedScheduleId, final String roomCode)
      throws DerivedException {
    RoomLocks.tryLock(gameRepository, roomCode);
    final GameEntity game = gameService.findByCode(roomCode, 2);
    final ScheduleEntity lastPlayedSong = currentSchedule(game, lastPlayedScheduleId, roomCode);
    if (!presenceGateway.areBothPresent(roomCode)) {
      throw new AppNotRegisteredException("Both apps need to be present in order to continue");
    }
    final LocalDateTime now = LocalDateTime.now();
    interruptRepository.resolveErrors(lastPlayedScheduleId, now);
    lastPlayedSong.setRevealedAt(now);
    scheduleRepository.saveAndFlush(lastPlayedSong);
    LOG.info("Revealing schedule {}", lastPlayedScheduleId);
    TransactionCallbacks.afterCommitOrNow(
        () -> broadcastGateway.broadcast(roomCode, "{\"type\":\"song_reveal\"}"));
  }

  @Override
  /* Advances the game: resolve any pending errors from the previous song, then either
  start the next scheduled song or, if none remain, finish the album and move to the next stage. */
  @Transactional(rollbackOn = DerivedException.class)
  public void progress(String roomCode) throws DerivedException {
    RoomLocks.tryLock(gameRepository, roomCode);
    final GameEntity game = gameService.findByCode(roomCode, 2);
    if (!presenceGateway.areBothPresent(roomCode)) {
      throw new AppNotRegisteredException("Both apps need to be present in order to continue");
    }
    final ScheduleEntity lastSongPlayed = scheduleRepository.findLastPlayed(game.getId());
    final LocalDateTime now = LocalDateTime.now();
    // Any unresolved "pause/error" state must be closed before starting/revealing/advancing.
    interruptRepository.resolveErrors(lastSongPlayed.getId(), now);
    final HashMap<String, Object> json = new HashMap<>();
    final Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findNext(game.getId());
    if (maybeSchedule.isPresent()) {
      final ScheduleEntity nextSong = maybeSchedule.get();
      nextSong.setStartedAt(LocalDateTime.now());
      scheduleRepository.saveAndFlush(nextSong);
      GameServiceImpl.putDefaultFields(nextSong, json);
      json.put(
          "remaining",
          nextSong.getTrackId().getSongId().getSnippetDuration()); // Najvjerovatnije ne treba!
      json.put("type", "song_next");
      final String payload = objectMapper.writeValueAsString(json);
      TransactionCallbacks.afterCommitOrNow(() -> broadcastGateway.broadcast(roomCode, payload));
      return;
    }

    final int nextState = categoryService.finishAndNext(game);
    gameService.changeStage(nextState, roomCode);
  }

  private ScheduleEntity currentSchedule(
      final GameEntity game, final UUID requestedScheduleId, final String roomCode)
      throws InvalidArgumentException {
    final ScheduleEntity current = scheduleRepository.findLastPlayed(game.getId());
    if (current == null || !current.getId().equals(requestedScheduleId)) {
      throw new InvalidArgumentException(
          "Schedule " + requestedScheduleId + " is not the current schedule for game " + roomCode);
    }
    return current;
  }
}
