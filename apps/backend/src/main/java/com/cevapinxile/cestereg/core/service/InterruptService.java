/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles team and system interruptions and computes the effective playback position.
 *
 * <p>Team/system overlap restrictions remain gameplay rules, while playback seek safely
 * subtracts the union of all interruptions and TV-registered loading intervals.
 *
 * @author denijal
 */
public interface InterruptService {
  /**
   * Computes effective playback time since {@code startTimestamp}, excluding interruptions
   * and registered TV loading intervals.
   *
   * <p>Uses the union of interrupt and TV-loading intervals, including open intervals.
   *
   * @param startTimestamp timestamp when snippet playback started
   * @param scheduleId schedule identifier for the current song
   * @return effective playback duration in seconds
   */
  double calculateSeek(LocalDateTime startTimestamp, UUID scheduleId);

  InterruptEntity[] getLastTwoInterrupts(LocalDateTime startTimestamp, UUID scheduleId);

  /**
   * Creates a team interrupt (buzz-in pause) for the currently active song in the given room.
   *
   * <p>Partial overlaps remain forbidden by the gameplay rules; seek computation is
   * independently overlap-safe.
   *
   * <p><b>Forbidden example (partial overlap):</b> TV disconnect interrupt active → team buzz
   * interrupt starts → TV interrupt resolved → team interrupt still active. The gameplay
   * prohibition still applies even though the seek calculator safely merges overlaps.
   *
   * @param roomCode room identifier
   * @param teamId team that buzzed in
   * @throws DerivedException if the room/schedule is invalid or if the interrupt would violate the
   *     overlap invariant
   */
  void interrupt(String roomCode, UUID teamId) throws DerivedException;

  void resolveErrors(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;

  void answer(UUID answerId, AnswerRequest ar, String roomCode) throws DerivedException;

  UUID findCorrectAnswer(UUID scheduleId, String roomCode) throws DerivedException;

  /**
   * Persists the admin UI scenario so the client can restore its state after a system pause/error.
   *
   * <p><b>Implementation note:</b> {@code InterruptEntity.scoreOrScenarioId} is overloaded: for
   * TEAM interrupts it stores awarded score (e.g., +30 / -10), while for SYSTEM/PAUSE interrupts it
   * stores {@code previousScenarioId} for UI restoration. Interpretation depends on the interrupt
   * type.
   *
   * @param scenarioId admin UI scenario (allowed range 0..4, excluding 3)
   * @param roomCode room identifier
   * @throws DerivedException if the scenario is invalid or there is no suitable interrupt to attach
   *     it to
   */
  void savePreviousScenario(int scenarioId, String roomCode) throws DerivedException;
}
