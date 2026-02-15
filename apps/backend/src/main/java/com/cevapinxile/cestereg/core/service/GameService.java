/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateGameRequest;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import java.util.HashMap;

/**
 * @author denijal
 * Game runtime service responsible for building frontend context payloads and applying
 * stage-based transitions for the quiz.
 *
 * <p>The service produces payloads that allow clients to render correct UI state after reconnects.
 * Stage logic is derived from persisted timestamps (schedule start/reveal) and interrupt frames.</p>
 */
public interface GameService {

    public String createGame(CreateGameRequest cgr) throws DerivedException;

    /**
    * Builds the runtime context payload for the given room and stage.
    *
    * <p>Stage 2 is reconstruction-heavy: it derives the current song playback state from persisted timestamps
    * and interrupt frames so clients can recover after refreshes or disconnects without relying on in-memory state.</p>
    *
    * <p>The returned payload includes mandatory fields expected by the frontend in all scenarios:
    * {@code songId}, {@code question}, {@code answer}, {@code scheduleId}, {@code answerDuration}.</p>
    *
    * @param roomCode room identifier
    * @return context payload consumable by both admin and TV clients
    * @throws DerivedException if the room does not exist or the persisted state is inconsistent
    */
    public HashMap<String, Object> contextFetch(String roomCode) throws DerivedException;

    public GameEntity isChangeStageLegal(int newStage, String roomCode) throws DerivedException;

    public void changeStage(int stageId, String roomCode) throws DerivedException;

    public int getStage(String roomCode);

    public GameEntity findByCode(String roomCode, Integer stageId) throws DerivedException;

}
