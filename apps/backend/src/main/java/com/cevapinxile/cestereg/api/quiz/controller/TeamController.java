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
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cevapinxile.cestereg.core.service.TeamService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 * @author denijal
 */

@Tag(name="Teams")
@RestController
@RequestMapping("/api/v1/games/{roomCode}/teams")
@CrossOrigin(origins = "*")
public class TeamController {

    @Autowired
    private TeamService teamService;

    @Operation(
            summary = "Create a team",
            description = """
Registers a team in the lobby (stage 0).

Workflow:
- Admin provides team name, image, and selected buttonCode (for wiring).
- Validates required arguments are present and valid.
- Validates game exists and is in stage 0 (lobby).
- Validates referenced button exists (temporary design).
- Creates the Team entity, wires team<->button, persists, and notifies the TV app.

Notes:
- Image is currently expected to be a link (e.g., identicon), but future versions may generate/store reusable images.
- Button selection will become automatic (latest unassigned pressed) and the Button table will be replaced by an index on Team.
"""
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Team created successfully.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = CreateTeamResponse.class),
                        examples = @ExampleObject(value = """
{
  "id":"b8c57c1d-4e91-4a1f-9a0d-0a65c3f4d2d1",
  "name":"Team Cyan",
  "image":"https://example.com/team.png"
}
""")
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "A required argument is missing.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E000 - An argument is missing\",\"message\":\"name, buttonCode, and image are required.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Nonexisting Game or button.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game was not found for roomCode, or buttonCode does not exist.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Game isn't in state 0 (lobby).",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Teams can only be created in stage 0 (lobby).\"}")
                )
        )
    })

    @PostMapping
    public ResponseEntity<?> createTeam(@RoomCodePath @PathVariable String roomCode, @RequestBody CreateTeamRequest ctr) {
        try {
            return ResponseEntity.ok(teamService.createTeam(ctr, roomCode));
        } catch (DerivedException ex) {
            Logger.getLogger(TeamController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            Logger.getLogger(TeamController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Kick (delete) a team",
            description = """
Removes a team from the game during the lobby stage.

Workflow:
- Validates the game exists and is still in stage 0 (not started).
- Validates the team exists.
- Deletes the team from the database and notifies the TV app.
"""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Team deleted successfully."),
        @ApiResponse(
                responseCode = "404",
                description = "Nonexisting Game or Team.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game or team was not found for the provided identifiers.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Game already started (isn't in stage 0).",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Kicking teams is only allowed in stage 0 (lobby).\"}")
                )
        )
    })

    @DeleteMapping("/{teamId}")
    public ResponseEntity<?> kickTeam(@RoomCodePath @PathVariable String roomCode,
            @Parameter(
                    name = "teamId",
                    description = "Unique identifier of the team.",
                    required = true,
                    example = "746d47f0-1f98-4ac5-8f02-7fe420bc1f5a",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable String teamId) {
        try {
            teamService.kickTeam(teamId, roomCode);
            return ResponseEntity.ok().build();
        } catch (DerivedException ex) {
            Logger.getLogger(TeamController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            Logger.getLogger(TeamController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

}
