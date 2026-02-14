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

/**
 *
 * @author denijal
 */
public interface InterruptRepository extends JpaRepository<InterruptEntity, UUID> {
    
    /* We select only OUTERMOST interrupt frames after startTimestamp.
       Rationale: nested interrupts can occur (e.g., TV disconnect inside a team-answer pause),
       but subtracting both would double-count paused time.
       Outermost frames cover the entire paused period and keep seek calculation correct.
       Assumption: interrupts are never partially overlapping (must be disjoint or nested). */
    public List<InterruptFrame> findInterrupts(LocalDateTime startTimestamp, UUID scheduleId);
    
    public InterruptEntity findLastPause(LocalDateTime startTimestamp, UUID scheduleId);
    
    public InterruptEntity findLastAnswer(LocalDateTime startTimestamp, UUID scheduleId);

    public Optional<Boolean> didTeamAnswer(UUID teamId);
    
    @Modifying
    public void resolveErrors(UUID scheduleId, LocalDateTime resolvedAt);

    public UUID findCorrectAnswer(UUID scheduleId);
    
    public Integer findPreviousScenarioId(UUID scheduleId);
    
}
