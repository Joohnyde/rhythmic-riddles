package com.cevapinxile.cestereg.api.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PlaybackAcknowledgementRequest")
public record PlaybackAcknowledgementRequest(
    @Schema(description = "Original registration timestamp, in Unix milliseconds")
        Long clientAttemptAt) {}
