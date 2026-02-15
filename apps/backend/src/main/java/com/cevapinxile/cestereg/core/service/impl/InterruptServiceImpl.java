/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.GuessNotAllowedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.UnauthorizedException;
import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author denijal
 */
@Service
public class InterruptServiceImpl implements InterruptService {

    private static final Logger log = LoggerFactory.getLogger(InterruptServiceImpl.class);
    
    @Autowired
    private TeamService teamService;

    @Autowired
    private InterruptRepository interruptRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BroadcastGateway broadcastGateway;

    @Autowired
    private PresenceGateway presenceGateway;

    @Override
    /**
     * Calculates the effective playback time ("seek") by subtracting time spent
     * in interrupts.
     *
     * Important invariant: Interrupt frames must be either fully disjoint OR
     * properly nested. Partially overlapping interrupts are not allowed because
     * they make "paused time" ambiguous (you would either double-count or
     * under-count overlap).
     *
     * To enforce this, we query only the outermost interrupt frames and
     * subtract their durations. Nested interrupts are contained inside the
     * outer frame and must not be counted separately.
     */
    public long calculateSeek(LocalDateTime startTimestamp, UUID scheduleId) {
        List<InterruptFrame> interrupts = interruptRepository.findInterrupts(startTimestamp, scheduleId);
        if (interrupts == null) {
            interrupts = new ArrayList<>();
        }
        if (interrupts.isEmpty() || interrupts.getLast().getEnd() != null) {
            interrupts.add(new InterruptFrame(LocalDateTime.now(), null));
        }

        LocalDateTime end = startTimestamp;
        long seek = 0;
        for (InterruptFrame interrupt : interrupts) {
            seek += Duration.between(end, interrupt.getStart()).toMillis();
            end = interrupt.getEnd();
        }
        return seek;
    }

    @Override
    public InterruptEntity[] getLastTwoInterrupts(LocalDateTime startTimestamp, UUID scheduleId) {
        return new InterruptEntity[]{
            interruptRepository.findLastAnswer(startTimestamp, scheduleId),
            interruptRepository.findLastPause(startTimestamp, scheduleId)
        };

    }

    @Override
    @Transactional
    /* Interrupt invariant (critical for seek calculation):
       Interrupts may be DISJOINT (A ends, then B starts) or NESTED (B fully inside A).
       Partially overlapping interrupts are forbidden because paused time becomes ambiguous
       (overlap would be double-counted or missed).

       Forbidden example (partial overlap):
        1) TV disconnect triggers interrupt A (system pause)
        2) While A is active, a team buzz triggers interrupt B (team pause)
        3) Admin resolves A when TV reconnects, but B is still active
           → A and B now overlap only partially (invalid state).
      
      Therefore, creating a team interrupt while any system interrupt is active is only allowed
      if the new interrupt is guaranteed to be fully nested and resolved before the outer one ends. */
    public void interrupt(String roomCode, UUID teamId) throws DerivedException {

        GameEntity game = gameRepository.findByCode(roomCode, 2);
        ScheduleEntity lastPlayedSong = scheduleRepository.findLastPlayed(game.getId());
        TeamEntity team = null;

        if (teamId != null) {
            // Request validation (fail fast on invalid input).
            Optional<TeamEntity> maybeTeam = teamService.findById(teamId);
            if (maybeTeam.isEmpty()) {
                throw new InvalidReferencedObjectException("Team with id" + teamId + " does not exist");
            }
            team = maybeTeam.get();
            if (!team.getGameId().getId().equals(game.getId())) {
                throw new UnauthorizedException("Team with id " + teamId + " isn't part of the game " + roomCode);
            }

            double seek = calculateSeek(lastPlayedSong.getStartedAt(), lastPlayedSong.getId()) / 1000.0;
            double remaining = lastPlayedSong.getTrackId().getSongId().getSnippetDuration() - seek;

            List<InterruptEntity> interruptList = lastPlayedSong.getInterruptList();
            if (interruptList != null) {
                for (InterruptEntity interrupt : interruptList) {
                    if (interrupt.getTeamId() != null && interrupt.getTeamId().getId().compareTo(teamId) == 0) {
                        throw new GuessNotAllowedException("Team with id " + teamId + " already made a guess for this song");
                    }
                }
            }

            if (lastPlayedSong.getRevealedAt() != null || remaining < 0) {
                throw new GuessNotAllowedException("Song " + lastPlayedSong.getTrackId().getSongId().getId() + " is no longer playing");
            }

            InterruptEntity[] lastTwoInterrupts = getLastTwoInterrupts(lastPlayedSong.getStartedAt(), lastPlayedSong.getId());
            InterruptEntity teamInterrupt = lastTwoInterrupts[0];
            InterruptEntity systemInterrupt = lastTwoInterrupts[1];
            if (teamInterrupt != null && teamInterrupt.isCorrect() == null) {
                throw new GuessNotAllowedException("Someone's already guessing");
            }
            if (systemInterrupt != null && systemInterrupt.getResolvedAt() == null) {
                throw new GuessNotAllowedException("The game is paused");
            }

        }
        InterruptEntity newInterrupt = new InterruptEntity(UUID.randomUUID());
        newInterrupt.setScheduleId(lastPlayedSong);
        newInterrupt.setTeamId(team);
        newInterrupt.setArrivedAt(LocalDateTime.now());
        interruptRepository.saveAndFlush(newInterrupt);

        /* Lightweight WS frame to reduce serialization overhead for frequent events.
           Safe from escaping concerns because we only send UUIDs (no free-form input). */
        broadcastGateway.broadcast(roomCode, "{\"type\":\"pause\",\"answeringTeamId\":\"" + (team == null ? "null" : team.getId()) + "\",\"interruptId\":\"" + newInterrupt.getId() + "\"}");
    }

    @Override
    @Transactional
    public void resolveErrors(UUID lastPlayedScheduleId, String roomCode) throws DerivedException {
        GameEntity game = gameRepository.findByCode(roomCode, 2);
        
        // Request validation (fail fast on invalid input).
        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findById(lastPlayedScheduleId);
        if (maybeSchedule.isEmpty()) {
            throw new InvalidReferencedObjectException("Order with id " + lastPlayedScheduleId + " does not exist");
        }
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        }

        Integer previousScenario = interruptRepository.findPreviousScenarioId(lastPlayedScheduleId);
        interruptRepository.resolveErrors(lastPlayedScheduleId, LocalDateTime.now());
        broadcastGateway.broadcast(roomCode, "{\"type\":\"error_solved\",\"previousScenario\":" + previousScenario + "}");
    }

    @Override
    @Transactional
    public void answer(UUID answerId, AnswerRequest ar, String roomCode) throws DerivedException {
        boolean correct = ar.correct();
        GameEntity game = gameRepository.findByCode(roomCode, 2);
        
        // Request validation (fail fast on invalid input).
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        }
        Optional<InterruptEntity> maybeAnswer = interruptRepository.findById(answerId);
        if (maybeAnswer.isEmpty()) {
            throw new InvalidReferencedObjectException("Answer with id " + answerId + " does not exist");
        }
        InterruptEntity answer = maybeAnswer.get();
        if (!answer.getTeamId().getGameId().getCode().equals(roomCode)) {
            throw new InvalidArgumentException("Room code " + roomCode + " isn't consistent with the answer");
        }
        if (answer.isCorrect() != null || answer.getResolvedAt() != null) {
            throw new GuessNotAllowedException("That guess was already answered");
        }

        LocalDateTime resolvedAt = LocalDateTime.now();

        // Scoring rule: correct guess awards +30, incorrect guess penalizes -10.
        Integer newScore = teamService.getTeamPoints(answer.getTeamId().getId(), roomCode) + (correct ? 30 : -10);
        answer.setResolvedAt(resolvedAt);
        answer.setCorrect(correct);
        answer.setScoreOrScenarioId(newScore);
        interruptRepository.resolveErrors(answer.getScheduleId().getId(), resolvedAt);
        interruptRepository.save(answer);

        if (correct) {
            ScheduleEntity schedule = answer.getScheduleId();
            schedule.setRevealedAt(resolvedAt);
            scheduleRepository.saveAndFlush(schedule);
        }

        teamService.saveTeamAnswer(answer.getTeamId().getId(), answer.getScheduleId().getId(), newScore, roomCode);
        log.info("Team {} answered correct={} new points={}", answer.getTeamId().getId(), correct, newScore);
        broadcastGateway.broadcast(roomCode, "{\"type\":\"answer\",\"teamId\":\"" + answer.getTeamId().getId() + "\",\"scheduleId\":\"" + answer.getScheduleId().getId() + "\",\"correct\":" + correct + "}");
    }

    @Override
    public UUID findCorrectAnswer(UUID scheduleId, String roomCode) throws DerivedException {
        GameEntity game = gameRepository.findByCode(roomCode, 2);
        return interruptRepository.findCorrectAnswer(scheduleId);
    }

    @Override
    @Transactional
    public void savePreviousScenario(int scenarioId, String roomCode) throws DerivedException {
        // Request validation (fail fast on invalid input).
        if (scenarioId < 0 || scenarioId > 4 || scenarioId == 3) {
            throw new InvalidArgumentException("Scenario has to be a number between 0 and 4 but not 3");
        }

        GameEntity game = gameRepository.findByCode(roomCode, 2);
        ScheduleEntity lastPlayedSong = scheduleRepository.findLastPlayed(game.getId());
        if (lastPlayedSong != null) {
            InterruptEntity pause = interruptRepository.findLastPause(lastPlayedSong.getStartedAt(), lastPlayedSong.getId());
            if (pause != null) {
                /* NOTE: scoreOrScenarioId is overloaded:
                   - for TEAM interrupts it stores the awarded score (+30 / -10)
                   - for SYSTEM/PAUSE interrupts it stores the admin UI "previousScenarioId"
                   This keeps schema small but requires callers to interpret it by interrupt type. */
                log.info("System paused. Previous scenario: {}", scenarioId);
                pause.setScoreOrScenarioId(scenarioId);
                interruptRepository.saveAndFlush(pause);
            }
        }

    }

}
