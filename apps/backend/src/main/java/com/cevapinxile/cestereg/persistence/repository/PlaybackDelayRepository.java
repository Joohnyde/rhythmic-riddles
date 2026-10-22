package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.persistence.entity.PlaybackDelayEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaybackDelayRepository extends JpaRepository<PlaybackDelayEntity, UUID> {

  PlaybackDelayEntity findOpenBySchedule(UUID scheduleId);

  /**
   * Returns playback attempts in chronological order, including an unfinished attempt.
   *
   * @param scheduleId the schedule identifier
   * @return playback attempts for the schedule
   */
  List<PlaybackDelayEntity> findBySchedule(UUID scheduleId);
}
