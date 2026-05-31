package com.cevapinxile.cestereg.e2e;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record E2eGameFixtureRequest(
    @NotNull UUID id,
    @NotBlank @Pattern(regexp = "^[A-Z0-9]{4}$") String roomCode,
    @NotNull @Min(1) Integer maxSongs,
    @NotNull @Min(1) Integer maxAlbums,
    @NotNull @Min(0) @Max(3) Integer stage,
    @NotEmpty List<@Valid Team> teams,
    @NotEmpty List<@Valid Category> categories) {

  public record Team(
      @NotNull UUID id, @NotBlank String buttonCode, @NotBlank String name, String image) {}

  public record Category(
      @NotNull UUID id,
      UUID pickedByTeamId,
      @Nullable @Min(1) Integer ordinalNumber,
      @NotNull Boolean done,
      @Valid @NotNull Album album) {}

  public record Album(
      @NotNull UUID id,
      @NotBlank String name,
      String customQuestion,
      @NotEmpty List<@Valid Track> tracks) {}

  public record Track(String customAnswer, @Valid Schedule schedule) {}

  public record Schedule(
      @NotNull UUID id,
      LocalDateTime startedAt,
      LocalDateTime revealedAt,
      @NotNull @Min(1) Integer ordinalNumber,
      List<@Valid Interrupt> interrupts) {}

  public record Interrupt(
      @NotNull UUID id,
      UUID teamId,
      @NotNull LocalDateTime arrivedAt,
      LocalDateTime resolvedAt,
      Boolean correct,
      Integer score,
      @Nullable @Min(0) @Max(4) Integer scenario) {}
}
