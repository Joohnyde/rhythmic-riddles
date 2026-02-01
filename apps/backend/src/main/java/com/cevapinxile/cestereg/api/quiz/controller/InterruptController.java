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
import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.quiz.dto.request.ScenarioRequest;
import com.cevapinxile.cestereg.api.quiz.dto.request.ScheduleIdRequest;
import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cevapinxile.cestereg.core.service.InterruptService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 * @author denijal
 */

@Tag(name="Interrupts")
@RestController
@RequestMapping("/api/v1/games/{roomCode}")
@CrossOrigin(origins = "*")
public class InterruptController {
    
    private static final Logger log = LoggerFactory.getLogger(InterruptController.class);

    @Autowired
    private InterruptService interruptService;

   @Operation(
    summary = "Create an interrupt (team buzz or system interrupt)",
    description = """
Creates an interrupt during stage 2 (song playing). Used for team buzz-ins and system-caused errors.

Workflow:
- Validates roomCode and request body (teamId optional).
- Ensures game exists and is in stage 2.
- If teamId is present:
  - Ensures the team exists and belongs to the specified game.
  - Calculates current seek/time remaining of the active song to ensure the interrupt happened while a snippet is playing.
  - Checks that the team has not already buzzed for the same song.
  - Ensures the last system interrupt and last team interrupt are resolved to avoid stacking unresolved interrupts.
- Persists a new interrupt (teamId optional, arrivedAt=now).
- Notifies the TV app. (TV connectivity is not checked here because disconnects create implicit system interrupts.)
"""
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Interrupt created successfully."),
    @ApiResponse(
        responseCode = "404",
        description = "Team or Game doesn't exist.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game was not found for roomCode, or teamId does not exist.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Game isn't in stage 2 or guess isn't allowed.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E006 - Guess wasn't allowed\",\"message\":\"Interrupt rejected: song is not currently playable, game is paused, or another team is already guessing.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "401",
        description = "Team isn't part of the game.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E005 - Unauthorized request\",\"message\":\"Provided teamId does not belong to the game identified by roomCode.\"}")
        )
    )
})
    @PostMapping("/interrupts")
    public ResponseEntity<?> interrupt(@RoomCodePath @PathVariable String roomCode, @RequestBody TeamIdRequest tir) {
        try {
            interruptService.interrupt(roomCode, tir.teamId());
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
    summary = "Resolve all unresolved system interrupts",
    description = """
Resolves system-caused interrupts (technical issues) in stage 2.

Workflow:
- Validates game exists and is in stage 2.
- Validates the provided scheduleId exists (last played song schedule).
- Verifies both apps are present (ready to resume).
- Resolves ALL unresolved SYSTEM interrupts at once by setting resolvedAt, preventing seek/sync inconsistencies.
- Broadcasts the previous 'scenario' that was active when the initial interrupt occurred so UIs can restore state.
"""
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "System interrupts resolved successfully."),
    @ApiResponse(
        responseCode = "404",
        description = "Schedule or Game doesn't exist.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game or schedule was not found for the provided identifiers.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Game isn't in stage 2.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"System error resolution is only allowed in stage 2 (song playing).\"}")
        )
    ),
    @ApiResponse(
        responseCode = "503",
        description = "One of the apps is missing.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Cannot resume because one or more apps are not registered or not reachable.\"}")
        )
    )
})

    @PostMapping("/interrupts/system/resolve")
    public ResponseEntity<?> resolveErrors(@RoomCodePath @PathVariable String roomCode, @RequestBody ScheduleIdRequest sir) {
        try {
            interruptService.resolveErrors(sir.scheduleId(), roomCode);
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
    summary = "Mark an interrupt answer as correct/incorrect",
    description = """
Admin resolves a team's guess (correct or incorrect) for a given interrupt.

Workflow:
- Validates game exists and is in stage 2.
- Validates interrupt exists and belongs to the provided game (roomCode).
- Optionally checks if already resolved to prevent manual repeated HTTP calls.
- Requires both apps to be present before changing points (sync safety).
- Updates team points: +30 if correct, -10 if incorrect.
- Resolves all previously unresolved system errors to avoid seek/sync issues.
- If correct, resolves the current Schedule (the schedule where the interrupt occurred) and proceeds accordingly.
"""
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Answer processed successfully."),
    @ApiResponse(
        responseCode = "404",
        description = "Game or Interrupt doesn't exist.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game was not found for roomCode or interrupt was not found for answerId.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "422",
        description = "Interrupt wasn't made in the given game.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E002 - Malformed argument\",\"message\":\"Interrupt does not belong to the specified game (roomCode mismatch).\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Wrong stage or guess already made.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Answering is only allowed in stage 2 and only for unresolved interrupts.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "503",
        description = "One of the apps is missing.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Cannot process answer because one or more apps are not registered or not reachable.\"}")
        )
    )
})

    @PostMapping("/interrupts/{answerId}/answer")
    public ResponseEntity<?> answer(@RoomCodePath @PathVariable String roomCode, 
            @Parameter(
                    name = "answerId",
                    description = "Unique identifier of the answer.",
                    required = true,
                    example = "b2d94406-6536-4d40-b6ab-6a4b05bf700a",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID answerId, @RequestBody AnswerRequest ar) {
        try {
            interruptService.answer(answerId, ar, roomCode);
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
    summary = "Save UI scenario number into last interrupt",
    description = """
Stores the UI 'scenario' (front-end concept) so clients can return to the same screen after recovery.

Workflow:
- Validates game exists and is in stage 2.
- Validates scenario is within 0..4 and is NOT equal to 3 (3 represents error screen itself and is not persisted here).
- Finds the last interrupt for the game and stores the scenario in the interrupt's 'scoreOrScenarioId' field
  (reused field; same column that is used for team points calculations).
- When system errors are resolved, backend re-broadcasts this saved scenario so UIs restore quickly.
"""
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Scenario saved successfully."),
    @ApiResponse(
        responseCode = "404",
        description = "Game doesn't exist.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game was not found for the provided roomCode.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "422",
        description = "Scenario invalid (not 0..4 or equals 3).",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E002 - Malformed argument\",\"message\":\"scenario must be 0..4 and must not be 3.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Game isn't in stage 2.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Saving scenario is only allowed in stage 2 (song playing).\"}")
        )
    )
})

    @PutMapping("/ui/scenario")
    public ResponseEntity<?> savePreviousScenario(@RoomCodePath @PathVariable String roomCode, @RequestBody ScenarioRequest sr) {
        try {
            interruptService.savePreviousScenario(sr.scenario(), roomCode);
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
