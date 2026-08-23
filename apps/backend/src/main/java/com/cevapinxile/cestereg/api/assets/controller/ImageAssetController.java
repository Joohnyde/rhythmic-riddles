/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.assets.controller;

import static com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.core.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author denijal
 */
@Tag(name = "Assets")
@RestController
@RequestMapping("/assets/v1/image")
@CrossOrigin(origins = "*")
public class ImageAssetController {

  private static final Logger LOG = LoggerFactory.getLogger(ImageAssetController.class);

  @Autowired private ImageService imageService;

  @Operation(
      summary = "Get album image",
      description =
          """
          Returns the album image.

          Workflow:
          - Album images are stored on disk in: data/images/albums
          - File name is the UUID of the album.
          - Supported image formats are PNG, JPEG, and WebP.
          - The endpoint reads the image from disk and returns the raw image bytes
            with the corresponding image MIME type.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Album image found and returned.",
        content = {
          @Content(mediaType = "image/png", schema = @Schema(type = "byte[]", format = "binary")),
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "byte[]", format = "binary")),
          @Content(mediaType = "image/webp", schema = @Schema(type = "byte[]", format = "binary"))
        }),
    @ApiResponse(
        responseCode = "404",
        description = "Album image not found.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            "{\"error\":\"E007 - Asset Not Found\","
                                + "\"message\":\"Image not found for album "
                                + "550e8400-e29b-41d4-a716-446655440000\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "Album image found but could not be read.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            "{\"error\":\"E008 - Asset Unavailable\","
                                + "\"message\":\"Failed reading image for album "
                                + "550e8400-e29b-41d4-a716-446655440000\"}"))),
    @ApiResponse(
        responseCode = "500",
        description = "Unforeseen error.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            "{\"error\":\"E999 - Internal Server Error\","
                                + "\"message\":\"Unexpected internal error\"}")))
  })
  @GetMapping("/albums/{albumId}")
  public ResponseEntity<?> getAlbumImage(
      @Parameter(
              name = "albumId",
              description = "Unique identifier of the album.",
              required = true,
              example = "550e8400-e29b-41d4-a716-446655440000",
              schema = @Schema(type = "string", format = "uuid"))
          @PathVariable
          UUID albumId) {
    try {
      final ImageAsset image = imageService.getAlbumImage(albumId);

      return ResponseEntity.ok()
          .contentType(MediaType.valueOf(image.mimeType()))
          .body(image.bytes());
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }
}
