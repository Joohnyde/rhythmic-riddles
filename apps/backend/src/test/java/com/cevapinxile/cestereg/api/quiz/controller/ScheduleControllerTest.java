package com.cevapinxile.cestereg.api.quiz.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleController.class)
class ScheduleControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ScheduleService scheduleService;

  @Nested
  class ReplaySongTests {

    @Test
    void replaySongReturnsOk() throws Exception {
      UUID scheduleId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/replay", "AKKU", scheduleId))
          .andExpect(status().isOk())
          .andExpect(content().string(""));

      verify(scheduleService).replaySong(scheduleId, "AKKU");
    }

    @Test
    void replaySongDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      UUID scheduleId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/replay", "abc", scheduleId))
          .andExpect(status().isOk());

      verify(scheduleService).replaySong(scheduleId, "abc");
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForReplaySong")
    void replaySongMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      UUID scheduleId = UUID.randomUUID();
      doThrow(derived(status, code, title, message))
          .when(scheduleService)
          .replaySong(scheduleId, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/replay", "AKKU", scheduleId))
          .andExpect(status().is(status))
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(
              content()
                  .string(
                      "{\"error\":\"E"
                          + code
                          + " - "
                          + title
                          + "\", \"message\":\""
                          + message
                          + "\"}"));
    }

    @Test
    void replaySongReturnsInternalServerErrorForUnexpectedException() throws Exception {
      UUID scheduleId = UUID.randomUUID();
      doThrow(new RuntimeException("boom")).when(scheduleService).replaySong(scheduleId, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/replay", "AKKU", scheduleId))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void replaySongAddsCorsHeaderWhenOriginPresent() throws Exception {
      UUID scheduleId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/replay", "AKKU", scheduleId)
                  .header("Origin", "https://example.com"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForReplaySong() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game or schedule not found."),
          Arguments.of(409, "003", "Wrong game-state", "Replay allowed only in stage 2."),
          Arguments.of(503, "004", "App not reachable", "An app is missing."));
    }
  }

  @Nested
  class RevealAnswerTests {

    @Test
    void revealAnswerReturnsOk() throws Exception {
      UUID scheduleId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal", "AKKU", scheduleId))
          .andExpect(status().isOk());

      verify(scheduleService).revealAnswer(scheduleId, "AKKU");
    }

    @Test
    void revealAnswerReturnsBadRequestForMalformedScheduleUuid() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal", "AKKU", "not-a-uuid"))
          .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForRevealAnswer")
    void revealAnswerMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      UUID scheduleId = UUID.randomUUID();
      doThrow(derived(status, code, title, message))
          .when(scheduleService)
          .revealAnswer(scheduleId, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal", "AKKU", scheduleId))
          .andExpect(status().is(status))
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(
              content()
                  .string(
                      "{\"error\":\"E"
                          + code
                          + " - "
                          + title
                          + "\", \"message\":\""
                          + message
                          + "\"}"));
    }

    @Test
    void revealAnswerReturnsInternalServerErrorForUnexpectedException() throws Exception {
      UUID scheduleId = UUID.randomUUID();
      doThrow(new RuntimeException("boom")).when(scheduleService).revealAnswer(scheduleId, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal", "AKKU", scheduleId))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void revealAnswerAddsCorsHeaderWhenOriginPresent() throws Exception {
      UUID scheduleId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal", "AKKU", scheduleId)
                  .header("Origin", "https://example.com"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForRevealAnswer() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game or schedule not found."),
          Arguments.of(409, "003", "Wrong game-state", "Reveal allowed only in stage 2."),
          Arguments.of(503, "004", "App not reachable", "An app is missing."));
    }
  }

  @Nested
  class NextTests {

    @Test
    void nextReturnsOk() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/schedules/next", "AKKU"))
          .andExpect(status().isOk());

      verify(scheduleService).progress("AKKU");
    }

    @Test
    void nextDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/schedules/next", "abc"))
          .andExpect(status().isOk());

      verify(scheduleService).progress("abc");
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForNext")
    void nextMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      doThrow(derived(status, code, title, message)).when(scheduleService).progress("AKKU");

      mockMvc
          .perform(post("/api/v1/games/{roomCode}/schedules/next", "AKKU"))
          .andExpect(status().is(status))
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(
              content()
                  .string(
                      "{\"error\":\"E"
                          + code
                          + " - "
                          + title
                          + "\", \"message\":\""
                          + message
                          + "\"}"));
    }

    @Test
    void nextReturnsInternalServerErrorForUnexpectedException() throws Exception {
      doThrow(new RuntimeException("boom")).when(scheduleService).progress("AKKU");

      mockMvc
          .perform(post("/api/v1/games/{roomCode}/schedules/next", "AKKU"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void nextAddsCorsHeaderWhenOriginPresent() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/next", "AKKU")
                  .header("Origin", "https://example.com"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForNext() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game not found."),
          Arguments.of(409, "003", "Wrong game-state", "Next allowed only in stage 2."),
          Arguments.of(503, "004", "App not reachable", "An app is missing."));
    }
  }

  @Nested
  class ReplayContract {
    @Test
    void replayRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(
              delete(
                  "/api/v1/games/{roomCode}/schedules/{scheduleId}/replay",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(scheduleService);
    }

    @Test
    void replayPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options(
                      "/api/v1/games/{roomCode}/schedules/{scheduleId}/replay",
                      "AKKU",
                      UUID.randomUUID())
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void malformedScheduleIdDoesNotCallReplayService() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/schedules/{scheduleId}/replay", "AKKU", "not-a-uuid"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(scheduleService);
    }
  }

  @Nested
  class RevealContract {
    @Test
    void revealRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(
              delete(
                  "/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(scheduleService);
    }

    @Test
    void revealPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options(
                      "/api/v1/games/{roomCode}/schedules/{scheduleId}/reveal",
                      "AKKU",
                      UUID.randomUUID())
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
  }

  @Nested
  class NextContract {
    @Test
    void nextRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/schedules/next", "AKKU"))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(scheduleService);
    }

    @Test
    void nextReturnsEmptyBodyOnSuccess() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/schedules/next", "AKKU"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }

    @Test
    void nextPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options("/api/v1/games/{roomCode}/schedules/next", "AKKU")
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
  }
}
