/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.ErrorResponse;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 * @author denijal
 */
@Tag(name = "Schedules")
@RestController
@RequestMapping("/api/v1/games/{roomCode}/schedules")
@CrossOrigin(origins = "*")
public class ScheduleController {
    
    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    @Autowired
    private ScheduleService scheduleService;

    @Operation(
            summary = "Replay current song snippet",
            description = """
Replays (refreshes) the current song snippet in stage 2.

Workflow:
- Validates game and schedule exist and game is in stage 2.
- Ensures both apps are present (sync safety).
- Resolves all unresolved system errors to avoid seek/sync issues.
- Sets schedule.startedAt to now and persists.
- Broadcasts remaining time (full duration) to clients.
"""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay triggered successfully."),
        @ApiResponse(
                responseCode = "404",
                description = "Invalid Game/Schedule id.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game or schedule was not found for the provided identifiers.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Game isn't in state 2.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Replaying is only allowed in stage 2 (song playing).\"}")
                )
        ),
        @ApiResponse(
                responseCode = "503",
                description = "An app isn't present.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Cannot replay because one or more apps are not registered or not reachable.\"}")
                )
        )
    })

    @PostMapping("/{scheduleId}/replay")
    public ResponseEntity<?> replaySong(@RoomCodePath @PathVariable String roomCode, 
            @Parameter(
                    name = "scheduleId",
                    description = "Unique identifier of the schedule.",
                    required = true,
                    example = "1c835481-b314-4217-9a32-0af276e44fef",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID scheduleId) {
        try {
            scheduleService.replaySong(scheduleId, roomCode);
            return ResponseEntity.ok().build();
        } catch (DerivedException ex) {
            log.info(ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            log.warn("Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Reveal song answer",
            description = """
Reveals the answer for the current song in stage 2.

Workflow:
- Validates game and schedule exist and game is in stage 2.
- Ensures both apps are present (sync safety).
- Resolves all unresolved system errors to avoid seek/sync issues.
- Sets schedule.revealedAt to now, persists, and broadcasts reveal to clients.
"""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Answer reveal triggered successfully."),
        @ApiResponse(
                responseCode = "404",
                description = "Invalid Game/Schedule id.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game or schedule was not found for the provided identifiers.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Game isn't in state 2.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Revealing is only allowed in stage 2 (song playing).\"}")
                )
        ),
        @ApiResponse(
                responseCode = "503",
                description = "An app isn't present.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Cannot reveal because one or more apps are not registered or not reachable.\"}")
                )
        )
    })

    @PostMapping("/{scheduleId}/reveal")
    public ResponseEntity<?> revealAnswer(@RoomCodePath @PathVariable String roomCode, 
            @Parameter(
                    name = "scheduleId",
                    description = "Unique identifier of the schedule.",
                    required = true,
                    example = "1c835481-b314-4217-9a32-0af276e44fef",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID scheduleId) {
        try {
            scheduleService.revealAnswer(scheduleId, roomCode);
            return ResponseEntity.ok().build();
        } catch (DerivedException ex) {
            log.info(ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            log.warn("Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Advance to next song or next stage",
            description = """
Moves gameplay forward after a song finishes.

Workflow:
- Validates game exists and is in stage 2, and both apps are present.
- Resolves all unresolved system errors to avoid seek/sync issues.
- Finds the next Schedule by incrementing ordinal number and looking it up.
  - If found: sets its startedAt to now and broadcasts that the next song is playing.
  - If not found: determines whether to go back to album selection (stage 1) or finish the game (stage 3):
    - Loads last chosen category and compares its ordinalNumber with game.maxAlbums.
    - If maxAlbums reached: transitions to stage 3 (winner table).
    - Else: marks category finished, transitions game to stage 1, broadcasts stage change.
"""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Advanced successfully (next song or stage transition)."),
        @ApiResponse(
                responseCode = "404",
                description = "Invalid Game.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game was not found for the provided roomCode.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Game isn't in state 2.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Next is only allowed in stage 2 (song playing).\"}")
                )
        ),
        @ApiResponse(
                responseCode = "503",
                description = "An app isn't present.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Cannot advance because one or more apps are not registered or not reachable.\"}")
                )
        )
    })

    @PostMapping("/next")
    public ResponseEntity<?> next(@RoomCodePath @PathVariable String roomCode) {
        try {
            scheduleService.progress(roomCode);
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
