/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface CategoryService {

    public LastCategory pickAlbum(UUID categoryId, TeamIdRequest par, String roomCode) throws DerivedException;

    public void startCategory(UUID categoryId, String roomCode) throws DerivedException;

    public int finishAndNext(GameEntity game)  throws DerivedException ;
    
}
