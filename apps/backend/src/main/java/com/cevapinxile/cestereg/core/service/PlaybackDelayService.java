package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/** Tracks TV playback start acknowledgements for a single schedule attempt. */
public interface PlaybackDelayService {

  /**
   * Registers a pending TV attempt; duplicate requests reuse the current open delay.
   *
   * @param scheduleId the schedule identifier
   * @param roomCode the room code
   * @return the identifier of the pending playback delay
   * @throws DerivedException if the delay cannot be registered
   */
  UUID registerPlaybackDelay(UUID scheduleId, String roomCode) throws DerivedException;

  /**
   * Records that the TV audio clock has measurably advanced after playback starts.
   *
   * @param delayId the playback delay identifier
   * @param roomCode the room code
   * @throws DerivedException if the delay cannot be acknowledged
   */
  void acknowledgePlaybackDelay(UUID delayId, String roomCode) throws DerivedException;
}
