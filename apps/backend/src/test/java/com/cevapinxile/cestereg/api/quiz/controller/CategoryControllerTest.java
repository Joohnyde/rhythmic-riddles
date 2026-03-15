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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.CategoryPreview;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.core.service.CategoryService;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
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

@WebMvcTest(CategoryController.class)
class CategoryControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CategoryService categoryService;

  @Nested
  class PickAlbumTests {

    @Test
    void pickAlbumReturnsSerializedLastCategory() throws Exception {
      UUID categoryId = UUID.randomUUID();
      UUID teamId = UUID.randomUUID();
      LastCategory response = new LastCategory();
      response.setCategoryId(categoryId);
      response.setChosenCategoryPreview(new CategoryPreview("Best of 2000s", "cat.png"));
      response.setPickedByTeam(new CreateTeamResponse(teamId, "Team Cyan", "team.png"));
      response.setStarted(true);
      response.setOrdinalNumber(7);

      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU"))).thenReturn(response);

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":\"" + teamId + "\"}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
          .andExpect(jsonPath("$.chosenCategoryPreview.title").value("Best of 2000s"))
          .andExpect(jsonPath("$.chosenCategoryPreview.image").value("cat.png"))
          .andExpect(jsonPath("$.pickedByTeam.id").value(teamId.toString()))
          .andExpect(jsonPath("$.pickedByTeam.name").value("Team Cyan"))
          .andExpect(jsonPath("$.pickedByTeam.image").value("team.png"))
          .andExpect(jsonPath("$.started").value(true))
          .andExpect(jsonPath("$.ordinalNumber").value(7));

      ArgumentCaptor<TeamIdRequest> captor = ArgumentCaptor.forClass(TeamIdRequest.class);
      verify(categoryService).pickAlbum(eq(categoryId), captor.capture(), eq("AKKU"));
      assertEquals(teamId, captor.getValue().teamId());
    }

    @Test
    void pickAlbumReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(
              put(
                  "/api/v1/games/{roomCode}/categories/{categoryId}/pick",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isBadRequest());
    }

    @Test
    void pickAlbumReturnsBadRequestForMalformedCategoryUuid() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", "not-a-uuid")
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void pickAlbumForwardsNullTeamIdWhenPayloadOmitsIt() throws Exception {
      UUID categoryId = UUID.randomUUID();
      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU")))
          .thenReturn(new LastCategory());

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));

      ArgumentCaptor<TeamIdRequest> captor = ArgumentCaptor.forClass(TeamIdRequest.class);
      verify(categoryService).pickAlbum(eq(categoryId), captor.capture(), eq("AKKU"));
      assertNull(captor.getValue().teamId());
    }

    @Test
    void pickAlbumDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      UUID categoryId = UUID.randomUUID();
      when(categoryService.pickAlbum(eq(categoryId), any(), eq("abc")))
          .thenReturn(new LastCategory());

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "abc", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
          .andExpect(status().isOk());

      verify(categoryService).pickAlbum(eq(categoryId), any(), eq("abc"));
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForPickAlbum")
    void pickAlbumMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      UUID categoryId = UUID.randomUUID();
      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU")))
          .thenThrow(derived(status, code, title, message));

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
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
    void pickAlbumReturnsInternalServerErrorForUnexpectedException() throws Exception {
      UUID categoryId = UUID.randomUUID();
      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU")))
          .thenThrow(new RuntimeException("boom"));

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void pickAlbumAddsCorsHeaderWhenOriginPresent() throws Exception {
      UUID categoryId = UUID.randomUUID();
      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU")))
          .thenReturn(new LastCategory());

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForPickAlbum() {
      return Stream.of(
          Arguments.of(400, "000", "An argument is missing", "teamId is required."),
          Arguments.of(
              422, "002", "Malformed argument", "Request body must contain a valid teamId."),
          Arguments.of(
              404, "001", "Invalid referenced object", "Game, team, or category not found."),
          Arguments.of(
              409, "003", "Wrong game-state", "Category picking is only allowed in stage 1."),
          Arguments.of(503, "004", "App not reachable", "TV app is not reachable."));
    }
  }

  @Nested
  class StartCategoryTests {

    @Test
    void startCategoryReturnsOk() throws Exception {
      UUID categoryId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/categories/{categoryId}/start", "AKKU", categoryId))
          .andExpect(status().isOk())
          .andExpect(content().string(""));

      verify(categoryService).startCategory(categoryId, "AKKU");
    }

    @Test
    void startCategoryDoesNotValidateRoomCodeFormatAtControllerLevel() throws Exception {
      UUID categoryId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/categories/{categoryId}/start", "abc", categoryId))
          .andExpect(status().isOk());

      verify(categoryService).startCategory(categoryId, "abc");
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForStartCategory")
    void startCategoryMapsDerivedExceptions(int status, String code, String title, String message)
        throws Exception {
      UUID categoryId = UUID.randomUUID();
      doThrow(derived(status, code, title, message))
          .when(categoryService)
          .startCategory(categoryId, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/categories/{categoryId}/start", "AKKU", categoryId))
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
    void startCategoryReturnsInternalServerErrorForUnexpectedException() throws Exception {
      UUID categoryId = UUID.randomUUID();
      doThrow(new RuntimeException("boom")).when(categoryService).startCategory(categoryId, "AKKU");

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/categories/{categoryId}/start", "AKKU", categoryId))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void startCategoryAddsCorsHeaderWhenOriginPresent() throws Exception {
      UUID categoryId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/categories/{categoryId}/start", "AKKU", categoryId)
                  .header("Origin", "https://example.com"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForStartCategory() {
      return Stream.of(
          Arguments.of(
              422, "002", "Malformed argument", "Category exists but is not part of the game."),
          Arguments.of(404, "001", "Invalid referenced object", "Category not found."),
          Arguments.of(
              409, "003", "Wrong game-state", "Starting a category is only allowed in stage 1."));
    }
  }

  @Nested
  class PickAlbumContract {

    @Test
    void pickAlbumRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(
              delete(
                  "/api/v1/games/{roomCode}/categories/{categoryId}/pick",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(categoryService);
    }

    @Test
    void pickAlbumRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              put(
                      "/api/v1/games/{roomCode}/categories/{categoryId}/pick",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType("text/plain")
                  .content("x"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(categoryService);
    }

    @Test
    void pickAlbumMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              put(
                      "/api/v1/games/{roomCode}/categories/{categoryId}/pick",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(categoryService);
    }

    @Test
    void pickAlbumPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options(
                      "/api/v1/games/{roomCode}/categories/{categoryId}/pick",
                      "AKKU",
                      UUID.randomUUID())
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "PUT"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
  }

  @Nested
  class StartCategoryContract {

    @Test
    void startCategoryRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(
              put(
                  "/api/v1/games/{roomCode}/categories/{categoryId}/start",
                  "AKKU",
                  UUID.randomUUID()))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(categoryService);
    }

    @Test
    void startCategoryPreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options(
                      "/api/v1/games/{roomCode}/categories/{categoryId}/start",
                      "AKKU",
                      UUID.randomUUID())
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void malformedCategoryIdDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games/{roomCode}/categories/{categoryId}/start", "AKKU", "not-a-uuid"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(categoryService);
    }
  }

  @Nested
  class PickAlbumBinding {

    @Test
    void pickAlbumRejectsNumericTeamId() throws Exception {
      mockMvc
          .perform(
              put(
                      "/api/v1/games/{roomCode}/categories/{categoryId}/pick",
                      "AKKU",
                      UUID.randomUUID())
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":123}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void pickAlbumAllowsExplicitNullTeamIdAndForwardsIt() throws Exception {
      UUID categoryId = UUID.randomUUID();

      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU")))
          .thenReturn(new LastCategory());

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":null}"))
          .andExpect(status().isOk());

      ArgumentCaptor<TeamIdRequest> captor = ArgumentCaptor.forClass(TeamIdRequest.class);
      verify(categoryService).pickAlbum(eq(categoryId), captor.capture(), eq("AKKU"));
      Assertions.assertNull(captor.getValue().teamId());
    }

    @Test
    void pickAlbumIgnoresUnknownFieldsWhenMainPayloadIsValid() throws Exception {
      UUID categoryId = UUID.randomUUID();
      when(categoryService.pickAlbum(eq(categoryId), any(), eq("AKKU")))
          .thenReturn(new LastCategory());

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/categories/{categoryId}/pick", "AKKU", categoryId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"teamId\":\"" + UUID.randomUUID() + "\",\"extra\":\"ignored\"}"))
          .andExpect(status().isOk());
    }
  }
}
