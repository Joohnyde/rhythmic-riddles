package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.api.quiz.dto.response.PlaybackRegistrationResponse;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/** Tracks TV playback start acknowledgements for a single schedule attempt. */
public interface PlaybackDelayService {

  /**
   * Registers the latest TV attempt; retries reuse their delay and older requests are rejected.
   *
   * @param scheduleId the schedule identifier
   * @param roomCode the room code
   * @param clientAttemptAt TV-generated Unix millisecond timestamp
   * @return the persisted delay ID and echoed client timestamp
   * @throws DerivedException if the delay cannot be registered
   */
  PlaybackRegistrationResponse registerPlaybackDelay(
      UUID scheduleId, String roomCode, Long clientAttemptAt) throws DerivedException;

  /**
   * Records that the TV audio clock has measurably advanced after playback starts.
   *
   * @param delayId the playback delay identifier
   * @param roomCode the room code
   * @param clientAttemptAt original TV attempt timestamp
   * @throws DerivedException if the delay cannot be acknowledged
   */
  void acknowledgePlaybackDelay(UUID delayId, String roomCode, Long clientAttemptAt)
      throws DerivedException;
}
