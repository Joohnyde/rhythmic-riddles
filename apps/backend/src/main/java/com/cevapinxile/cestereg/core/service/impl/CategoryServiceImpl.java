/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.MissingArgumentException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;

/**
 *
 * @author denijal
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private GameService gameService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private BroadcastGateway broadcastGateway;

    @Autowired
    private PresenceGateway presenceGateway;

    @Override
    public LastCategory pickAlbum(UUID categoryId, TeamIdRequest par, String roomCode) throws DerivedException {
       // Request validation (fail fast on invalid input).
        if (par == null) {
            throw new MissingArgumentException("The request's body is missing");
        }
        UUID teamId = par.teamId();
        if (categoryId == null) {
            throw new MissingArgumentException("The request's body is missing category_id");
        }
        Optional<CategoryEntity> maybeCategory = categoryRepository.findById(categoryId);
        if (maybeCategory.isEmpty()) {
            throw new InvalidReferencedObjectException("Category with with id " + categoryId + " does not exist");
        }
        
        TeamEntity team = null;
        if (teamId != null) {
            Optional<TeamEntity> maybeTeam = teamRepository.findById(teamId);
            if (maybeTeam.isEmpty()) {
                throw new InvalidReferencedObjectException("Team with with id " + teamId + " does not exist");
            }
            team = maybeTeam.get();
            if (!team.getGameId().getCode().equals(roomCode)) {
                throw new InvalidArgumentException("Room code " + roomCode + " isn't consistent with the provided team");
            }
        }

        CategoryEntity category = maybeCategory.get();
        if (!category.getGameId().getCode().equals(roomCode)) {
            throw new InvalidArgumentException("Room code " + roomCode + " isn't consistent with the category");
        }
        if (category.getGameId().getStage() != 1) {
            throw new WrongGameStateException("Game " + roomCode + " doesn't choose albums now");
        }

        category.setPickedByTeamId(team);
        category.setOrdinalNumber(categoryRepository.findNextId(category.getGameId().getId()));

        /* No state change is legal if both apps aren't present.
           This request is made by admin app so their app is obviously 
           there, hence the error message. */
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("TV app has to be connected to proceed");
        }

        categoryRepository.saveAndFlush(category);
        LastCategory result = new LastCategory(category);
        broadcastGateway.toTv(roomCode, "{\"type\":\"album_picked\",\"selected\":" + new ObjectMapper().writeValueAsString(result) + "}");
        return result;
    }

    @Override
    public void startCategory(UUID categoryId, String roomCode) throws DerivedException {
        // Request validation (fail fast on invalid input).
        Optional<CategoryEntity> maybeCategory = categoryRepository.findById(categoryId);
        if (maybeCategory.isEmpty()) {
            throw new InvalidReferencedObjectException("Category with with id " + categoryId + " does not exist");
        }

        CategoryEntity category = maybeCategory.get();
        if (!category.getGameId().getCode().equals(roomCode)) {
            throw new InvalidArgumentException("Room code " + roomCode + " isn't consistent with the category");
        }

        GameEntity game = gameService.isChangeStageLegal(2, roomCode);
        int maxSongs = game.getMaxSongs();
        List<TrackEntity> trackList = category.getAlbumId().getTrackList();
        if (trackList.size() < maxSongs) {
            /* Getting here means that during creationg admin added
               a category that doesn't meet the requirements.
               Preparation tool should make this impossible. */
            throw new InvalidArgumentException("The category (len:" + trackList.size() + ") doesn't have enough songs (" + maxSongs + ")");
        }

        /* Pick songs for this category and persist their schedules.
           All timestamps start as null; the first song is started immediately (startedAt = now()).
           TODO: Improve selection logic (prefer songs not already used in other categories). */
        Collections.shuffle(trackList);
        AtomicInteger index = new AtomicInteger(0);
        List<ScheduleEntity> schedule = trackList.subList(0, Math.min(maxSongs, trackList.size())).stream().map(elem -> new ScheduleEntity(category, elem, index.incrementAndGet())).toList();
        schedule.getFirst().setStartedAt(LocalDateTime.now());
        scheduleRepository.saveAllAndFlush(schedule);

        gameService.changeStage(2, roomCode); // Not-optimal. TODO: Broadcast only what you need.
    }

    @Override
    @Modifying
    public int finishAndNext(GameEntity game) throws DerivedException {
        LastCategory lastCategoryDto = categoryRepository.findLastCategory(game.getId());
        
        // Request validation (fail fast on invalid input).
        Optional<CategoryEntity> maybeKategorija = categoryRepository.findById(lastCategoryDto.getCategoryId());
        if (maybeKategorija.isEmpty()) {
            throw new InvalidReferencedObjectException("Category with with id " + lastCategoryDto.getCategoryId() + " does not exist");
        }
        
        CategoryEntity lastCategory = maybeKategorija.get();
        lastCategory.setDone(true);
        categoryRepository.saveAndFlush(lastCategory);
        if (lastCategory.getOrdinalNumber() == game.getMaxAlbums()) {
            return 3;
        }
        return 1;
    }

}
