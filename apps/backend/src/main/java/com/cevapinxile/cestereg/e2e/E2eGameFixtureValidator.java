package com.cevapinxile.cestereg.e2e;

import com.cevapinxile.cestereg.common.exception.E2eGameFixtureValidationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class E2eGameFixtureValidator {

  public void validateOrThrow(final E2eGameFixtureRequest request)
      throws E2eGameFixtureValidationException {
    final List<String> violations = validate(request);
    if (!violations.isEmpty()) {
      throw new E2eGameFixtureValidationException(violations);
    }
  }

  public List<String> validate(final E2eGameFixtureRequest request) {
    final List<String> violations = new ArrayList<>();
    if (request == null) {
      return List.of("fixture request is required");
    }

    // Keep these checks aligned with E2eGameFixtureRequest Bean Validation.
    // Optional/nested semantic rules live below, not in the DTO annotations.
    if (request.id() == null) {
      violations.add("id is required");
    }
    if (isBlank(request.roomCode())) {
      violations.add("roomCode is required");
    }
    if (request.maxSongs() == null || request.maxSongs() < 1) {
      violations.add("maxSongs must be at least 1");
    }
    if (request.maxAlbums() == null || request.maxAlbums() < 1) {
      violations.add("maxAlbums must be at least 1");
    }
    if (request.stage() == null || request.stage() < 0 || request.stage() > 3) {
      violations.add("stage must be between 0 and 3");
    }
    if (request.teams() == null || request.teams().isEmpty()) {
      violations.add("at least one team is required");
    }
    if (request.categories() == null || request.categories().isEmpty()) {
      violations.add("at least one category is required");
    }

    final Set<UUID> teamIds = teamIds(request, violations);
    validateCategories(request, teamIds, violations);
    validateStageTwoStartedSong(request, violations);
    validateAtMostOneActiveSong(request, violations);
    validateInterrupts(request, teamIds, violations);

    return violations;
  }

  private static Set<UUID> teamIds(
      final E2eGameFixtureRequest request, final List<String> violations) {
    final Set<UUID> teamIds = new HashSet<>();
    if (request.teams() == null) {
      return teamIds;
    }
    for (int i = 0; i < request.teams().size(); i++) {
      final E2eGameFixtureRequest.Team team = request.teams().get(i);
      if (team == null) {
        violations.add("teams[" + i + "] is required");
        continue;
      }
      if (team.id() == null) {
        violations.add("teams[" + i + "].id is required");
      } else if (!teamIds.add(team.id())) {
        violations.add("duplicate team id: " + team.id());
      }
      if (isBlank(team.name())) {
        violations.add("teams[" + i + "].name is required");
      }
      if (isBlank(team.buttonCode())) {
        violations.add("teams[" + i + "].buttonCode is required");
      }
    }
    return teamIds;
  }

  private static void validateCategories(
      final E2eGameFixtureRequest request, final Set<UUID> teamIds, final List<String> violations) {
    if (request.categories() == null) {
      return;
    }
    final Set<Integer> categoryOrdinals = new HashSet<>();
    for (int categoryIndex = 0; categoryIndex < request.categories().size(); categoryIndex++) {
      final E2eGameFixtureRequest.Category category = request.categories().get(categoryIndex);
      final String cp = "categories[" + categoryIndex + "]";
      if (category == null) {
        violations.add(cp + " is required");
        continue;
      }
      if (category.id() == null) {
        violations.add(cp + ".id is required");
      }
      if (category.done() == null) {
        violations.add(cp + ".done is required");
      }
      if (category.pickedByTeamId() != null && !teamIds.contains(category.pickedByTeamId())) {
        violations.add(cp + ".pickedByTeamId must reference an existing team");
      }
      if (category.ordinalNumber() != null) {
        if (category.ordinalNumber() < 1) {
          violations.add(cp + ".ordinalNumber must be positive when chosen");
        }
        if (!categoryOrdinals.add(category.ordinalNumber())) {
          violations.add("duplicate chosen category ordinalNumber: " + category.ordinalNumber());
        }
      }
      validateAlbumAndChosenCategorySchedule(request, category, categoryIndex, violations);
    }
  }

  private static void validateAlbumAndChosenCategorySchedule(
      final E2eGameFixtureRequest request,
      final E2eGameFixtureRequest.Category category,
      final int categoryIndex,
      final List<String> violations) {
    final String cp = "categories[" + categoryIndex + "]";
    final E2eGameFixtureRequest.Album album = category.album();
    if (album == null) {
      violations.add(cp + ".album is required");
      return;
    }
    if (album.id() == null) {
      violations.add(cp + ".album.id is required");
    }
    if (isBlank(album.name())) {
      violations.add(cp + ".album.name is required");
    }
    if (album.tracks() == null || album.tracks().isEmpty()) {
      violations.add(cp + ".album.tracks must not be empty");
      return;
    }

    final List<E2eGameFixtureRequest.Schedule> schedules = schedules(album);

    // Track.schedule is optional in the DTO. It becomes semantically required only for chosen
    // categories.
    if (category.ordinalNumber() == null && !schedules.isEmpty()) {
      violations.add(cp + " is not chosen, so its tracks must not have schedule rows");
    }
    if (category.ordinalNumber() != null
        && request.maxSongs() != null
        && schedules.size() != request.maxSongs()) {
      violations.add(cp + " is chosen, so it must have exactly maxSongs scheduled tracks");
    }
    if (category.ordinalNumber() != null) {
      validateScheduleOrdinals(cp, schedules, request.maxSongs(), violations);
    }

    // done is required by DTO, but true has extra semantic meaning.
    if (Boolean.TRUE.equals(category.done())) {
      if (schedules.isEmpty()) {
        violations.add(cp + ".done cannot be true without scheduled tracks");
      }
      for (int trackIndex = 0; trackIndex < album.tracks().size(); trackIndex++) {
        final E2eGameFixtureRequest.Track track = album.tracks().get(trackIndex);
        if (track != null && track.schedule() != null && track.schedule().revealedAt() == null) {
          violations.add(cp + ".done can be true only when every scheduled track has revealedAt");
          break;
        }
      }
    }
  }

  private static void validateScheduleOrdinals(
      final String cp,
      final List<E2eGameFixtureRequest.Schedule> schedules,
      final Integer maxSongs,
      final List<String> violations) {
    final Set<Integer> ordinals = new HashSet<>();
    for (E2eGameFixtureRequest.Schedule schedule : schedules) {
      // Schedule.ordinalNumber is @NotNull in the DTO, but keep the guard for direct validator unit
      // tests.
      if (schedule.ordinalNumber() == null) {
        violations.add(cp + " scheduled tracks must have ordinalNumber");
      } else if (schedule.ordinalNumber() < 1
          || (maxSongs != null && schedule.ordinalNumber() > maxSongs)) {
        violations.add(cp + " schedule ordinalNumber must be between 1 and maxSongs");
      } else if (!ordinals.add(schedule.ordinalNumber())) {
        violations.add(cp + " has duplicate schedule ordinalNumber: " + schedule.ordinalNumber());
      }
    }
  }

  private static void validateStageTwoStartedSong(
      final E2eGameFixtureRequest request, final List<String> violations) {
    if (!Objects.equals(request.stage(), 2)) {
      return;
    }
    final boolean anyStarted = allSchedules(request).stream().anyMatch(s -> s.startedAt() != null);
    if (!anyStarted) {
      violations.add("stage 2 requires at least one scheduled track with startedAt");
    }
  }

  private static void validateAtMostOneActiveSong(
      final E2eGameFixtureRequest request, final List<String> violations) {
    final long active =
        allSchedules(request).stream()
            .filter(s -> s.startedAt() != null && s.revealedAt() == null)
            .count();
    if (active > 1) {
      violations.add("at most one schedule can be started and not yet revealed");
    }
  }

  private static void validateInterrupts(
      final E2eGameFixtureRequest request, final Set<UUID> teamIds, final List<String> violations) {
    final List<InterruptRef> interrupts = allInterrupts(request);
    for (InterruptRef ref : interrupts) {
      final E2eGameFixtureRequest.Interrupt interrupt = ref.interrupt();
      if (interrupt.id() == null) {
        violations.add(ref.path() + ".id is required");
      }
      if (interrupt.arrivedAt() == null) {
        violations.add(ref.path() + ".arrivedAt is required");
      }

      // Interrupt.teamId is optional. Null means system/crash interrupt.
      if (interrupt.teamId() != null) {
        if (!teamIds.contains(interrupt.teamId())) {
          violations.add(ref.path() + ".teamId must reference an existing team");
        }

        // correct/score are optional for historical/seed flexibility. If one is supplied, require
        // the other
        // because score only makes sense as the team's score after a resolved correct/incorrect
        // answer.
        if (interrupt.score() != null && interrupt.correct() == null) {
          violations.add(
              ref.path() + ".correct is required when score is provided for a team interrupt");
        }
        if (interrupt.correct() != null && interrupt.score() == null) {
          violations.add(
              ref.path() + ".score is required when correct is provided for a team interrupt");
        }
      } else {
        // System interrupts must not carry team-answer result fields.
        if (interrupt.correct() != null) {
          violations.add(ref.path() + ".correct must be null for system interrupts");
        }
        if (interrupt.score() != null && interrupt.scenario() == null) {
          violations.add(ref.path() + ".score must be null for non-scenario system interrupts");
        }
      }
    }

    final List<InterruptRef> ongoingSystem =
        interrupts.stream()
            .filter(ref -> ref.interrupt().teamId() == null && ref.interrupt().resolvedAt() == null)
            .toList();
    if (!ongoingSystem.isEmpty()) {
      final List<InterruptRef> scenarioCarriers =
          ongoingSystem.stream().filter(ref -> ref.interrupt().scenario() != null).toList();
      if (scenarioCarriers.size() != 1) {
        violations.add("exactly one ongoing system interrupt must carry scenario");
      } else {
        final Integer scenario = scenarioCarriers.get(0).interrupt().scenario();
        if (scenario < 0 || scenario > 4 || scenario == 3) {
          violations.add("ongoing system interrupt scenario must be 0, 1, 2, or 4");
        }
      }
      for (InterruptRef ref : ongoingSystem) {
        final E2eGameFixtureRequest.Interrupt interrupt = ref.interrupt();
        if (interrupt.scenario() == null && interrupt.score() != null) {
          violations.add(ref.path() + ".score must be null for non-scenario system interrupts");
        }
      }
    }

    final List<InterruptRef> ongoingTeam =
        interrupts.stream()
            .filter(ref -> ref.interrupt().teamId() != null && ref.interrupt().resolvedAt() == null)
            .toList();
    if (ongoingTeam.size() > 1) {
      violations.add("at most one ongoing team interrupt is allowed");
    }
    if (ongoingTeam.size() == 1 && !ongoingSystem.isEmpty()) {
      final LocalDateTime teamArrivedAt = ongoingTeam.get(0).interrupt().arrivedAt();
      if (teamArrivedAt != null) {
        final boolean teamIsBeforeEverySystem =
            ongoingSystem.stream()
                .map(ref -> ref.interrupt().arrivedAt())
                .filter(Objects::nonNull)
                .allMatch(systemArrivedAt -> teamArrivedAt.isBefore(systemArrivedAt));
        if (!teamIsBeforeEverySystem) {
          violations.add("ongoing team interrupt must arrive before all ongoing system interrupts");
        }
      }
    }

    validateResolutionGroups(interrupts, violations);
  }

  private static void validateResolutionGroups(
      final List<InterruptRef> interrupts, final List<String> violations) {
    final List<InterruptRef> resolved =
        interrupts.stream().filter(ref -> ref.interrupt().resolvedAt() != null).toList();
    if (resolved.isEmpty()) {
      return;
    }

    final Set<LocalDateTime> resolvedTimes = new HashSet<>();
    resolved.forEach(ref -> resolvedTimes.add(ref.interrupt().resolvedAt()));
    if (resolvedTimes.size() > 1) {
      violations.add("interrupts resolved together must have the exact same resolvedAt");
    }

    final boolean hasUnresolved =
        interrupts.stream().anyMatch(ref -> ref.interrupt().resolvedAt() == null);
    if (hasUnresolved) {
      violations.add(
          "fixture cannot mix resolved and unresolved interrupts in the same active interruption group");
    }
  }

  private static List<E2eGameFixtureRequest.Schedule> allSchedules(
      final E2eGameFixtureRequest request) {
    if (request.categories() == null) {
      return List.of();
    }
    return request.categories().stream()
        .filter(Objects::nonNull)
        .map(E2eGameFixtureRequest.Category::album)
        .filter(Objects::nonNull)
        .flatMap(album -> schedules(album).stream())
        .toList();
  }

  private static List<E2eGameFixtureRequest.Schedule> schedules(
      final E2eGameFixtureRequest.Album album) {
    if (album.tracks() == null) {
      return List.of();
    }
    return album.tracks().stream()
        .filter(Objects::nonNull)
        .map(E2eGameFixtureRequest.Track::schedule)
        .filter(Objects::nonNull)
        .toList();
  }

  private static List<InterruptRef> allInterrupts(final E2eGameFixtureRequest request) {
    final List<InterruptRef> refs = new ArrayList<>();
    if (request.categories() == null) {
      return refs;
    }
    for (int c = 0; c < request.categories().size(); c++) {
      final E2eGameFixtureRequest.Category category = request.categories().get(c);
      if (category == null || category.album() == null || category.album().tracks() == null) {
        continue;
      }
      for (int t = 0; t < category.album().tracks().size(); t++) {
        final E2eGameFixtureRequest.Track track = category.album().tracks().get(t);
        if (track == null || track.schedule() == null || track.schedule().interrupts() == null) {
          continue;
        }
        for (int i = 0; i < track.schedule().interrupts().size(); i++) {
          final E2eGameFixtureRequest.Interrupt interrupt = track.schedule().interrupts().get(i);
          if (interrupt != null) {
            refs.add(
                new InterruptRef(
                    "categories[" + c + "].album.tracks[" + t + "].schedule.interrupts[" + i + "]",
                    interrupt));
          }
        }
      }
    }
    return refs.stream().sorted(Comparator.comparing(InterruptRef::path)).toList();
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }

  private record InterruptRef(String path, E2eGameFixtureRequest.Interrupt interrupt) {}
}
