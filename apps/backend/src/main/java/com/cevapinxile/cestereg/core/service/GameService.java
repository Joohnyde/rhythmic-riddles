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
 *
 * @author denijal
 */
public interface GameService {

    public String createGame(CreateGameRequest cgr) throws DerivedException;

    public HashMap<String, Object> contextFetch(String roomCode) throws DerivedException;

    public GameEntity isChangeStageLegal(int newStage, String roomCode) throws DerivedException;

    public void changeStage(int stageId, String roomCode) throws DerivedException;

    public int getStage(String roomCode);

    public GameEntity findByCode(String roomCode, Integer stageId) throws DerivedException;

}
