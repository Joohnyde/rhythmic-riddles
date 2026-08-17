package com.cevapinxile.cestereg.core.service.support;

import com.cevapinxile.cestereg.common.exception.RoomBusyException;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import org.springframework.dao.PessimisticLockingFailureException;

/** Shared helpers for blocking and fail-fast room-row locking. */
public final class RoomLocks {

  private RoomLocks() {}

  /**
   * Waits until the room row can be locked. Use only for mutations that must not be dropped.
   *
   * @param gameRepository repository used to acquire the database row lock
   * @param roomCode unique room identifier
   */
  public static void lock(final GameRepository gameRepository, final String roomCode) {
    gameRepository.lockGame(roomCode);
  }

  /**
   * Fails fast when another transaction already owns the room row.
   *
   * @param gameRepository repository used to acquire the database row lock
   * @param roomCode unique room identifier
   * @throws com.cevapinxile.cestereg.common.exception.RoomBusyException
   */
  public static void tryLock(final GameRepository gameRepository, final String roomCode)
      throws RoomBusyException {
    try {
      gameRepository.tryLockGame(roomCode);
    } catch (final PessimisticLockingFailureException exception) {
      throw new RoomBusyException("Another request is already changing game " + roomCode + ".");
    }
  }
}
