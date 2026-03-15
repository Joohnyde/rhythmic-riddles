package com.cevapinxile.cestereg.api.quiz.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.core.service.InterruptService;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InterruptController.class)
class InterruptControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InterruptService interruptService;

  @Nested
  class InterruptTests {

    @Test
    void interruptReturnsOkAndForwardsTeamId() throws Exception {
      UUID teamId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":\"" + teamId + "\"}"))
          .andExpect(status().isOk());

      verify(interruptService).interrupt("AKKU", teamId);
    }

    @Test
    void interruptAllowsSystemInterruptByForwardingNullTeamId() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk());

      verify(interruptService).interrupt("AKKU", null);
    }

    @Test
    void interruptReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/interrupts", "AKKU"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void interruptDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "abc")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk());

      verify(interruptService).interrupt("abc", null);
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForInterrupt")
    void interruptMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      doThrow(derived(status, code, title, message)).when(interruptService).interrupt("AKKU", null);

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
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
    void interruptReturnsInternalServerErrorForUnexpectedException() throws Exception {
      doThrow(new RuntimeException("boom")).when(interruptService).interrupt("AKKU", null);

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void interruptAddsCorsHeaderWhenOriginPresent() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForInterrupt() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game or team not found."),
          Arguments.of(409, "006", "Guess wasn't allowed", "Interrupt rejected."),
          Arguments.of(401, "005", "Unauthorized request", "Team does not belong to game."));
    }
  }

  @Nested
  class ResolveErrorsTests {

    @Test
    void resolveErrorsReturnsOkAndForwardsScheduleId() throws Exception {
      UUID scheduleId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scheduleId\":\"" + scheduleId + "\"}"))
          .andExpect(status().isOk());

      verify(interruptService).resolveErrors(scheduleId, "AKKU");
    }

    @Test
    void resolveErrorsReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void resolveErrorsForwardsNullScheduleIdWhenPayloadOmitsIt() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk());

      verify(interruptService).resolveErrors(null, "AKKU");
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForResolveErrors")
    void resolveErrorsMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      doThrow(derived(status, code, title, message))
          .when(interruptService)
          .resolveErrors(null, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
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
    void resolveErrorsReturnsInternalServerErrorForUnexpectedException() throws Exception {
      doThrow(new RuntimeException("boom")).when(interruptService).resolveErrors(null, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void resolveErrorsAddsCorsHeaderWhenOriginPresent() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForResolveErrors() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game or schedule not found."),
          Arguments.of(409, "003", "Wrong game-state", "Allowed only in stage 2."),
          Arguments.of(
              503, "004", "App not reachable", "Cannot resume because an app is missing."));
    }
  }

  @Nested
  class AnswerTests {

    @Test
    void answerReturnsOkAndForwardsAnswerRequest() throws Exception {
      UUID answerId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/{answerId}/answer", "AKKU", answerId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":true}"))
          .andExpect(status().isOk());

      ArgumentCaptor<AnswerRequest> captor = ArgumentCaptor.forClass(AnswerRequest.class);
      verify(interruptService).answer(eq(answerId), captor.capture(), eq("AKKU"));
      assertTrue(captor.getValue().correct());
    }

    @Test
    void answerReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(
              post(
                  "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isBadRequest());
    }

    @Test
    void answerReturnsBadRequestWhenCorrectFlagMissingFromPayload() throws Exception {
      mockMvc
          .perform(
              post(
                      "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForAnswer")
    void answerMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      UUID answerId = UUID.randomUUID();
      doThrow(derived(status, code, title, message))
          .when(interruptService)
          .answer(eq(answerId), any(), eq("AKKU"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/{answerId}/answer", "AKKU", answerId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":false}"))
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
    void answerReturnsInternalServerErrorForUnexpectedException() throws Exception {
      UUID answerId = UUID.randomUUID();
      doThrow(new RuntimeException("boom"))
          .when(interruptService)
          .answer(eq(answerId), any(), eq("AKKU"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/{answerId}/answer", "AKKU", answerId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":true}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void answerAddsCorsHeaderWhenOriginPresent() throws Exception {
      UUID answerId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/{answerId}/answer", "AKKU", answerId)
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":true}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForAnswer() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game or interrupt not found."),
          Arguments.of(
              422, "002", "Malformed argument", "Interrupt does not belong to the specified game."),
          Arguments.of(
              409,
              "003",
              "Wrong game-state",
              "Answering allowed only for unresolved stage-2 interrupts."),
          Arguments.of(503, "004", "App not reachable", "An app is missing."));
    }
  }

  @Nested
  class SavePreviousScenarioTests {

    @Test
    void savePreviousScenarioReturnsOkAndForwardsScenario() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":2}"))
          .andExpect(status().isOk());

      verify(interruptService).savePreviousScenario(2, "AKKU");
    }

    @Test
    void savePreviousScenarioReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(put("/api/v1/games/{roomCode}/ui/scenario", "AKKU"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void savePreviousScenarioReturnsBadRequestWhenScenarioMissingFromPayload() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForSavePreviousScenario")
    void savePreviousScenarioMapsDerivedExceptions(
        int status, String code, String title, String message) throws Exception {
      doThrow(derived(status, code, title, message))
          .when(interruptService)
          .savePreviousScenario(2, "AKKU");

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":2}"))
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
    void savePreviousScenarioReturnsInternalServerErrorForUnexpectedException() throws Exception {
      doThrow(new RuntimeException("boom")).when(interruptService).savePreviousScenario(2, "AKKU");

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":2}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void savePreviousScenarioAddsCorsHeaderWhenOriginPresent() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":2}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForSavePreviousScenario() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game not found."),
          Arguments.of(
              422, "002", "Malformed argument", "scenario must be 0..4 and must not be 3."),
          Arguments.of(409, "003", "Wrong game-state", "Saving scenario allowed only in stage 2."));
    }
  }

  @Nested
  class InterruptContract {

    @Test
    void interruptRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/interrupts", "AKKU"))
          .andExpect(status().isMethodNotAllowed())
          .andDo(
              result -> {
                System.out.println("Mastika");
                System.out.println("status = " + result.getResponse().getStatus());
                System.out.println(
                    "handler = " + result.getResponse().getHeader("X-Exception-Handler"));
                System.out.println("body = " + result.getResponse().getContentAsString());
                System.out.println("resolved = " + result.getResolvedException());
              });
      verifyNoInteractions(interruptService);
    }

    @Test
    void interruptRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType("text/plain")
                  .content("x"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(interruptService);
    }

    @Test
    void interruptMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(interruptService);
    }

    @Test
    void interruptPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
  }

  @Nested
  class ResolveErrorsContract {

    @Test
    void resolveErrorsRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(put("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU"))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(interruptService);
    }

    @Test
    void resolveErrorsRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType("text/plain")
                  .content("x"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(interruptService);
    }

    @Test
    void resolveErrorsMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scheduleId\":"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(interruptService);
    }
  }

  @Nested
  class AnswerContract {

    @Test
    void answerRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(
              put(
                  "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(interruptService);
    }

    @Test
    void answerRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              post(
                      "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType("text/plain")
                  .content("x"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(interruptService);
    }

    @Test
    void answerMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              post(
                      "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(interruptService);
    }

    @Test
    void malformedAnswerIdDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/{answerId}/answer", "AKKU", "not-a-uuid")
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":true}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(interruptService);
    }
  }

  @Nested
  class SaveScenarioContract {

    @Test
    void saveScenarioRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":2}"))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(interruptService);
    }

    @Test
    void saveScenarioRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType("text/plain")
                  .content("2"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(interruptService);
    }

    @Test
    void saveScenarioMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(interruptService);
    }
  }

  @Nested
  class InterruptBinding {

    @Test
    void interruptRejectsNumericTeamIdIfEndpointRequiresUuidOrStringShape() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":123}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @Test
    void interruptAllowsExplicitNullTeamIdAndForwardsIt() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
          .andExpect(status().isOk());

      Mockito.verify(interruptService).interrupt("AKKU", null);
    }
  }

  @Nested
  class ResolveBinding {

    @Test
    void resolveErrorsRejectsWrongJsonTypeForScheduleId() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scheduleId\":123}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @Test
    void resolveErrorsAllowsExplicitNullScheduleIdAndForwardsIt() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/interrupts/system/resolve", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scheduleId\":null}"))
          .andExpect(status().isOk());

      Mockito.verify(interruptService).resolveErrors(null, "AKKU");
    }
  }

  @Nested
  class AnswerBinding {

    @Test
    void answerRejectsStringForBooleanField() throws Exception {
      mockMvc
          .perform(
              post(
                      "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":\"yes\"}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @Test
    void answerRejectsExplicitNullBooleanField() throws Exception {
      mockMvc
          .perform(
              post(
                      "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":null}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @Test
    void answerIgnoresUnknownFieldsWhenMainPayloadIsValid() throws Exception {
      mockMvc
          .perform(
              post(
                      "/api/v1/games/{roomCode}/interrupts/{answerId}/answer",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{\"correct\":true,\"extra\":\"ignored\"}"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  class ScenarioBinding {

    @Test
    void savePreviousScenarioRejectsStringForNumericField() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":\"two\"}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @Test
    void savePreviousScenarioRejectsExplicitNullScenario() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":null}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(interruptService);
    }

    @Test
    void savePreviousScenarioIgnoresUnknownFieldsWhenMainPayloadIsValid() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/ui/scenario", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"scenario\":2,\"extra\":\"ignored\"}"))
          .andExpect(status().isOk());
    }
  }
}
