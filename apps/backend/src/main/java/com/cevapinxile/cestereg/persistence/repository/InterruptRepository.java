/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/*
 * @author denijal
 */
public interface InterruptRepository extends JpaRepository<InterruptEntity, UUID> {

  /**
   * Returns outermost interrupt frames after {@code startTimestamp} for the given schedule.
   *
   * <p>"Outermost" means the returned intervals are not strictly contained by another interrupt
   * interval that starts earlier (or equal) and resolves later (or is unresolved). This allows seek
   * computation to subtract paused time without double-counting nested pauses.
   *
   * <p><b>Requires invariant:</b> interrupt intervals are disjoint or fully nested (no partial
   * overlap).
   *
   * @param startTimestamp snippet start time; only interrupts after this timestamp are considered
   * @param scheduleId schedule identifier
   * @return list of outermost interrupt frames ordered by arrival time ascending
   */
  List<InterruptFrame> findInterrupts(LocalDateTime startTimestamp, UUID scheduleId);

  /**
   * Returns the latest system interrupt/pause affecting the given schedule since {@code
   * startTimestamp}.
   *
   * <p>Typically used to attach auxiliary metadata to the most recent pause (e.g., UI restoration
   * scenario).
   *
   * @param startTimestamp lower bound timestamp (usually song start)
   * @param scheduleId schedule identifier
   * @return latest pause interrupt or {@code null} if none exists
   */
  InterruptEntity findLastPause(LocalDateTime startTimestamp, UUID scheduleId);

  /**
   * Returns the latest team caused interrupt/buzz-in for the given schedule since {@code
   * startTimestamp}.
   *
   * <p>It's main usage is to calculate if the game is stopped because someone is answering and to
   * properly show that team on the UI.
   *
   * @param startTimestamp lower bound timestamp (usually song start)
   * @param scheduleId schedule identifier
   * @return latest pause interrupt or {@code null} if none exists
   */
  InterruptEntity findLastAnswer(LocalDateTime startTimestamp, UUID scheduleId);

  Optional<Boolean> didTeamAnswer(UUID teamId);

  @Modifying
  void resolveErrors(UUID scheduleId, LocalDateTime resolvedAt);

  UUID findCorrectAnswer(UUID scheduleId);

  Integer findPreviousScenarioId(UUID scheduleId);
}
