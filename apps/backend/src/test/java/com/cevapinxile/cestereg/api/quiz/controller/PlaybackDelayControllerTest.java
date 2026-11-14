package com.cevapinxile.cestereg.api.quiz.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.quiz.dto.response.PlaybackRegistrationResponse;
import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.core.service.PlaybackDelayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaybackDelayController.class)
class PlaybackDelayControllerTest extends ControllerTestSupport {

  private static final long ATTEMPT_AT = 1_799_999_999_000L;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PlaybackDelayService service;

  @Test
  void registersCurrentScheduleDelay() throws Exception {
    final UUID schedule = UUID.randomUUID();
    final UUID delay = UUID.randomUUID();
    when(service.registerPlaybackDelay(schedule, "AKKU", ATTEMPT_AT))
        .thenReturn(new PlaybackRegistrationResponse(delay, ATTEMPT_AT));
    mockMvc
        .perform(
            post("/api/v1/games/AKKU/delays")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"scheduleId\":\"" + schedule + "\",\"clientAttemptAt\":" + ATTEMPT_AT + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.delayId").value(delay.toString()))
        .andExpect(jsonPath("$.clientAttemptAt").value(ATTEMPT_AT));
    verify(service).registerPlaybackDelay(schedule, "AKKU", ATTEMPT_AT);
  }

  @Test
  void registrationRequiresBothConnectedClients() throws Exception {
    final UUID schedule = UUID.randomUUID();
    when(service.registerPlaybackDelay(schedule, "AKKU", ATTEMPT_AT))
        .thenThrow(new AppNotRegisteredException("Both apps must be present"));
    mockMvc
        .perform(
            post("/api/v1/games/AKKU/delays")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"scheduleId\":\"" + schedule + "\",\"clientAttemptAt\":" + ATTEMPT_AT + "}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void acknowledgementRequiresBothConnectedClients() throws Exception {
    final UUID attempt = UUID.randomUUID();
    doThrow(new AppNotRegisteredException("Both apps must be present"))
        .when(service)
        .acknowledgePlaybackDelay(attempt, "AKKU", ATTEMPT_AT);
    mockMvc
        .perform(
            put("/api/v1/games/AKKU/delays/{id}/acknowledge", attempt)
                .contentType(APPLICATION_JSON)
                .content("{\"clientAttemptAt\":" + ATTEMPT_AT + "}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void acknowledgesCurrentAttempt() throws Exception {
    final UUID delay = UUID.randomUUID();
    mockMvc
        .perform(
            put("/api/v1/games/AKKU/delays/{id}/acknowledge", delay)
                .contentType(APPLICATION_JSON)
                .content("{\"clientAttemptAt\":" + ATTEMPT_AT + "}"))
        .andExpect(status().isOk());
    verify(service).acknowledgePlaybackDelay(delay, "AKKU", ATTEMPT_AT);
  }
}
