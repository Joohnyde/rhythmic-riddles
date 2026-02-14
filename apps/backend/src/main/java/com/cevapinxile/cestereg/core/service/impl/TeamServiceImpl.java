/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.MissingArgumentException;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreCache;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;

/**
 *
 * @author denijal
 */
@Service
public class TeamServiceImpl implements TeamService {

    @Autowired
    private BroadcastGateway broadcastGateway;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GameRepository gameRepository;

    /* In-memory score cache to avoid hitting the DB on every scoreboard refresh.
       Assumes single-instance runtime and must be kept consistent with DB updates. */
    private final HashMap<String, TeamScoreCache> scoreMap = new HashMap<>();

    @Override
    public CreateTeamResponse createTeam(CreateTeamRequest ctr, String roomCode) throws DerivedException {
        // Request validation (fail fast on invalid input).
        if (StringUtils.isBlank(ctr.name()) || StringUtils.isBlank(ctr.image())) {
            throw new MissingArgumentException("The request's body is missing a name and/or a picture");
        }
        
        GameEntity game = gameRepository.findByCode(roomCode, 0);
        TeamEntity team = new TeamEntity(ctr, game.getId());
        teamRepository.saveAndFlush(team);

        CreateTeamResponse response = new CreateTeamResponse(team);
        broadcastGateway.toTv(roomCode, "{\"type\":\"new_team\",\"team\":" + new ObjectMapper().writeValueAsString(response) + "}");
        return response;
    }

    @Override
    public void kickTeam(String teamId, String roomCode) throws DerivedException {
        // Request validation (fail fast on invalid input).
        GameEntity game = gameRepository.findByCode(roomCode, 0);
        Optional<TeamEntity> maybeTeam = teamRepository.findById(UUID.fromString(teamId));
        if (maybeTeam.isEmpty()) {
            throw new InvalidReferencedObjectException("Team with id " + teamId + " does not exist");
        }

        TeamEntity team = maybeTeam.get();
        teamRepository.delete(team);
        teamRepository.flush();

        broadcastGateway.toTv(roomCode, "{\"type\":\"kick_team\",\"uuid\":\"" + team.getId() + "\"}");
    }

    private void addToCache(String roomCode) throws InvalidReferencedObjectException {
        if (!scoreMap.containsKey(roomCode)) {
            List<TeamScoreProjection> teamScores = teamRepository.getTeamScores(roomCode);
            if (teamScores == null) {
                throw new InvalidReferencedObjectException("Game with code " + roomCode + " could not be found");
            }
            TeamScoreCache teamScoreCache = new TeamScoreCache(teamScores);
            scoreMap.put(roomCode, teamScoreCache);
        }
    }

    @Override
    public Object getTeamScores(String roomCode) throws DerivedException {
        addToCache(roomCode);
        return scoreMap.get(roomCode).getScores();
    }

    @Override
    public Integer getTeamPoints(UUID teamId, String roomCode) throws DerivedException {
        addToCache(roomCode);
        return scoreMap.get(roomCode).getScore(teamId);
    }

    @Override
    public void saveTeamAnswer(UUID teamId, UUID scheduleId, Integer score, String roomCode) throws DerivedException {
        addToCache(roomCode);
        scoreMap.get(roomCode).setScore(teamId, scheduleId, score);
    }

    @Override
    public List<CreateTeamResponse> findByRoomCode(String roomCode) {
        return teamRepository.findByGameId(roomCode);
    }

    @Override
    public ChoosingTeam findNextChoosingTeam(UUID gameId, int maxAlbums) {
        return teamRepository.findNext(gameId, maxAlbums);
    }

    @Override
    public Optional<TeamEntity> findById(UUID teamId) {
        return teamRepository.findById(teamId);
    }
}
