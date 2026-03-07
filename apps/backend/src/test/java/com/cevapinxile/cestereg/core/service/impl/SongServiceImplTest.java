package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.core.gateway.AssetGateway;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

public class SongServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class SongAssetGatewayDelegation {
    @Mock private AssetGateway assetGateway;

    @InjectMocks private SongServiceImpl songService;

    @Test
    void playSnippetDelegatesToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] expected = new byte[] {1, 2, 3};
      when(assetGateway.readSnippetMp3(songId)).thenReturn(expected);

      final byte[] actual = songService.playSnippet(songId);

      assertArrayEquals(expected, actual);
      verify(assetGateway).readSnippetMp3(songId);
    }

    @Test
    void playAnswerDelegatesToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] expected = new byte[] {4, 5, 6};
      when(assetGateway.readAnswerMp3(songId)).thenReturn(expected);

      final byte[] actual = songService.playAnswer(songId);

      assertArrayEquals(expected, actual);
      verify(assetGateway).readAnswerMp3(songId);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class SongAssetGatewayPayloads {
    @Mock private AssetGateway assetGateway;

    @InjectMocks private SongServiceImpl songService;

    @Test
    void playSnippetDelegatesToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] payload = new byte[] {1, 2, 3};
      when(assetGateway.readSnippetMp3(songId)).thenReturn(payload);

      assertArrayEquals(payload, songService.playSnippet(songId));
      verify(assetGateway).readSnippetMp3(songId);
    }

    @Test
    void playAnswerDelegatesToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] payload = new byte[] {9, 8, 7};
      when(assetGateway.readAnswerMp3(songId)).thenReturn(payload);

      assertArrayEquals(payload, songService.playAnswer(songId));
      verify(assetGateway).readAnswerMp3(songId);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class SongCombinedGatewayDelegation {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;
    @InjectMocks private TeamServiceImpl teamService;

    @Mock private AssetGateway assetGateway;
    @InjectMocks private SongServiceImpl songService;

    @Test
    void playSnippetAndAnswerDelegateToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] snippet = new byte[] {1, 2, 3};
      final byte[] answer = new byte[] {4, 5};
      when(assetGateway.readSnippetMp3(songId)).thenReturn(snippet);
      when(assetGateway.readAnswerMp3(songId)).thenReturn(answer);

      assertArrayEquals(snippet, songService.playSnippet(songId));
      assertArrayEquals(answer, songService.playAnswer(songId));
      verify(assetGateway).readSnippetMp3(songId);
      verify(assetGateway).readAnswerMp3(songId);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class SongReturnValueRegression {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;
    @Mock private AssetGateway assetGateway;

    @InjectMocks private TeamServiceImpl teamService;
    @InjectMocks private SongServiceImpl songService;

    @Test
    void playSnippetDelegatesToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] content = new byte[] {1, 2, 3};
      when(assetGateway.readSnippetMp3(songId)).thenReturn(content);

      final byte[] result = songService.playSnippet(songId);

      assertSame(content, result);
    }

    @Test
    void playAnswerDelegatesToAssetGateway() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] content = new byte[] {4, 5, 6};
      when(assetGateway.readAnswerMp3(songId)).thenReturn(content);

      final byte[] result = songService.playAnswer(songId);

      assertSame(content, result);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class SongExactBytePayloads {
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private TeamRepository teamRepository;
    @Mock private GameRepository gameRepository;
    @Mock private AssetGateway assetGateway;

    @InjectMocks private TeamServiceImpl teamService;
    @InjectMocks private SongServiceImpl songService;

    @Test
    void playSnippetAndAnswerReturnExactGatewayBytes() throws Exception {
      final UUID songId = UUID.randomUUID();
      final byte[] snippet = new byte[] {1, 2};
      final byte[] answer = new byte[] {3, 4};
      when(assetGateway.readSnippetMp3(songId)).thenReturn(snippet);
      when(assetGateway.readAnswerMp3(songId)).thenReturn(answer);

      assertSame(snippet, songService.playSnippet(songId));
      assertSame(answer, songService.playAnswer(songId));
    }
  }
}
