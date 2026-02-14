/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateGameRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;

/**
 *
 * @author denijal
 */
@Service
public class GameServiceImpl implements GameService {

    @Autowired
    private TeamService teamService;

    @Autowired
    private InterruptService interruptService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BroadcastGateway broadcastGateway;

    @Autowired
    private PresenceGateway presenceGateway;

    @Override
    public String createGame(CreateGameRequest cgr) throws DerivedException {
        // Request validation (fail fast on invalid input).
        if (cgr.maxAlbums() != null && cgr.maxAlbums() < 0) {
            throw new InvalidArgumentException("Number of albums must be a positive integer");
        }
        if (cgr.maxSongs() != null && cgr.maxSongs() < 0) {
            throw new InvalidArgumentException("Number of songs must be a positive integer");
        }
        
        GameEntity existingGame = new GameEntity(cgr);
        while (gameRepository.findByCode(existingGame.getCode()).isPresent()) {
            existingGame = new GameEntity(cgr);
        }
        gameRepository.saveAndFlush(existingGame);
        return existingGame.getCode();
    }

    /* Core payload fields expected by the frontend in all scenarios.
       Keeping them always present avoids conditional checks on the client side.*/
    public static void putDefaultFields(ScheduleEntity lastPlayedSong, HashMap<String, Object> json) {
        String question = lastPlayedSong.getTrackId().getAlbumId().getCustomQuestion();
        String answer = lastPlayedSong.getTrackId().getCustomAnswer();
        if (question == null) {
            question = "Prepoznaj ovu pjesmu!"; //TODO: Translate
        }
        if (answer == null) {
            answer = lastPlayedSong.getTrackId().getSongId().toString();
        }
        json.put("songId", lastPlayedSong.getTrackId().getSongId().getId());
        json.put("question", question);
        json.put("answer", answer);
        json.put("scheduleId", lastPlayedSong.getId());
        json.put("answerDuration", lastPlayedSong.getTrackId().getSongId().getAnswerDuration());
    }

    @Override
    public HashMap<String, Object> contextFetch(String roomCode) throws DerivedException {
        HashMap<String, Object> json = new HashMap<>();

        Optional<GameEntity> maybeGame = gameRepository.findByCode(roomCode);
        if (maybeGame.isEmpty()) {
            return json;
        }

        GameEntity game = maybeGame.get();
        json.put("type", "welcome");
        switch (game.getStage()) {
            case 0:
                // Return a list of teams to show
                json.put("teams", teamService.findByRoomCode(roomCode));
                json.put("stage", "lobby");
                break;
            case 1:
                LastCategory lastChosenCategory = categoryRepository.findLastCategory(game.getId());
                /* Stage 1 category handling:
                   If the previous category has already started, we must advance to the next one
                   (unless the game is finished).
                   The end check is defensive: stage 2 should already enforce completion,
                   but this protects against edge cases (e.g., empty or inconsistent setup).
                
                   Response structure:
                   - If selecting a new category:
                      return album metadata (name, image, pickedBy) and the current picker
                   - If a category was selected but not started yet:
                      return the selected album and who selected it
                
                   Picker rotation follows the predefined button order. */
                if (lastChosenCategory == null || lastChosenCategory.isStarted() && lastChosenCategory.getOrdinalNumber() != game.getMaxAlbums()) {
                    // Caclucate whose turn it is to choose
                    ChoosingTeam choosingTeam = teamService.findNextChoosingTeam(game.getId(), game.getMaxAlbums());
                    json.put("albums", categoryRepository.findByGameId(game.getId()));
                    json.put("team", choosingTeam == null ? choosingTeam : new CreateTeamResponse(choosingTeam));
                } else if (!lastChosenCategory.isStarted()) {
                    // We chose a category but we didn't start -- Choice display
                    json.put("selected", lastChosenCategory);
                }
                // Else would mean that it's the end and we are in stage 1. Impossible!

                json.put("stage", "albums");
                break;
            case 2:
                /* Stage 2 reconstructs the current song state from persisted timestamps.
                   We determine whether the snippet is playing, paused, revealed,
                   or waiting for admin action based on stored interrupt frames. */
                json.put("stage", "songs");
                ScheduleEntity lastPlayedSong = scheduleRepository.findLastPlayed(game.getId());

                /* Default fields
                   songId, question, answer, answerDuration, lastScheduledId, teams and their scores */
                GameServiceImpl.putDefaultFields(lastPlayedSong, json);

                /* Logically a default field, but depends on teamService + roomCode.
                   Kept outside putDefaultFields to avoid polluting its signature.*/
                json.put("scores", teamService.getTeamScores(roomCode));
                if (lastPlayedSong.getRevealedAt() != null) {
                    /* If the last played song is revealed (revealedAt != null on the latest track),
                       we are in the post-song state.
                       UI: play the answer and show the "progress" button.
                       Payload: revealed = true, bravo = UUID of the correct team. */
                    json.put("revealed", true);
                    json.put("bravo", interruptService.findCorrectAnswer(lastPlayedSong.getId(), roomCode));
                    break;
                }

                /* The song isn't revealed so we have to figure out if it finished
                   "seek" = effective playback time, excluding pauses/interrupts, computed from interrupt frames. */
                double seek = interruptService.calculateSeek(lastPlayedSong.getStartedAt(), lastPlayedSong.getId()) / 1000.0;
                double remaining = lastPlayedSong.getTrackId().getSongId().getSnippetDuration() - seek;

                if (remaining < 0) {
                    /* Seek went past the end which means the song had finished
                       UI: Show "replay" and "reveal" buttons
                       Payload: revealed = false. */
                    json.put("revealed", false);
                    break;
                }

                /* Seek didn't go past the end which means the song didn't finish
                   Fields neccesairy for proper, continued playback */
                json.put("seek", seek);
                json.put("remaining", remaining);

                // We need to figure out if we are interrupted. Find last unresolved team and system interrupt.
                InterruptEntity[] interrupts = interruptService.getLastTwoInterrupts(lastPlayedSong.getStartedAt(), lastPlayedSong.getId());

                InterruptEntity teamInterrupt = interrupts[0];
                InterruptEntity systemInterrupt = interrupts[1];

                if (teamInterrupt != null && teamInterrupt.isCorrect() == null) {
                    /* Last team interrupt doesn't have correct field means that the team is still answering
                       UI: Show who is answering, the answer, "correct" and "wrong" buttons
                       Payload: answeringTeam, interruptId = UUID of the team interrupt. */
                    TeamEntity team = teamInterrupt.getTeamId();
                    json.put("answeringTeam", new CreateTeamResponse(team));
                    json.put("interruptId", teamInterrupt.getId());
                    break;

                }
                if (systemInterrupt != null && systemInterrupt.getResolvedAt() == null) {
                    /* No team is answering but there is an unresolved system interrupt.
                       UI: Show "technical difficulties" and "unpause" button
                       Payload: error = true */
                    json.put("error", true);
                    break;

                }
                /* Seek didn't pass the end and there are no active pauses
                   UI: Play the song
                   Payload: Alredy covered by default fields*/
                break;
            case 3:
                /* Stage 3 just shows the leaderboard
                   Send all teams and their scores. */
                json.put("stage", "winner");
                json.put("scores", teamService.getTeamScores(roomCode));
                break;
        }

        return json;
    }

    @Override
    public GameEntity isChangeStageLegal(int newStage, String roomCode) throws DerivedException {
        // Request validation (fail fast on invalid input).
        Optional<GameEntity> maybeGame = gameRepository.findByCode(roomCode);
        if (maybeGame.isEmpty()) {
            throw new InvalidReferencedObjectException("Game with code " + roomCode + " does not exist");
        }
        
        GameEntity game = maybeGame.get();
        int currentStage = game.getStage();

        if (newStage < 1 || newStage > 4) {
            throw new InvalidArgumentException("Stage id has to be a number between 1 and 3");
        }
        if (newStage == currentStage) {
            throw new InvalidArgumentException("Game is already in that state");
        }
        if (currentStage == 0 && newStage != 1) {
            throw new WrongGameStateException("This game is in lobby state. The only allowed state transition is to album selection (stage 1)");
        }
        if (currentStage == 1 && newStage != 2) {
            throw new WrongGameStateException("Album selection is in progress. We can only move to song listening (stage 2)");
        }
        if (currentStage == 2 && newStage == 0) {
            throw new WrongGameStateException("We're listening to a song. Stage has to be 1 (album selection) or 3 (finish)");
        }

        /* No state change is legal if both apps aren't present.
           This request is made by admin app so their app is obviously 
           there, hence the error message. */
        if (!presenceGateway.areBothPresent(roomCode)) {
            throw new AppNotRegisteredException("TV app has to be connected to proceed");
        }
        return game;
    }

    @Override
    public void changeStage(int stageId, String roomCode) throws DerivedException {
        GameEntity game = isChangeStageLegal(stageId, roomCode);
        game.setStage(stageId);
        gameRepository.saveAndFlush(game);
        broadcastGateway.broadcast(roomCode, new ObjectMapper().writeValueAsString(contextFetch(roomCode)));
    }

    @Override
    public int getStage(String roomCode) {
        // Request validation (fail fast on invalid input).
        Optional<GameEntity> maybeGame = gameRepository.findByCode(roomCode);
        if (maybeGame.isEmpty()) {
            return -1;
        }
        
        return maybeGame.get().getStage();
    }

    @Override
    public GameEntity findByCode(String roomCode, Integer stageId) throws DerivedException {
        return gameRepository.findByCode(roomCode, stageId);
    }

}
