package com.cevapinxile.cestereg.api.quiz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.core.service.TeamService;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeamController.class)
class TeamControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TeamService teamService;

  @Nested
  class CreateTeamTests {

    @Test
    void createTeamReturnsSerializedResponse() throws Exception {
      UUID id = UUID.randomUUID();
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenReturn(new CreateTeamResponse(id, "Team Cyan", "team.png"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content(
                      "{\"name\":\"Team Cyan\",\"buttonCode\":\"BTN-1\",\"image\":\"team.png\"}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(id.toString()))
          .andExpect(jsonPath("$.name").value("Team Cyan"))
          .andExpect(jsonPath("$.image").value("team.png"));

      ArgumentCaptor<CreateTeamRequest> captor = ArgumentCaptor.forClass(CreateTeamRequest.class);
      verify(teamService).createTeam(captor.capture(), eq("AKKU"));
      CreateTeamRequest request = captor.getValue();
      assertEquals("Team Cyan", request.name());
      assertEquals("BTN-1", request.buttonCode());
      assertEquals("team.png", request.image());
    }

    @Test
    void createTeamReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/teams", "AKKU"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void createTeamForwardsNullFieldsWhenPayloadOmitsParameters() throws Exception {
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenReturn(new CreateTeamResponse(UUID.randomUUID(), "Fallback", "fallback.png"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));

      ArgumentCaptor<CreateTeamRequest> captor = ArgumentCaptor.forClass(CreateTeamRequest.class);
      verify(teamService).createTeam(captor.capture(), eq("AKKU"));
      assertNull(captor.getValue().name());
      assertNull(captor.getValue().buttonCode());
      assertNull(captor.getValue().image());
    }

    @Test
    void createTeamDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      when(teamService.createTeam(any(), eq("abc")))
          .thenReturn(new CreateTeamResponse(UUID.randomUUID(), "Team", "img"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "abc")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":\"Team\",\"buttonCode\":\"BTN-1\",\"image\":\"img\"}"))
          .andExpect(status().isOk());

      verify(teamService).createTeam(any(), eq("abc"));
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForCreateTeam")
    void createTeamMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenThrow(derived(status, code, title, message));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content(
                      "{\"name\":\"Team Cyan\",\"buttonCode\":\"BTN-1\",\"image\":\"team.png\"}"))
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
    void createTeamReturnsInternalServerErrorForUnexpectedException() throws Exception {
      when(teamService.createTeam(any(), eq("AKKU"))).thenThrow(new RuntimeException("boom"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content(
                      "{\"name\":\"Team Cyan\",\"buttonCode\":\"BTN-1\",\"image\":\"team.png\"}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void createTeamAddsCorsHeaderWhenOriginPresent() throws Exception {
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenReturn(new CreateTeamResponse(UUID.randomUUID(), "Team", "img"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":\"Team\",\"buttonCode\":\"BTN-1\",\"image\":\"img\"}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForCreateTeam() {
      return Stream.of(
          Arguments.of(
              400, "000", "An argument is missing", "name, buttonCode, and image are required."),
          Arguments.of(404, "001", "Invalid referenced object", "Game or button not found."),
          Arguments.of(409, "003", "Wrong game-state", "Teams can only be created in stage 0."));
    }
  }

  @Nested
  class KickTeamTests {

    @Test
    void kickTeamReturnsOkAndForwardsIds() throws Exception {
      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-42"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));

      verify(teamService).kickTeam("team-42", "AKKU");
    }

    @Test
    void kickTeamDoesNotValidateTeamIdFormatAtControllerLevel() throws Exception {
      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "not-a-uuid"))
          .andExpect(status().isOk());

      verify(teamService).kickTeam("not-a-uuid", "AKKU");
    }

    @Test
    void kickTeamDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/teams/{teamId}", "abc", "team-42"))
          .andExpect(status().isOk());

      verify(teamService).kickTeam("team-42", "abc");
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForKickTeam")
    void kickTeamMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      doThrow(derived(status, code, title, message)).when(teamService).kickTeam("team-42", "AKKU");

      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-42"))
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
    void kickTeamReturnsInternalServerErrorForUnexpectedException() throws Exception {
      doThrow(new RuntimeException("boom")).when(teamService).kickTeam("team-42", "AKKU");

      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-42"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void kickTeamAddsCorsHeaderWhenOriginPresent() throws Exception {
      mockMvc
          .perform(
              delete("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-42")
                  .header("Origin", "https://example.com"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForKickTeam() {
      return Stream.of(
          Arguments.of(404, "001", "Invalid referenced object", "Game or team not found."),
          Arguments.of(
              409, "003", "Wrong game-state", "Kicking teams is only allowed in stage 0."));
    }
  }

  @Nested
  class CreateTeamContract {

    @Test
    void createTeamRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(get("/api/v1/games/{roomCode}/teams", "AKKU"))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(teamService);
    }

    @Test
    void createTeamRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU").contentType("text/plain").content("x"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(teamService);
    }

    @Test
    void createTeamPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options("/api/v1/games/{roomCode}/teams", "AKKU")
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void createTeamMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(teamService);
    }
  }

  @Nested
  class KickTeamContract {

    @Test
    void kickTeamRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-1"))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(teamService);
    }

    @Test
    void kickTeamReturnsEmptyBodyOnSuccess() throws Exception {
      mockMvc
          .perform(delete("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-1"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }

    @Test
    void kickTeamPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options("/api/v1/games/{roomCode}/teams/{teamId}", "AKKU", "team-1")
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "DELETE"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
  }

  @Nested
  class CreateTeamBinding {

    @Test
    void createTeamCoercesNumericNameToStringAndForwardsIt() throws Exception {
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenReturn(new CreateTeamResponse(UUID.randomUUID(), "123", "img"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":123,\"buttonCode\":\"BTN-1\",\"image\":\"team.png\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<CreateTeamRequest> captor = ArgumentCaptor.forClass(CreateTeamRequest.class);
      verify(teamService).createTeam(captor.capture(), eq("AKKU"));
      assertEquals("123", captor.getValue().name());
    }

    @Test
    void createTeamAllowsNullNameAndForwardsItToService() throws Exception {
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenReturn(new CreateTeamResponse(UUID.randomUUID(), "Fallback", "img"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":null,\"buttonCode\":\"BTN-1\",\"image\":\"team.png\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<CreateTeamRequest> captor = ArgumentCaptor.forClass(CreateTeamRequest.class);
      verify(teamService).createTeam(captor.capture(), eq("AKKU"));
      assertNull(captor.getValue().name());
      assertEquals("BTN-1", captor.getValue().buttonCode());
      assertEquals("team.png", captor.getValue().image());
    }

    @Test
    void createTeamUnknownFieldsAreIgnoredWhenMainPayloadIsValid() throws Exception {
      UUID id = UUID.randomUUID();
      when(teamService.createTeam(any(), eq("AKKU")))
          .thenReturn(new CreateTeamResponse(id, "Team", "img"));

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/teams", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content(
                      "{\"name\":\"Team\",\"buttonCode\":\"BTN-1\",\"image\":\"img\",\"extra\":\"ignored\"}"))
          .andExpect(status().isOk())
          .andExpect(content().json("{\"id\":\"" + id + "\",\"name\":\"Team\",\"image\":\"img\"}"));
    }
  }
}
