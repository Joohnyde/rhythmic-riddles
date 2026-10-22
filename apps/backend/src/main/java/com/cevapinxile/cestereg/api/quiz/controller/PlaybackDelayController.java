package com.cevapinxile.cestereg.api.quiz.controller;

import static com.cevapinxile.cestereg.api.support.ApiErrorResponses.handleApiException;

import com.cevapinxile.cestereg.api.quiz.dto.request.ScheduleIdRequest;
import com.cevapinxile.cestereg.common.util.RoomCodePath;
import com.cevapinxile.cestereg.core.service.PlaybackDelayService;
import io.swagger.v3.oas.annotations.Operation;
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

  @Operation(summary = "Register TV audio loading for the current song",
      description = "TV registers before starting the audio. Returns an attempt UUID; "
          + "duplicate registration reuses an open attempt. Admin waits for acknowledgement.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Attempt registered or already pending"),
      @ApiResponse(responseCode = "404", description = "Schedule or room not found"),
      @ApiResponse(responseCode = "409", description = "Room not in the song stage or schedule stale"),
      @ApiResponse(responseCode = "503", description = "Admin or TV disconnected"),
      @ApiResponse(responseCode = "422", description = "Schedule belongs to a different room")
  })
  @PostMapping
  public ResponseEntity<?> createDelay(@RoomCodePath @PathVariable String roomCode,
      @RequestBody ScheduleIdRequest request) {
    try {
      return ResponseEntity.ok(
          playbackDelayService.registerPlaybackDelay(request.scheduleId(), roomCode));
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }

  @Operation(summary = "Acknowledge that TV audio playback has begun progressing",
      description = "Idempotent acknowledgement for the current schedule attempt. "
          + "Requires both clients present and notifies Admin after commit; stale IDs are rejected.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Acknowledged or already acknowledged"),
      @ApiResponse(responseCode = "404", description = "Attempt not found"),
      @ApiResponse(responseCode = "409", description = "Attempt belongs to a stale replay"),
      @ApiResponse(responseCode = "503", description = "Admin or TV disconnected"),
      @ApiResponse(responseCode = "422", description = "Attempt belongs to a different room")
  })
  @PutMapping("/{delayId}/acknowledge")
  public ResponseEntity<?> acknowledgeDelay(@RoomCodePath @PathVariable String roomCode,
      @PathVariable UUID delayId) {
    try {
      playbackDelayService.acknowledgePlaybackDelay(delayId, roomCode);
      return ResponseEntity.ok().build();
    } catch (Exception ex) {
      return handleApiException(LOG, ex);
    }
  }
}
