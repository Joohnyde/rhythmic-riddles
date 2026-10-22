package com.cevapinxile.cestereg.api.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "PlaybackRegistrationRequest")
public record PlaybackRegistrationRequest(
    @Schema(description = "Current song schedule ID") UUID scheduleId,
    @Schema(description = "TV-generated playback attempt timestamp in Unix milliseconds")
        Long clientAttemptAt) {}
