/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author denijal
 */
@Service
public class ScheduleServiceImpl implements ScheduleService {
    
    private static final Logger log = LoggerFactory.getLogger(ScheduleServiceImpl.class);

    @Autowired
    private GameService gameService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private InterruptRepository interruptRepository;

    @Autowired
    private PresenceGateway presenceGateway;

    @Autowired
    private BroadcastGateway broadcastGateway;

    @Override
    @Transactional
    public void replaySong(UUID lastPlayedScheduleId, String roomCode) throws DerivedException {
        GameEntity game = gameService.findByCode(roomCode, 2);
        // Request validation (fail fast on invalid input).
        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findById(lastPlayedScheduleId);
        if (maybeSchedule.isEmpty()) {
            throw new InvalidReferencedObjectException("Order with id " + lastPlayedScheduleId + " does not exist");
        }
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        }

        LocalDateTime now = LocalDateTime.now();
        interruptRepository.resolveErrors(lastPlayedScheduleId, now);
        ScheduleEntity lastPlayedSong = maybeSchedule.get();
        lastPlayedSong.setStartedAt(now);
        scheduleRepository.saveAndFlush(lastPlayedSong);

        /* Replay has no direct response, so we broadcast the event instead of targeting the TV only.
           This ensures a refreshed admin client can recover the song duration. */
        log.info("Replaying schedule {}", lastPlayedScheduleId);
        broadcastGateway.broadcast(roomCode, "{\"type\":\"song_repeat\",\"remaining\":" + lastPlayedSong.getTrackId().getSongId().getSnippetDuration() + "}");
    }

    @Override
    @Transactional
    public void revealAnswer(UUID lastPlayedScheduleId, String roomCode) throws DerivedException {
        GameEntity game = gameService.findByCode(roomCode, 2);
        // Request validation (fail fast on invalid input).
        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findById(lastPlayedScheduleId);
        if (maybeSchedule.isEmpty()) {
            throw new InvalidReferencedObjectException("Order with id " + lastPlayedScheduleId + " does not exist");
        }
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        }

        LocalDateTime now = LocalDateTime.now();
        interruptRepository.resolveErrors(lastPlayedScheduleId, now);
        ScheduleEntity lastPlayedSong = maybeSchedule.get();
        lastPlayedSong.setRevealedAt(now);
        scheduleRepository.saveAndFlush(lastPlayedSong);

        /* Should you broadcast or simply send to TV?
           Admin will get response code 200 and can use that to change view */
        
        log.info("Revealing schedule {}", lastPlayedScheduleId);
        broadcastGateway.broadcast(roomCode, "{\"type\":\"song_reveal\"}");
    }

    @Override
    @Transactional
    /* Advances the game: resolve any pending errors from the previous song, then either
       start the next scheduled song or, if none remain, finish the album and move to the next stage. */
    public void progress(String roomCode) throws DerivedException {
        GameEntity game = gameService.findByCode(roomCode, 2);
        // Request validation (fail fast on invalid input).
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        }

        ScheduleEntity lastSongPlayed = scheduleRepository.findLastPlayed(game.getId());
        LocalDateTime now = LocalDateTime.now();
        
        // Any unresolved "pause/error" state must be closed before starting/revealing/advancing.
        interruptRepository.resolveErrors(lastSongPlayed.getId(), now);

        HashMap<String, Object> json = new HashMap<>();
        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findNext(game.getId());
        if (maybeSchedule.isPresent()) {
            ScheduleEntity nextSong = maybeSchedule.get();
            nextSong.setStartedAt(LocalDateTime.now());
            scheduleRepository.saveAndFlush(nextSong);

            GameServiceImpl.putDefaultFields(nextSong, json);
            json.put("remaining", nextSong.getTrackId().getSongId().getSnippetDuration()); //Najvjerovatnije ne treba!
            json.put("type", "song_next");

            broadcastGateway.broadcast(roomCode, new ObjectMapper().writeValueAsString(json));
            return;
        }

        int nextState = categoryService.finishAndNext(game);
        gameService.changeStage(nextState, roomCode);
    }

}
