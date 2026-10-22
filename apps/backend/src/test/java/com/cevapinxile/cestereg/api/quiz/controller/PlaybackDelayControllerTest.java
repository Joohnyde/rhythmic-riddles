package com.cevapinxile.cestereg.api.quiz.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PlaybackDelayService service;

  @Test
  void registersCurrentScheduleDelay() throws Exception {
    final UUID schedule = UUID.randomUUID();
    final UUID delay = UUID.randomUUID();
    when(service.registerPlaybackDelay(schedule, "AKKU")).thenReturn(delay);
    mockMvc
        .perform(
            post("/api/v1/games/AKKU/delays")
                .contentType(APPLICATION_JSON)
                .content("{\"scheduleId\":\"" + schedule + "\"}"))
        .andExpect(status().isOk());
    verify(service).registerPlaybackDelay(schedule, "AKKU");
  }

  @Test
  void registrationRequiresBothConnectedClients() throws Exception {
    final UUID schedule = UUID.randomUUID();
    when(service.registerPlaybackDelay(schedule, "AKKU"))
        .thenThrow(new AppNotRegisteredException("Both apps must be present"));
    mockMvc
        .perform(
            post("/api/v1/games/AKKU/delays")
                .contentType(APPLICATION_JSON)
                .content("{\"scheduleId\":\"" + schedule + "\"}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void acknowledgementRequiresBothConnectedClients() throws Exception {
    final UUID attempt = UUID.randomUUID();
    doThrow(new AppNotRegisteredException("Both apps must be present"))
        .when(service)
        .acknowledgePlaybackDelay(attempt, "AKKU");
    mockMvc
        .perform(put("/api/v1/games/AKKU/delays/{id}/acknowledge", attempt))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void acknowledgesCurrentAttempt() throws Exception {
    final UUID delay = UUID.randomUUID();
    mockMvc
        .perform(put("/api/v1/games/AKKU/delays/{id}/acknowledge", delay))
        .andExpect(status().isOk());
    verify(service).acknowledgePlaybackDelay(delay, "AKKU");
  }
}
