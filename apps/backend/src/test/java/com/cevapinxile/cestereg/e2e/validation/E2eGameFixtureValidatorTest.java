package com.cevapinxile.cestereg.e2e.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.cevapinxile.cestereg.e2e.E2eGameFixtureRequest;
import com.cevapinxile.cestereg.e2e.E2eGameFixtureValidator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("E2E fixture semantic validator")
class E2eGameFixtureValidatorTest {

  private final E2eGameFixtureValidator validator = new E2eGameFixtureValidator();

  @Test
  void acceptsChosenCategoryWhenOrdinalIsPresentAndExactlyMaxSongsSchedulesExist() {
    assertThat(validator.validate(validStageTwoFixture())).isEmpty();
  }

  @Test
  void acceptsUnchosenCategoryWhenOrdinalIsNullAndNoScheduleRowsExistYet() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            1,
            2,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    null,
                    null,
                    false,
                    album(List.of(track("Unscheduled", null))))));

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsUnchosenCategoryThatAlreadyContainsScheduleRows() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        request(
            1,
            1,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    null,
                    null,
                    false,
                    album(List.of(track("A", schedule(1, now, null, List.of())))))));

    assertThat(validator.validate(request)).anyMatch(v -> v.contains("is not chosen"));
  }

  @Test
  void rejectsDuplicateChosenCategoryOrdinals() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        request(
            1,
            1,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(List.of(track("A", schedule(1, now, null, List.of()))))),
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(List.of(track("B", schedule(1, null, null, List.of())))))));

    assertThat(validator.validate(request)).contains("duplicate chosen category ordinalNumber: 1");
  }

  @Test
  void rejectsChosenCategoryWhenScheduledTrackCountDoesNotEqualMaxSongs() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        request(
            1,
            3,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(List.of(track("A", schedule(1, now, null, List.of())))))));

    assertThat(validator.validate(request))
        .anyMatch(v -> v.contains("exactly maxSongs scheduled tracks"));
  }

  @Test
  void rejectsDoneCategoryIfAnyScheduledTrackIsNotRevealedYet() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        request(
            1,
            2,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    true,
                    album(
                        List.of(
                            track(
                                "A",
                                schedule(1, now.minusMinutes(2), now.minusMinutes(1), List.of())),
                            track("B", schedule(2, now, null, List.of())))))));

    assertThat(validator.validate(request)).anyMatch(v -> v.contains("done can be true only"));
  }

  @Test
  void acceptsDoneCategoryWhenEveryScheduledTrackHasRevealedAt() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        request(
            1,
            2,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    true,
                    album(
                        List.of(
                            track(
                                "A",
                                schedule(1, now.minusMinutes(4), now.minusMinutes(3), List.of())),
                            track(
                                "B",
                                schedule(
                                    2, now.minusMinutes(2), now.minusMinutes(1), List.of())))))));

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsStageTwoWhenNoScheduledTrackHasStartedAt() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            2,
            1,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(List.of(track("A", schedule(1, null, null, List.of())))))));

    assertThat(validator.validate(request))
        .contains("stage 2 requires at least one scheduled track with startedAt");
  }

  @Test
  void rejectsMoreThanOneStartedButUnrevealedSong() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        request(
            2,
            2,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(
                        List.of(
                            track("A", schedule(1, now, null, List.of())),
                            track("B", schedule(2, now.plusSeconds(10), null, List.of())))))));

    assertThat(validator.validate(request))
        .contains("at most one schedule can be started and not yet revealed");
  }

  @Test
  void acceptsMultipleOngoingSystemInterruptsWhenExactlyOneCarriesScenario() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(UUID.randomUUID(), null, now.plusSeconds(1), null, null, null, 2),
                interrupt(UUID.randomUUID(), null, now.plusSeconds(2), null, null, null, null)));

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsOngoingSystemInterruptsWhenNoInterruptCarriesScenario() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(UUID.randomUUID(), null, now.plusSeconds(1), null, null, null, null),
                interrupt(UUID.randomUUID(), null, now.plusSeconds(2), null, null, null, null)));

    assertThat(validator.validate(request))
        .contains("exactly one ongoing system interrupt must carry scenario");
  }

  @Test
  void rejectsOngoingSystemInterruptsWhenMoreThanOneInterruptCarriesScenario() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(UUID.randomUUID(), null, now.plusSeconds(1), null, null, null, 1),
                interrupt(UUID.randomUUID(), null, now.plusSeconds(2), null, null, null, 2)));

    assertThat(validator.validate(request))
        .contains("exactly one ongoing system interrupt must carry scenario");
  }

  @Test
  void rejectsScenarioThreeForOngoingSystemInterrupt() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(interrupt(UUID.randomUUID(), null, now.plusSeconds(1), null, null, null, 3)));

    assertThat(validator.validate(request))
        .contains("ongoing system interrupt scenario must be 0, 1, 2, or 4");
  }

  @Test
  void rejectsTeamInterruptThatReferencesUnknownTeam() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    now.plusSeconds(1),
                    null,
                    true,
                    10,
                    null)));

    assertThat(validator.validate(request))
        .anyMatch(v -> v.contains("teamId must reference an existing team"));
  }

  @Test
  void rejectsMoreThanOneOngoingTeamInterrupt() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(UUID.randomUUID(), teamId, now.plusSeconds(1), null, true, 10, null),
                interrupt(UUID.randomUUID(), teamId, now.plusSeconds(2), null, false, 10, null)));

    assertThat(validator.validate(request))
        .contains("at most one ongoing team interrupt is allowed");
  }

  @Test
  void rejectsOngoingTeamInterruptThatArrivesAfterOngoingSystemInterrupt() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(UUID.randomUUID(), null, now.plusSeconds(1), null, null, null, 2),
                interrupt(UUID.randomUUID(), teamId, now.plusSeconds(2), null, true, 10, null)));

    assertThat(validator.validate(request))
        .contains("ongoing team interrupt must arrive before all ongoing system interrupts");
  }

  @Test
  void rejectsInterruptWithoutArrivedAt() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId, List.of(interrupt(UUID.randomUUID(), null, null, null, null, null, 2)));

    assertThat(validator.validate(request)).anyMatch(v -> v.contains("arrivedAt is required"));
  }

  @Test
  void acceptsResolvedInterruptGroupWhenAllInterruptsHaveSameResolvedAt() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    LocalDateTime resolvedAt = now.plusSeconds(15);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(
                    UUID.randomUUID(), teamId, now.plusSeconds(1), resolvedAt, false, 10, null),
                interrupt(UUID.randomUUID(), null, now.plusSeconds(2), resolvedAt, null, null, 4)));

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsPartiallyResolvedInterruptGroup() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(
                    UUID.randomUUID(),
                    teamId,
                    now.plusSeconds(1),
                    now.plusSeconds(10),
                    false,
                    10,
                    null),
                interrupt(UUID.randomUUID(), null, now.plusSeconds(2), null, null, null, 4)));

    assertThat(validator.validate(request))
        .contains(
            "fixture cannot mix resolved and unresolved interrupts in the same active interruption group");
  }

  @Test
  void rejectsResolvedInterruptGroupWithDifferentResolvedAtValues() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    E2eGameFixtureRequest request =
        fixtureWithInterrupts(
            teamId,
            List.of(
                interrupt(
                    UUID.randomUUID(),
                    teamId,
                    now.plusSeconds(1),
                    now.plusSeconds(10),
                    true,
                    10,
                    null),
                interrupt(
                    UUID.randomUUID(),
                    null,
                    now.plusSeconds(2),
                    now.plusSeconds(11),
                    null,
                    null,
                    0)));

    assertThat(validator.validate(request))
        .contains("interrupts resolved together must have the exact same resolvedAt");
  }

  @Test
  void chosenCategoryMustHaveExactlyMaxSongsSchedulesWithOrdinalsOneThroughMaxSongs() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            2,
            3,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(
                        List.of(
                            track("A", schedule(1, started(), null, List.of())),
                            track("B", schedule(2, null, null, List.of())),
                            track("C", schedule(3, null, null, List.of())))))));

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsChosenCategoryWithMissingScheduleOrdinalGapBecauseMaxSongsSchedulesAreNotComplete() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            2,
            3,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(
                        List.of(
                            track("A", schedule(1, started(), null, List.of())),
                            track("B", schedule(2, null, null, List.of())))))));

    assertThat(validator.validate(request))
        .anyMatch(v -> v.contains("exactly maxSongs scheduled tracks"));
  }

  @Test
  void rejectsChosenCategoryWithDuplicateScheduleOrdinals() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            2,
            3,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(
                        List.of(
                            track("A", schedule(1, started(), null, List.of())),
                            track("B", schedule(1, null, null, List.of())),
                            track("C", schedule(3, null, null, List.of())))))));

    assertThat(validator.validate(request))
        .anyMatch(v -> v.contains("duplicate schedule ordinalNumber"));
  }

  @Test
  void rejectsChosenCategoryWithScheduleOrdinalOutsideMaxSongsRange() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            2,
            3,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(
                        List.of(
                            track("A", schedule(1, started(), null, List.of())),
                            track("B", schedule(2, null, null, List.of())),
                            track("C", schedule(4, null, null, List.of())))))));

    assertThat(validator.validate(request))
        .anyMatch(v -> v.contains("schedule ordinalNumber must be between 1 and maxSongs"));
  }

  @Test
  void stableValidationErrorListsDuplicateCategoryOrdinalsAndBadDoneFlagTogether() {
    UUID teamId = UUID.randomUUID();
    E2eGameFixtureRequest request =
        request(
            2,
            1,
            List.of(team(teamId)),
            List.of(
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    true,
                    album(List.of(track("A", schedule(1, started(), null, List.of()))))),
                category(
                    UUID.randomUUID(),
                    teamId,
                    1,
                    false,
                    album(List.of(track("B", schedule(1, null, null, List.of())))))));

    assertThat(validator.validate(request))
        .contains("duplicate chosen category ordinalNumber: 1")
        .anyMatch(v -> v.contains("done can be true only"));
  }

  private static E2eGameFixtureRequest validStageTwoFixture() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    return request(
        2,
        2,
        List.of(team(teamId)),
        List.of(
            category(
                UUID.randomUUID(),
                teamId,
                1,
                false,
                album(
                    List.of(
                        track("A", schedule(1, now, null, List.of())),
                        track("B", schedule(2, null, null, List.of())))))));
  }

  private static E2eGameFixtureRequest fixtureWithInterrupts(
      UUID teamId, List<E2eGameFixtureRequest.Interrupt> interrupts) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 20, 0);
    return request(
        2,
        1,
        List.of(team(teamId)),
        List.of(
            category(
                UUID.randomUUID(),
                teamId,
                1,
                false,
                album(List.of(track("A", schedule(1, now, null, interrupts)))))));
  }

  private static E2eGameFixtureRequest request(
      int stage,
      int maxSongs,
      List<E2eGameFixtureRequest.Team> teams,
      List<E2eGameFixtureRequest.Category> categories) {
    return new E2eGameFixtureRequest(
        UUID.randomUUID(), "AKKU", maxSongs, 10, stage, teams, categories);
  }

  private static E2eGameFixtureRequest.Team team(UUID teamId) {
    return new E2eGameFixtureRequest.Team(
        teamId, "BTN-" + teamId.toString().substring(0, 4), "Team A", null);
  }

  private static E2eGameFixtureRequest.Category category(
      UUID id,
      UUID pickedByTeamId,
      Integer ordinalNumber,
      boolean done,
      E2eGameFixtureRequest.Album album) {
    return new E2eGameFixtureRequest.Category(id, pickedByTeamId, ordinalNumber, done, album);
  }

  private static E2eGameFixtureRequest.Album album(List<E2eGameFixtureRequest.Track> tracks) {
    return new E2eGameFixtureRequest.Album(UUID.randomUUID(), "Album A", "Question A", tracks);
  }

  private static E2eGameFixtureRequest.Track track(
      String answer, E2eGameFixtureRequest.Schedule schedule) {
    return new E2eGameFixtureRequest.Track(answer, schedule);
  }

  private static E2eGameFixtureRequest.Schedule schedule(
      int ordinal,
      LocalDateTime startedAt,
      LocalDateTime revealedAt,
      List<E2eGameFixtureRequest.Interrupt> interrupts) {
    return new E2eGameFixtureRequest.Schedule(
        UUID.randomUUID(), startedAt, revealedAt, ordinal, interrupts);
  }

  private static LocalDateTime started() {
    return LocalDateTime.of(2026, 5, 31, 20, 0);
  }

  private static E2eGameFixtureRequest.Interrupt interrupt(
      UUID id,
      UUID teamId,
      LocalDateTime arrivedAt,
      LocalDateTime resolvedAt,
      Boolean correct,
      Integer score,
      Integer scenario) {
    return new E2eGameFixtureRequest.Interrupt(
        id, teamId, arrivedAt, resolvedAt, correct, score, scenario);
  }
}
