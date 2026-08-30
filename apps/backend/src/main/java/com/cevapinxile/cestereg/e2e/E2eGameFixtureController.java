package com.cevapinxile.cestereg.e2e;

import static com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException;

import com.cevapinxile.cestereg.common.util.RoomCodePath;
import com.cevapinxile.cestereg.core.service.BuzzerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[E2E] GameFixture")
@RestController
@Profile("e2e")
@RequestMapping("/api/e2e/v1/game-fixtures")
public class E2eGameFixtureController {

  private static final Logger LOG = LoggerFactory.getLogger(E2eGameFixtureController.class);

  @Autowired private E2eGameFixtureService e2eGameFixtureService;

  @Autowired private BuzzerService buzzerService;

  @Operation(
      summary = "Delete an E2E game fixture",
      description =
"""
Deletes all runtime state for a test room.

Workflow:

* Accepts a roomCode path parameter.
* Removes the seeded game and dependent runtime data for that room.
* Used by E2E tests during cleanup.
* Available only when the `e2e` Spring profile is active.

This endpoint is test infrastructure only and must not be enabled in production.
""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Fixture deleted successfully."),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected internal server error while deleting the fixture.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            "{\"error\":\"E999 - Internal Server Error\",\"message\":\"Unexpected internal error.\"}")))
  })
  @DeleteMapping("{roomCode}")
  public ResponseEntity<String> deleteRoom(@RoomCodePath @PathVariable String roomCode) {
    try {
      e2eGameFixtureService.resetRuntimeState(roomCode);
      return ResponseEntity.ok().build();
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }

  @Operation(
      summary = "Create an E2E game fixture",
      description =
"""
Creates a complete game fixture for Playwright/E2E tests.

Workflow:

* Accepts a full fixture payload containing game, teams, categories, albums, tracks, schedules, and interrupts.
* Validates basic request shape and semantic fixture consistency.
* Persists the fixture data required by E2E scenarios.
* Available only when the `e2e` Spring profile is active.

This endpoint is test infrastructure only and must not be enabled in production.
""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Fixture created successfully."),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid roomCode.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            "{\"error\":\"E009 - Invalid e2e game fixture\","
                                + "\"message\":\"Violations: stage must be provided.\"}"))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected internal server error while creating the fixture.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            "{\"error\":\"E999 - Internal Server Error\",\"message\":\"Unexpected internal error.\"}")))
  })
  @PostMapping
  public ResponseEntity<String> createFixture(@Valid @RequestBody E2eGameFixtureRequest request) {
    try {
      e2eGameFixtureService.createFixture(request);
      return ResponseEntity.ok().build();
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }

  @Operation(
      summary = "Attach deterministic catalog content to a product-created E2E room",
      description =
          "Adds only finite album/track data to an existing lobby room. The room and later game "
              + "actions still use the real product flow. Available only under the e2e profile.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Catalog attached successfully."),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid room code, catalog shape, lobby state, or insufficient content.",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected internal server error while attaching the catalog.",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("{roomCode}/catalog")
  public ResponseEntity<String> attachCatalog(
      @RoomCodePath @PathVariable String roomCode,
      @Valid @RequestBody E2eCatalogFixtureRequest request) {
    try {
      e2eGameFixtureService.attachCatalog(roomCode, request);
      return ResponseEntity.ok().build();
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }

  @Operation(
      summary = "Emit one receiver code at the E2E hardware boundary",
      description =
          "Substitutes only RF/serial transport and forwards the code to the real BuzzerService. "
              + "Available only under the e2e profile.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Receiver code forwarded successfully."),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected internal server error while forwarding the receiver code.",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("receiver/{buttonCode}")
  public ResponseEntity<String> receiverButton(
      @Parameter(description = "Receiver button code", required = true) @PathVariable
          String buttonCode) {
    try {
      buzzerService.buzz(buttonCode);
      return ResponseEntity.ok().build();
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }
}
