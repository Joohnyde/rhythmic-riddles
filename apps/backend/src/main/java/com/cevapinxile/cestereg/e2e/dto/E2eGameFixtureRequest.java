package com.cevapinxile.cestereg.e2e.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record E2eGameFixtureRequest(
        // Game
        UUID id,
        String roomCode,
        Integer maxSongs,
        Integer maxAlbums,
        Integer stage,
        // Children
        List<Team> teams,
        List<Category> categories
        ) {

    public record Team(
            UUID id,
            String buttonCode,
            String name,
            String image
            ) {

    }

    public record Category(
            // Category
            UUID id,
            UUID pickedByTeamId,
            Integer ordinalNumber,
            Boolean done,
            // Album
            Album album
            ) {

    }

    public record Album(
            UUID id,
            String name,
            String customQuestion,
            List<Track> tracks
            ) {

    }

    public record Track(
            // Song
            String customAnswer,
            // Runtime
            Schedule schedule
            ) {

    }

    public record Schedule(
            UUID id,
            LocalDateTime startedAt,
            LocalDateTime revealedAt,
            Integer ordinalNumber,
            List<Interrupt> interrupts
            ) {

    }

    public record Interrupt(
            UUID id,
            UUID teamId,
            LocalDateTime arrivedAt,
            LocalDateTime resolvedAt,
            Boolean correct,
            Integer score,
            Integer scenario
            ) {

    }
}
