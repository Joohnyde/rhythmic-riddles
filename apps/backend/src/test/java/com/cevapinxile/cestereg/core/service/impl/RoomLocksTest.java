package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.cevapinxile.cestereg.common.exception.RoomBusyException;
import com.cevapinxile.cestereg.core.service.support.RoomLocks;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class RoomLocksTest {

  @Mock private GameRepository gameRepository;

  @Test
  void delegatesBlockingLockToRepository() {
    RoomLocks.lock(gameRepository, "AKKU");

    verify(gameRepository).lockGame("AKKU");
  }

  @Test
  void delegatesWhenRoomLockIsAvailable() throws Exception {
    RoomLocks.tryLock(gameRepository, "AKKU");

    verify(gameRepository).tryLockGame("AKKU");
  }

  @Test
  void mapsNowaitContentionToMeaningfulRoomBusyException() throws Exception {
    doThrow(new CannotAcquireLockException("busy")).when(gameRepository).tryLockGame("AKKU");

    final RoomBusyException exception =
        assertThrows(RoomBusyException.class, () -> RoomLocks.tryLock(gameRepository, "AKKU"));

    assertEquals("Another request is already changing game AKKU.", exception.getMessage());
    assertEquals(
        "{\"error\":\"E010 - Room busy\", \"message\":\"Another request is already changing game AKKU.\"}",
        exception.toString());
  }
}
