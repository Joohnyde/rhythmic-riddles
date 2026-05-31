package com.cevapinxile.cestereg.e2e.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cevapinxile.cestereg.e2e.E2eGameFixtureRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * These tests become meaningful after applying the DTO validation patch in patch/src/main/java.
 * Without annotations on E2eGameFixtureRequest, Bean Validation has nothing to enforce.
 */
@DisplayName("E2E fixture request bean validation")
class E2eGameFixtureRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsMinimalValidFixture() {
    E2eGameFixtureRequest request = validRequest();

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsMissingRequiredRootFieldsBeforeServiceCanTouchDatabase() {
    E2eGameFixtureRequest request = new E2eGameFixtureRequest(null, "", 0, 0, -1, null, null);

    Set<ConstraintViolation<E2eGameFixtureRequest>> violations = validator.validate(request);

    assertThat(paths(violations))
        .contains("id", "roomCode", "maxSongs", "maxAlbums", "stage", "teams", "categories");
  }

  @Test
  void rejectsNegativeOrZeroRootLimitsAndInvalidStage() {
    E2eGameFixtureRequest request =
        new E2eGameFixtureRequest(
            UUID.randomUUID(), "AKKU", 0, -1, 99, List.of(validTeam()), List.of(validCategory()));

    assertThat(paths(validator.validate(request))).contains("maxSongs", "maxAlbums", "stage");
  }

  @Test
  void rejectsBlankTeamFieldsAndNestedInvalidReferences() {
    E2eGameFixtureRequest.Team badTeam = new E2eGameFixtureRequest.Team(null, "", "", null);
    E2eGameFixtureRequest.Interrupt badInterrupt =
        new E2eGameFixtureRequest.Interrupt(null, null, null, LocalDateTime.now(), null, -1, -1);
    E2eGameFixtureRequest.Schedule badSchedule =
        new E2eGameFixtureRequest.Schedule(null, null, null, 0, List.of(badInterrupt));
    E2eGameFixtureRequest.Track badTrack = new E2eGameFixtureRequest.Track("", badSchedule);
    E2eGameFixtureRequest.Album badAlbum =
        new E2eGameFixtureRequest.Album(null, "", "", List.of(badTrack));
    E2eGameFixtureRequest.Category badCategory =
        new E2eGameFixtureRequest.Category(null, null, 0, null, badAlbum);
    E2eGameFixtureRequest request =
        new E2eGameFixtureRequest(
            UUID.randomUUID(), "AKKU", 1, 1, 1, List.of(badTeam), List.of(badCategory));

    Set<String> paths = paths(validator.validate(request));

    assertThat(paths)
        .contains(
            "teams[0].id",
            "teams[0].buttonCode",
            "teams[0].name",
            "categories[0].id",
            "categories[0].done",
            "categories[0].album.id",
            "categories[0].album.name",
            "categories[0].album.tracks[0].schedule.id",
            "categories[0].album.tracks[0].schedule.ordinalNumber",
            "categories[0].album.tracks[0].schedule.interrupts[0].id",
            "categories[0].album.tracks[0].schedule.interrupts[0].arrivedAt",
            "categories[0].album.tracks[0].schedule.interrupts[0].scenario");
  }

  private static E2eGameFixtureRequest validRequest() {
    return new E2eGameFixtureRequest(
        UUID.randomUUID(), "AKKU", 5, 2, 1, List.of(validTeam()), List.of(validCategory()));
  }

  private static E2eGameFixtureRequest.Team validTeam() {
    return new E2eGameFixtureRequest.Team(UUID.randomUUID(), "BTN-1", "Team A", null);
  }

  private static E2eGameFixtureRequest.Category validCategory() {
    UUID teamId = UUID.randomUUID();
    return new E2eGameFixtureRequest.Category(
        UUID.randomUUID(),
        teamId,
        1,
        false,
        new E2eGameFixtureRequest.Album(
            UUID.randomUUID(),
            "Album A",
            "Question A",
            List.of(
                new E2eGameFixtureRequest.Track(
                    "Answer A",
                    new E2eGameFixtureRequest.Schedule(
                        UUID.randomUUID(),
                        LocalDateTime.now(),
                        null,
                        1,
                        List.of(
                            new E2eGameFixtureRequest.Interrupt(
                                UUID.randomUUID(),
                                teamId,
                                LocalDateTime.now(),
                                null,
                                true,
                                10,
                                2)))))));
  }

  private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
  }
}
