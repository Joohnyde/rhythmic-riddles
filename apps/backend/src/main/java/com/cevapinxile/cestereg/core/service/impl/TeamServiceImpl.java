/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreCache;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.MissingArgumentException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
/*
 * @author denijal
 */
@Service
public class TeamServiceImpl implements TeamService {

  @Autowired private BroadcastGateway broadcastGateway;

  @Autowired private TeamRepository teamRepository;

  @Autowired private GameRepository gameRepository;

  /* In-memory score cache to avoid hitting the DB on every scoreboard refresh.
  Assumes single-instance runtime and must be kept consistent with DB updates. */
  private final Map<String, TeamScoreCache> scoreMap = new ConcurrentHashMap<>();
  @Override
  public CreateTeamResponse createTeam(final CreateTeamRequest ctr, final String roomCode)
      throws DerivedException {
    // Request validation (fail fast on invalid input).
    if (StringUtils.isBlank(ctr.name()) || StringUtils.isBlank(ctr.image())) {
      throw new MissingArgumentException("The request's body is missing a name and/or a picture");
    }
    final GameEntity game = gameRepository.findByCode(roomCode, 0);
    final TeamEntity team = new TeamEntity(ctr, game.getId());
    teamRepository.saveAndFlush(team);
    final CreateTeamResponse response = new CreateTeamResponse(team);
    broadcastGateway.toTv(
        roomCode,
        "{\"type\":\"new_team\",\"team\":" + new ObjectMapper().writeValueAsString(response) + "}");
    return response;
  }
  @Override
  public void kickTeam(final String teamId, final String roomCode) throws DerivedException {
    // Request validation (fail fast on invalid input).
    final GameEntity game = gameRepository.findByCode(roomCode, 0);
    final Optional<TeamEntity> maybeTeam = teamRepository.findById(UUID.fromString(teamId));
    if (maybeTeam.isEmpty()) {
      throw new InvalidReferencedObjectException("Team with id " + teamId + " does not exist");
    }
    final TeamEntity team = maybeTeam.get();
    if (team.getGameId() == null || !game.getId().equals(team.getGameId().getId())) {
      throw new InvalidArgumentException(
          "Room code " + roomCode + " isn't consistent with team " + teamId);
    }
    teamRepository.delete(team);
    teamRepository.flush();
    broadcastGateway.toTv(roomCode, "{\"type\":\"kick_team\",\"uuid\":\"" + team.getId() + "\"}");
  }
  private TeamScoreCache addToCache(final String roomCode)
      throws InvalidReferencedObjectException {
    final TeamScoreCache cached = scoreMap.get(roomCode);
    if (cached != null) {
      return cached;
    }

    final List<TeamScoreProjection> teamScores = teamRepository.getTeamScores(roomCode);
    if (teamScores == null) {
      throw new InvalidReferencedObjectException(
          "Game with code " + roomCode + " could not be found");
    }
    final TeamScoreCache loaded = new TeamScoreCache(teamScores);
    final TeamScoreCache existing = scoreMap.putIfAbsent(roomCode, loaded);
    return existing == null ? loaded : existing;
  }

  @Override
  public Object getTeamScores(final String roomCode) throws DerivedException {
    return addToCache(roomCode).getScores();
  }

  @Override
  public Integer getTeamPoints(final UUID teamId, final String roomCode) throws DerivedException {
    return addToCache(roomCode).getScore(teamId);
  }

  @Override
  public void saveTeamAnswer(
      final UUID teamId, final UUID scheduleId, final Integer score, final String roomCode)
      throws DerivedException {
    final TeamScoreCache cache = addToCache(roomCode);
    cache.getScore(teamId); // validate before registering an after-commit callback

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      setCachedScore(cache, teamId, scheduleId, score);
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            setCachedScore(cache, teamId, scheduleId, score);
          }
        });
  }

  private void setCachedScore(
      final TeamScoreCache cache,
      final UUID teamId,
      final UUID scheduleId,
      final Integer score) {
    try {
      cache.setScore(teamId, scheduleId, score);
    } catch (final InvalidReferencedObjectException impossible) {
      // Teams cannot be kicked while answers are being scored (kickTeam is stage 0 only).
      throw new IllegalStateException("Cached team disappeared while applying a committed score", impossible);
    }
  }

  @Override
  public List<CreateTeamResponse> findByRoomCode(final String roomCode) {
    return teamRepository.findByGameId(roomCode);
  }
  @Override
  public ChoosingTeam findNextChoosingTeam(final UUID gameId, final int maxAlbums) {
    return teamRepository.findNext(gameId, maxAlbums);
  }

  @Override
  public Optional<TeamEntity> findById(final UUID teamId) {
    return teamRepository.findById(teamId);
  }
}
