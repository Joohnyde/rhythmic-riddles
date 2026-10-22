package com.cevapinxile.cestereg.api.quiz.controller;

import static com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException;

import com.cevapinxile.cestereg.api.quiz.dto.request.PlaybackAcknowledgementRequest;
import com.cevapinxile.cestereg.api.quiz.dto.request.PlaybackRegistrationRequest;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import com.cevapinxile.cestereg.core.service.PlaybackDelayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * @author denijal
 */
@Tag(name = "Playback delays")
@RestController
@RequestMapping("/api/v1/games/{roomCode}/delays")
@CrossOrigin(origins = "*")
public class PlaybackDelayController {

  private static final Logger LOG = LoggerFactory.getLogger(PlaybackDelayController.class);

  @Autowired private PlaybackDelayService playbackDelayService;

  @Operation(
      summary = "Register TV audio loading for the current song",
      description =
          "TV registers before starting audio and sends an attempt timestamp. "
              + "Returns the delay ID and that timestamp; obsolete timestamps are rejected.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Delay ID and echoed attempt timestamp"),
    @ApiResponse(responseCode = "404", description = "Schedule or room not found"),
    @ApiResponse(
        responseCode = "409",
        description = "Wrong stage, stale schedule or superseded attempt"),
    @ApiResponse(responseCode = "503", description = "Admin or TV disconnected"),
    @ApiResponse(
        responseCode = "422",
        description = "Invalid timestamp or schedule belongs to a different room"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PostMapping
  public ResponseEntity<?> createDelay(
      @RoomCodePath @PathVariable String roomCode,
      @RequestBody PlaybackRegistrationRequest request) {
    try {
      return ResponseEntity.ok(
          playbackDelayService.registerPlaybackDelay(
              request.scheduleId(), roomCode, request.clientAttemptAt()));
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }

  @Operation(
      summary = "Acknowledge that TV audio playback has begun progressing",
      description =
          "Idempotent acknowledgement with the registered client attempt timestamp. "
              + "Requires both clients and notifies Admin after commit; stale attempts are rejected.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Acknowledged or already acknowledged"),
    @ApiResponse(responseCode = "404", description = "Attempt not found"),
    @ApiResponse(
        responseCode = "409",
        description = "Attempt superseded or belongs to a stale replay"),
    @ApiResponse(responseCode = "503", description = "Admin or TV disconnected"),
    @ApiResponse(
        responseCode = "422",
        description = "Invalid timestamp or attempt belongs to a different room"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PutMapping("/{delayId}/acknowledge")
  public ResponseEntity<?> acknowledgeDelay(
      @RoomCodePath @PathVariable String roomCode,
      @Parameter(description = "Playback delay identifier") @PathVariable UUID delayId,
      @RequestBody PlaybackAcknowledgementRequest request) {
    try {
      playbackDelayService.acknowledgePlaybackDelay(delayId, roomCode, request.clientAttemptAt());
      return ResponseEntity.ok().build();
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }
}
