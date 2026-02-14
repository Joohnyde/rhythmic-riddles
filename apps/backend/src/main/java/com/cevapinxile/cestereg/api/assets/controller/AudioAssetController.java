/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.assets.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.ErrorResponse;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cevapinxile.cestereg.core.service.SongService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 * @author denijal
 */
@Tag(name = "Assets")
@RestController
@RequestMapping("/assets/v1/audio")
@CrossOrigin(origins = "*")
public class AudioAssetController {

    @Autowired
    private SongService songService;

    @Operation(
            summary = "Get song snippet audio (mp3)",
            description = """
Returns the audio snippet file for a song.

Workflow:
- Snippets are stored on disk in: data/audio/snippets
- File name is the UUID of the song, stored as an mp3.
- Endpoint reads the file and returns raw bytes as audio/mpeg.
"""
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Snippet found and returned as audio/mpeg.",
                content = @Content(
                        mediaType = "audio/mpeg",
                        schema = @Schema(type = "byte[]", format = "binary")
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Snippet file not found.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Snippet audio for songId was not found on disk.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "503",
                description = "Snippet file found but could not be read (IOException).",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Snippet audio exists but could not be read due to an IO error.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Unforeseen error.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E999 - Internal error\",\"message\":\"Unexpected server error while reading snippet audio.\"}")
                )
        )
    })

    @GetMapping(value = "/snippets/{songId}", produces = "audio/mpeg")
    public ResponseEntity<?> playSnippet(
            @Parameter(
                    name = "songId",
                    description = "Unique identifier of the song.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID songId) {
        try {
            byte[] content = songService.playSnippet(songId);
            return ResponseEntity.ok()
                    .header("Accept-Ranges", "bytes")
                    .body(content);
        } catch (DerivedException ex) {
            Logger.getLogger(AudioAssetController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            Logger.getLogger(AudioAssetController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Get song answer audio (mp3)",
            description = """
Returns the answer audio file for a song.

Workflow:
- Answers are stored on disk in: data/audio/answers
- File name is the UUID of the song, stored as an mp3.
- Endpoint reads the file and returns raw bytes as audio/mpeg.
"""
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Answer audio found and returned as audio/mpeg.",
                content = @Content(
                        mediaType = "audio/mpeg",
                        schema = @Schema(type = "string", format = "binary")
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Answer file not found.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E001 - Invalid referenced object\",\"message\":\"Answer audio for songId was not found on disk.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "503",
                description = "Answer file found but could not be read (IOException).",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E004 - App not reachable\",\"message\":\"Answer audio exists but could not be read due to an IO error.\"}")
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Unforeseen error.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"E999 - Internal error\",\"message\":\"Unexpected server error while reading answer audio.\"}")
                )
        )
    })

    @GetMapping(value = "/answers/{songId}", produces = "audio/mpeg")
    public ResponseEntity<?> playAnswer(
            @Parameter(
                    name = "songId",
                    description = "Unique identifier of the song.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            @PathVariable UUID songId) {
        try {
            byte[] content = songService.playAnswer(songId);
            return ResponseEntity.ok()
                    .header("Accept-Ranges", "bytes")
                    .body(content);
        } catch (DerivedException ex) {
            Logger.getLogger(AudioAssetController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex) {
            Logger.getLogger(AudioAssetController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }

}
