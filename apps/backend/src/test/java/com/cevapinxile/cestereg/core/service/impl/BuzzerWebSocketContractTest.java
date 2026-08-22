package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class BuzzerWebSocketContractTest {

  @Mock private GameRepository gameRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private InterruptService interruptService;
  @Mock private BroadcastGateway broadcastGateway;

  private final ObjectMapper mapper = new ObjectMapper();
  private BuzzerServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new BuzzerServiceImpl();
    ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
    ReflectionTestUtils.setField(service, "teamRepository", teamRepository);
    ReflectionTestUtils.setField(service, "interruptService", interruptService);
    ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
  }

  @Test
  void buttonClickedFrameLocksTypeNumericButtonCodeAndFieldSet() throws Exception {
    final GameEntity game = lobbyGame();
    when(gameRepository.findActive()).thenReturn(Optional.of(game));
    when(teamRepository.findIdByButtonAndGameId("1671", game.getId())).thenReturn(Optional.empty());

    service.buzz("1671");

    final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(broadcastGateway).toAdmin(eq("AKKU"), payload.capture());
    final Map<?, ?> parsed = mapper.readValue(payload.getValue(), HashMap.class);
    assertEquals("button_clicked", parsed.get("type"));
    assertEquals(1671, parsed.get("buttonCode"));
    assertEquals(2, parsed.size());
    assertFalse(parsed.containsKey("teamId"));
    assertFalse(parsed.containsKey("roomCode"));
    verify(broadcastGateway, never()).toTv(anyString(), anyString());
    verify(broadcastGateway, never()).broadcast(anyString(), anyString());
  }

  private static GameEntity lobbyGame() {
    final GameEntity game = new GameEntity(UUID.randomUUID());
    game.setCode("AKKU");
    game.setStage(0);
    return game;
  }
}
