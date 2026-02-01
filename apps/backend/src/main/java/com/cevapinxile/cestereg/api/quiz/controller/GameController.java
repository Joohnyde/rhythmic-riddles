/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.ErrorResponse;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateGameRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.RoomCodeResponse;
import com.cevapinxile.cestereg.api.quiz.dto.request.StageIdRequest;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cevapinxile.cestereg.core.service.GameService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 *
 * @author denijal
 */
@Tag(name="Games")
@RestController
@RequestMapping("/api/v1/games")
@CrossOrigin(origins = "*")
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    
    @Autowired
    private GameService gameService;

    @Operation(
    summary = "Create a new game",
    description = """
Creates a new game in the database.

Workflow:
- Performs sanity checks on provided game parameters (currently maxSongs and maxAlbums).
- Generates a random 4-character room code repeatedly until a unique one is found.
- Persists the new game with creation time = now and default state = 0 (lobby).
- Returns the created room code to be used for joining/manipulating the game.
"""
)
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Game created successfully (roomCode returned).",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = RoomCodeResponse.class),
            examples = @ExampleObject(value = "{\"roomCode\":\"AKKU\"}")
        )
    ),
    @ApiResponse(
        responseCode = "422",
        description = "Non-positive integer in one of the parameters.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E002 - Malformed argument\",\"message\":\"maxSongs and maxAlbums must be positive integers.\"}")
        )
    )
})
    @PostMapping
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest cgr) {
        try {
            return ResponseEntity.ok(new RoomCodeResponse(gameService.createGame(cgr)));
        } catch (DerivedException ex) {
            log.info(ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            log.warn("Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

    
    @Operation(
    summary = "Change game stage",
    description = """
Transitions a game to a new stage (admin-only action).

Workflow:
- Validates that the game exists for the given roomCode.
- Validates stage_id is within [0..3] and different from current stage.
- Validates legal transitions only: 0->1, 1->2, 2->1, 2->3.
- Persists the stage change and broadcasts the update to connected apps.
- Fails if the TV app is not present.
"""
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Stage changed successfully."),
    @ApiResponse(
        responseCode = "422",
        description = "Stage id is invalid or already in that stage.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E002 - Malformed argument\",\"message\":\"stage_id must be between 0 and 3 and must differ from the current stage.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Illegal state transition.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Illegal stage transition: only 0->1, 1->2, 2->1, 2->3 are allowed.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "503",
        description = "TV app isn't present / not reachable.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"TV app is not registered or not reachable; cannot broadcast stage change.\"}")
        )
    )
})
    @PutMapping("/{roomCode}/stage")
    public ResponseEntity<?> changeState(@RoomCodePath @PathVariable String roomCode, @RequestBody StageIdRequest stageId) {
        try {gameService.changeStage(stageId.stageId(), roomCode);
            return ResponseEntity.ok().build();
        } catch (DerivedException ex) {
            log.info(ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            log.warn("Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

}
