package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/** Tracks TV playback start acknowledgements for a single schedule attempt. */
public interface PlaybackDelayService {

  /** Registers a pending TV attempt; duplicate requests reuse the current open delay. */
  UUID registerPlaybackDelay(UUID scheduleId, String roomCode) throws DerivedException;

  /** Records that the TV audio clock has measurably advanced after playback starts. */
  void acknowledgePlaybackDelay(UUID delayId, String roomCode) throws DerivedException;
}
