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
import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cevapinxile.cestereg.core.service.CategoryService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 *
 * @author denijal
 */

@Tag(name="Categories")
@RestController
@RequestMapping("/api/v1/games/{roomCode}/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Operation(
    summary = "Pick an album category for stage 1",
    description = """
Admin selects a category to be played next (stage 1: album selection).

Workflow:
- Validates request body presence and required fields.
- Validates the game exists, is in stage 1, and that the teamId (if provided) belongs to this game.
- Validates the chosen category exists and belongs to the given roomCode (it must have been added during game creation).
- Marks the selected category with the next ordinal number (incremental), used to differentiate picked categories.
- Persists who picked the category and notifies the TV via socket.
"""
)
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Category picked successfully (LastCategory returned).",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = LastCategory.class),
            examples = @ExampleObject(value = """
{
  "categoryId":"2f2c2b9b-6f7b-4d57-a3a5-0a6d1d3a9d61",
  "chosenCategoryPreview":{"name":"Best of 2000s","image":"https://example.com/cat.png"},
  "pickedByTeam":{"id":"b8c57c1d-4e91-4a1f-9a0d-0a65c3f4d2d1","name":"Team Cyan","picture":"https://example.com/team.png"},
  "started":false,
  "ordinalNumber":1
}
""")
        )
    ),
    @ApiResponse(
        responseCode = "422",
        description = "Request body is missing or invalid.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E002 - Malformed argument\",\"message\":\"Request body must contain a valid teamId (UUID).\"}")
        )
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Team_ID or ROOM_CODE or Category_ID not existing.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Game, team, or category was not found for the provided identifiers.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Game isn't in stage 1.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Category picking is only allowed in stage 1 (album selection).\"}")
        )
    ),
    @ApiResponse(
        responseCode = "503",
        description = "TV app isn't present.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"TV app is not registered or not reachable; cannot display picked category.\"}")
        )
    )
})
    @PutMapping("/{categoryId}/pick")
    public ResponseEntity<?> pickAlbum(@RoomCodePath @PathVariable String roomCode, 
            @Parameter(
                    name = "categoryId",
                    description = "Unique identifier of the category.",
                    required = true,
                    example = "a6d91448-0344-464d-944b-d891de888ffc",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID categoryId, @RequestBody TeamIdRequest par) {
        try {
            return ResponseEntity.ok(categoryService.pickAlbum(categoryId, par, roomCode));
        } catch (DerivedException ex) {
            Logger.getLogger(CategoryController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            Logger.getLogger(CategoryController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
    summary = "Start playing a picked category",
    description = """
Starts the gameplay for the chosen category.

Workflow:
- Validates roomCode and categoryId, verifies the game exists and is in stage 1.
- Ensures the category belongs to the game (was added to that roomCode at creation time).
- Fetches game's maxSongs and all songs in the selected category.
- Randomly picks maxSongs songs, creates Schedule rows for them, and sets the first start_time to now.
- Transitions game state to 2 (song playing) and broadcasts that the song snippet has started.
"""
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Category started successfully."),
    @ApiResponse(
        responseCode = "422",
        description = "Found category wasn't added to the game with that ROOM_CODE.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E002 - Malformed argument\",\"message\":\"Category exists but is not part of the specified game (roomCode).\"}")
        )
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Category_ID is not existing.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Category was not found for the provided categoryId.\"}")
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Game isn't in stage 1.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"error\":\"E003 - Wrong game-state\",\"message\":\"Starting a category is only allowed in stage 1 (album selection).\"}")
        )
    )
})

    @PostMapping("/{categoryId}/start")
    public ResponseEntity<?> start(@RoomCodePath @PathVariable String roomCode, 
            @Parameter(
                    name = "categoryId",
                    description = "Unique identifier of the category.",
                    required = true,
                    example = "a6d91448-0344-464d-944b-d891de888ffc",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID categoryId) {
        try {
            categoryService.startCategory(categoryId, roomCode);
            return ResponseEntity.ok().build();
        } catch (DerivedException ex) {
            Logger.getLogger(CategoryController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            Logger.getLogger(CategoryController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
}
