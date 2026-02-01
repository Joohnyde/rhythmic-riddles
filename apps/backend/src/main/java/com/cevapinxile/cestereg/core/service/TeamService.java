/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author denijal
 * Team-related operations for the quiz runtime (lookup, scoring, and turn/rotation support).
 *
 * <p>Any in-memory caching of scores assumes a single backend instance unless explicitly coordinated.
 * The database remains the source of truth.</p>
 */

public interface TeamService {
    
    public CreateTeamResponse createTeam(CreateTeamRequest ctr, String roomCode) throws DerivedException;
    
    public void kickTeam(String teamId, String roomCode) throws DerivedException;
    
    // This should return List<TeamScore> but that would expose non-public type TeamScore through public API
    public Object getTeamScores(String roomCode) throws DerivedException;
    
    public Integer getTeamPoints(UUID teamId, String roomCode) throws DerivedException;
    
    public void saveTeamAnswer(UUID teamId, UUID scheduleId, Integer score, String roomCode) throws DerivedException;

    public List<CreateTeamResponse> findByRoomCode(String roomCode);

    public ChoosingTeam findNextChoosingTeam(UUID gameId, int maxAlbums);

    public Optional<TeamEntity> findById(UUID teamId);
}
