package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import java.time.LocalDateTime;
import java.util.UUID;

final class ServiceImplTestDataFactory {
  private ServiceImplTestDataFactory() {}

  static GameEntity game() {
    return game("AKKU", 0);
  }

  static GameEntity game(final String code, final int stage) {
    final GameEntity game = new GameEntity(UUID.randomUUID());
    game.setCode(code);
    game.setStage(stage);
    game.setDate(LocalDateTime.now().minusHours(1));
    return game;
  }

  static TeamEntity team(final GameEntity game, final String name) {
    final TeamEntity team = new TeamEntity(UUID.randomUUID());
    team.setGameId(game);
    team.setName(name);
    team.setImage(name + ".png");
    return team;
  }

  static TeamScoreProjection projection(final UUID teamId, final Integer score) {
    return projection(teamId, "Name", "img.png", UUID.randomUUID(), score);
  }

  static TeamScoreProjection projection(
      final UUID teamId,
      final String name,
      final String image,
      final UUID scheduleId,
      final Integer score) {
    return new TeamScoreProjection() {
      @Override
      public UUID getTeam() {
        return teamId;
      }

      @Override
      public String getImage() {
        return image;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Integer getScore() {
        return score;
      }

      @Override
      public UUID getSchedule() {
        return scheduleId;
      }
    };
  }
}
