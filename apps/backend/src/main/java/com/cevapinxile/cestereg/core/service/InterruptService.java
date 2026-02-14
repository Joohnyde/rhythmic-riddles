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
 */
public interface InterruptService {
    
    public long calculateSeek(LocalDateTime startTimestamp, UUID scheduleId);
    
    public InterruptEntity[] getLastTwoInterrupts(LocalDateTime startTimestamp, UUID scheduleId);
    
    public void interrupt(String roomCode, UUID teamId) throws DerivedException;

    public void resolveErrors(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;

    public void answer(UUID answerId, AnswerRequest ar, String roomCode) throws DerivedException;

    public UUID findCorrectAnswer(UUID scheduleId, String roomCode) throws DerivedException;

    public void savePreviousScenario(int scenarioId, String roomCode) throws DerivedException;
    
}
