/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 *
 * @author denijal
 * Manages interruptions of song playback (team answers and system pauses) and derives effective
 * playback position ("seek") from persisted interrupt frames.
 *
 * <p><b>Critical invariant:</b> interrupt intervals must be either disjoint (A ends, then B starts)
 * or fully nested (B completely inside A). Partially overlapping interrupts are forbidden because
 * seek calculation subtracts only outermost frames; partial overlaps make paused time ambiguous
 * (overlap would be under-counted or double-counted).</p>
 *
 * <p>State derived here is used to reconstruct stage 2 UI after reconnects.</p>
 */
public interface InterruptService {
    /**
    * Computes effective playback time since {@code startTimestamp}, excluding time spent paused in interrupts.
    *
    * <p>Implementation uses only the outermost interrupt frames to avoid double-counting nested pauses.
    * This is correct under the invariant that interrupts are disjoint or nested (no partial overlap).</p>
    *
    * @param startTimestamp timestamp when snippet playback started
    * @param scheduleId schedule identifier for the current song
    * @return effective playback duration in milliseconds
    */
    public long calculateSeek(LocalDateTime startTimestamp, UUID scheduleId);
    
    public InterruptEntity[] getLastTwoInterrupts(LocalDateTime startTimestamp, UUID scheduleId);
    
    /**
    * Creates a team interrupt (buzz-in pause) for the currently active song in the given room.
    *
    * <p>Interrupts may be disjoint or nested. Partial overlaps are forbidden because seek computation
    * assumes paused time can be represented by outermost interrupt frames.</p>
    *
    * <p><b>Forbidden example (partial overlap):</b>
    * TV disconnect interrupt active → team buzz interrupt starts → TV interrupt resolved → team interrupt still active.
    * This would create a partial overlap and make seek ambiguous.</p>
    *
    * @param roomCode room identifier
    * @param teamId team that buzzed in
    * @throws DerivedException if the room/schedule is invalid or if the interrupt would violate the overlap invariant
    */
    public void interrupt(String roomCode, UUID teamId) throws DerivedException;

    public void resolveErrors(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;

    public void answer(UUID answerId, AnswerRequest ar, String roomCode) throws DerivedException;

    public UUID findCorrectAnswer(UUID scheduleId, String roomCode) throws DerivedException;

    /**
    * Persists the admin UI scenario so the client can restore its state after a system pause/error.
    *
    * <p><b>Implementation note:</b> {@code InterruptEntity.scoreOrScenarioId} is overloaded:
    * for TEAM interrupts it stores awarded score (e.g., +30 / -10),
    * while for SYSTEM/PAUSE interrupts it stores {@code previousScenarioId} for UI restoration.
    * Interpretation depends on the interrupt type.</p>
    *
    * @param scenarioId admin UI scenario (allowed range 0..4, excluding 3)
    * @param roomCode room identifier
    * @throws DerivedException if the scenario is invalid or there is no suitable interrupt to attach it to
    */
    public void savePreviousScenario(int scenarioId, String roomCode) throws DerivedException;
    
}
