package com.cevapinxile.cestereg.core.service.support;

import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;

/** Validates that a schedule belongs to the room addressed by a request. */
public final class ScheduleOwnership {

  private ScheduleOwnership() {}

  /**
   * Rejects missing associations as well as schedules from another game.
   *
   * @param schedule the schedule to validate
   * @param game the expected game
   * @param roomCode the room code used in the error message
   * @throws InvalidArgumentException if the schedule does not belong to the game
   */
  public static void validate(
      final ScheduleEntity schedule, final GameEntity game, final String roomCode)
      throws InvalidArgumentException {
    if (schedule == null
        || schedule.getCategoryId() == null
        || schedule.getCategoryId().getGameId() == null
        || game == null
        || !game.getId().equals(schedule.getCategoryId().getGameId().getId())) {
      throw new InvalidArgumentException("Schedule does not belong to game " + roomCode);
    }
  }
}
