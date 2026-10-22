package com.cevapinxile.cestereg.core.service.impl;

import static com.cevapinxile.cestereg.core.service.support.ScheduleOwnership.validate;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.core.service.PlaybackDelayService;
import com.cevapinxile.cestereg.core.service.support.RoomLocks;
import com.cevapinxile.cestereg.core.service.support.TransactionCallbacks;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.PlaybackDelayEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.PlaybackDelayRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/*
 * @author denijal
 */
@Service
public class PlaybackDelayServiceImpl implements PlaybackDelayService {

  private static final Logger LOG = LoggerFactory.getLogger(PlaybackDelayServiceImpl.class);

  @Autowired private PlaybackDelayRepository playbackDelayRepository;
  @Autowired private ScheduleRepository scheduleRepository;
  @Autowired private GameRepository gameRepository;
  @Autowired private BroadcastGateway broadcastGateway;
  @Autowired private PresenceGateway presenceGateway;
  @Autowired(required = false) private Clock clock = Clock.systemDefaultZone();

  @Override
  @Transactional(rollbackOn = DerivedException.class)
  public UUID registerPlaybackDelay(final UUID scheduleId, final String roomCode)
      throws DerivedException {
    validateArguments(scheduleId, roomCode);
    // Serialize registration, replays and acknowledgements against the room row.
    RoomLocks.lock(gameRepository, roomCode);
    final GameEntity game = gameRepository.findByCode(roomCode, 2);
    requireBothApps(roomCode);
    final ScheduleEntity schedule = scheduleRepository.findById(scheduleId)
        .orElseThrow(() -> new InvalidReferencedObjectException(
            "Schedule with id " + scheduleId + " does not exist"));
    validateCurrentSchedule(schedule, game, roomCode);

    final PlaybackDelayEntity open = playbackDelayRepository.findOpenBySchedule(scheduleId);
    if (open != null) {
      if (!open.getSentAt().isBefore(schedule.getStartedAt())) {
        return open.getId();
      }
      // A previous replay never acknowledged; it must not block a new attempt.
      playbackDelayRepository.delete(open);
    }

    final PlaybackDelayEntity delay = new PlaybackDelayEntity(schedule, LocalDateTime.now(clock));
    playbackDelayRepository.saveAndFlush(delay);
    LOG.debug("Playback attempt {} registered for schedule {}", delay.getId(), scheduleId);
    return delay.getId();
  }

  @Override
  @Transactional(rollbackOn = DerivedException.class)
  public void acknowledgePlaybackDelay(final UUID delayId, final String roomCode)
      throws DerivedException {
    validateArguments(delayId, roomCode);
    RoomLocks.lock(gameRepository, roomCode);
    final GameEntity game = gameRepository.findByCode(roomCode, 2);
    requireBothApps(roomCode);
    final PlaybackDelayEntity delay = findCurrentDelay(delayId, game, roomCode);
    if (delay.getAcknowledgedAt() != null) {
      // A repeated HTTP request must not restart the Admin timer.
      return;
    }

    final LocalDateTime now = LocalDateTime.now(clock);
    delay.setAcknowledgedAt(now);
    playbackDelayRepository.saveAndFlush(delay);
    LOG.info("TV playback acknowledged for schedule {}", delay.getScheduleId().getId());
    final String payload = "{\"type\":\"play\",\"scheduleId\":\""
        + delay.getScheduleId().getId() + "\"}";
    TransactionCallbacks.afterCommitOrNow(() -> broadcastGateway.toAdmin(roomCode, payload));
  }

  private PlaybackDelayEntity findCurrentDelay(final UUID delayId, final GameEntity game,
      final String roomCode) throws DerivedException {
    final PlaybackDelayEntity delay = playbackDelayRepository.findById(delayId)
        .orElseThrow(() -> new InvalidReferencedObjectException(
            "Playback attempt with id " + delayId + " does not exist"));
    final ScheduleEntity schedule = delay.getScheduleId();
    validateCurrentSchedule(schedule, game, roomCode);
    if (delay.getSentAt().isBefore(schedule.getStartedAt())) {
      throw new WrongGameStateException("Playback acknowledgement belongs to an earlier replay");
    }
    return delay;
  }

  private void validateCurrentSchedule(final ScheduleEntity schedule, final GameEntity game,
      final String roomCode) throws DerivedException {
    validate(schedule, game, roomCode);
    final ScheduleEntity current = scheduleRepository.findLastPlayed(game.getId());
    if (schedule.getStartedAt() == null || current == null
        || !schedule.getId().equals(current.getId()) || schedule.getRevealedAt() != null) {
      throw new WrongGameStateException("Schedule is not the current unrevealed song");
    }
  }

  private void requireBothApps(final String roomCode) throws AppNotRegisteredException {
    if (!presenceGateway.areBothPresent(roomCode)) {
      throw new AppNotRegisteredException("Both apps need to be present in order to continue");
    }
  }

  private static void validateArguments(final UUID id, final String roomCode)
      throws InvalidArgumentException {
    if (id == null || roomCode == null || roomCode.isBlank()) {
      throw new InvalidArgumentException("Room code and identifier are required");
    }
  }
}
