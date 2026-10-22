package com.cevapinxile.cestereg.api.quiz.dto.response;

import java.util.UUID;

public record PlaybackRegistrationResponse(UUID delayId, long clientAttemptAt) {}
