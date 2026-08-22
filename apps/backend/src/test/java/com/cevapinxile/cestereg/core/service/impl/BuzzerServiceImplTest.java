package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuzzerServiceImplTest {

  @Mock private GameRepository gameRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private InterruptService interruptService;
  @Mock private BroadcastGateway broadcastGateway;

  private BuzzerServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new BuzzerServiceImpl();
    ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
    ReflectionTestUtils.setField(service, "teamRepository", teamRepository);
    ReflectionTestUtils.setField(service, "interruptService", interruptService);
    ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
  }

  @Nested
  class LobbyStage {

    @Test
    void unassignedButtonIsPublishedToAdminOnly() {
      final GameEntity game = game(0);
      when(gameRepository.findActive()).thenReturn(Optional.of(game));
      when(teamRepository.findIdByButtonAndGameId("1671", game.getId()))
          .thenReturn(Optional.empty());

      service.buzz("1671");

      verify(broadcastGateway).toAdmin("AKKU", "{\"type\":\"button_clicked\",\"buttonCode\":1671}");
      verifyNoInteractions(interruptService);
    }

    @Test
    void assignedButtonIsIgnored() {
      final GameEntity game = game(0);
      when(gameRepository.findActive()).thenReturn(Optional.of(game));
      when(teamRepository.findIdByButtonAndGameId("1671", game.getId()))
          .thenReturn(Optional.of(UUID.randomUUID()));

      service.buzz("1671");

      verifyNoInteractions(interruptService, broadcastGateway);
    }
  }

  @Nested
  class SongsStage {

    @Test
    void assignedButtonInterruptsForItsTeam() throws Exception {
      final GameEntity game = game(2);
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findActive()).thenReturn(Optional.of(game));
      when(teamRepository.findIdByButtonAndGameId("1671", game.getId()))
          .thenReturn(Optional.of(teamId));

      service.buzz("1671");

      verify(interruptService).interrupt("AKKU", teamId);
      verifyNoInteractions(broadcastGateway);
    }

    @Test
    void unassignedButtonDoesNotInterruptOrPublish() {
      final GameEntity game = game(2);
      when(gameRepository.findActive()).thenReturn(Optional.of(game));
      when(teamRepository.findIdByButtonAndGameId("1671", game.getId()))
          .thenReturn(Optional.empty());

      service.buzz("1671");

      verifyNoInteractions(interruptService, broadcastGateway);
    }

    @Test
    void rejectedInterruptIsIgnored() throws Exception {
      final GameEntity game = game(2);
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findActive()).thenReturn(Optional.of(game));
      when(teamRepository.findIdByButtonAndGameId("1671", game.getId()))
          .thenReturn(Optional.of(teamId));
      org.mockito.Mockito.doThrow(new InvalidReferencedObjectException("stale interrupt"))
          .when(interruptService)
          .interrupt("AKKU", teamId);

      assertDoesNotThrow(() -> service.buzz("1671"));
    }
  }

  @Test
  void ignoresBuzzWhenThereIsNoActiveGame() {
    when(gameRepository.findActive()).thenReturn(Optional.empty());

    service.buzz("1671");

    verifyNoInteractions(teamRepository, interruptService, broadcastGateway);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void stagesOneAndThreeAreIgnoredWithoutLookingUpATeam(final int stage) {
    when(gameRepository.findActive()).thenReturn(Optional.of(game(stage)));

    service.buzz("1671");

    verifyNoInteractions(teamRepository, interruptService, broadcastGateway);
  }

  @Test
  void malformedButtonLookupIsIgnored() {
    final GameEntity game = game(0);
    when(gameRepository.findActive()).thenReturn(Optional.of(game));
    when(teamRepository.findIdByButtonAndGameId("not-a-number", game.getId()))
        .thenThrow(new NumberFormatException("bad buzzer code"));

    assertDoesNotThrow(() -> service.buzz("not-a-number"));

    verify(broadcastGateway, never())
        .toAdmin(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    verifyNoInteractions(interruptService);
  }

  private static GameEntity game(final int stage) {
    final GameEntity game = new GameEntity(UUID.randomUUID());
    game.setCode("AKKU");
    game.setStage(stage);
    return game;
  }
}
