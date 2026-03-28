package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplWebSocketTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ScheduleServiceImplIteration7TransitionWsTest {
          @Nested
          @ExtendWith(MockitoExtension.class)
          class Stage2SequenceContracts {
            @Mock private GameService gameService;
            @Mock private CategoryService categoryService;
            @Mock private ScheduleRepository scheduleRepository;
            @Mock private InterruptRepository interruptRepository;
            @Mock private PresenceGateway presenceGateway;
            @Mock private BroadcastGateway broadcastGateway;

            @InjectMocks private ScheduleServiceImpl service;

            @Test
            void replaySongResolvesErrorsThenSavesStartTimeThenBroadcastsRepeatExactlyOnce() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = schedule(game, 17.5, 6.0);

              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

              service.replaySong(schedule.getId(), "AKKU");

              assertNotNull(schedule.getStartedAt());
              final InOrder inOrder = inOrder(interruptRepository, scheduleRepository, broadcastGateway);
              inOrder.verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
              inOrder.verify(scheduleRepository).saveAndFlush(schedule);
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());

              final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
              verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
              assertEquals("{\"type\":\"song_repeat\",\"remaining\":17.5}", payload.getValue());
            }

            @Test
            void replaySongRequiresBothAppsAndDoesNotBroadcastFalseResume() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = schedule(game, 17.5, 6.0);

              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

              final AppNotRegisteredException exception =
                  assertThrows(AppNotRegisteredException.class, () -> service.replaySong(schedule.getId(), "AKKU"));

              assertEquals("Both apps need to be present in order to continue", exception.getMessage());
              assertNull(schedule.getRevealedAt());
              verify(interruptRepository, never()).resolveErrors(any(), any());
              verify(scheduleRepository, never()).saveAndFlush(any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            @Test
            void revealAnswerResolvesErrorsThenPersistsRevealThenBroadcastsRevealExactlyOnce() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = schedule(game, 17.5, 6.0);

              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

              service.revealAnswer(schedule.getId(), "AKKU");

              assertNotNull(schedule.getRevealedAt());
              final InOrder inOrder = inOrder(interruptRepository, scheduleRepository, broadcastGateway);
              inOrder.verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
              inOrder.verify(scheduleRepository).saveAndFlush(schedule);
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), eq("{\"type\":\"song_reveal\"}"));
            }

            @Test
            void revealAnswerRequiresBothAppsAndDoesNotBroadcastFalseReveal() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity schedule = schedule(game, 17.5, 6.0);

              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

              final AppNotRegisteredException exception =
                  assertThrows(AppNotRegisteredException.class, () -> service.revealAnswer(schedule.getId(), "AKKU"));

              assertEquals("Both apps need to be present in order to continue", exception.getMessage());
              assertNull(schedule.getRevealedAt());
              verify(interruptRepository, never()).resolveErrors(any(), any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            @Test
            void progressBroadcastsSongNextWithRecoveryFieldsAfterPersistingNextSong() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity lastPlayed = schedule(game, 11.0, 5.0);
              final ScheduleEntity nextSong = schedule(game, 23.0, 7.0);
              nextSong.setStartedAt(null);

              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
              when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
              when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(nextSong));

              service.progress("AKKU");

              assertNotNull(nextSong.getStartedAt());
              final InOrder inOrder = inOrder(interruptRepository, scheduleRepository, broadcastGateway);
              inOrder.verify(interruptRepository).resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
              inOrder.verify(scheduleRepository).saveAndFlush(nextSong);
              inOrder.verify(broadcastGateway).broadcast(eq("AKKU"), anyString());
              verify(gameService, never()).changeStage(any(Integer.class), anyString());

              final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
              verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
              assertTrue(payload.getValue().contains("\"type\":\"song_next\""));
              assertTrue(payload.getValue().contains("\"scheduleId\":\"" + nextSong.getId() + "\""));
              assertTrue(payload.getValue().contains("\"songId\":\"" + nextSong.getTrackId().getSongId().getId() + "\""));
              assertTrue(payload.getValue().contains("\"remaining\":23.0"));
            }

            @Test
            void progressChangesStageInsteadOfBroadcastingSongNextWhenPlaylistIsFinished() throws Exception {
              final GameEntity game = game("AKKU");
              final ScheduleEntity lastPlayed = schedule(game, 11.0, 5.0);

              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
              when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
              when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.empty());
              when(categoryService.finishAndNext(game)).thenReturn(3);

              service.progress("AKKU");

              verify(interruptRepository).resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
              verify(categoryService).finishAndNext(game);
              verify(gameService).changeStage(3, "AKKU");
              verify(scheduleRepository, never()).saveAndFlush(any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            @Test
            void progressRequiresBothAppsAndDoesNotBroadcastFalseNextSong() throws Exception {
              final GameEntity game = game("AKKU");
              when(gameService.findByCode("AKKU", 2)).thenReturn(game);
              when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

              final AppNotRegisteredException exception =
                  assertThrows(AppNotRegisteredException.class, () -> service.progress("AKKU"));

              assertEquals("Both apps need to be present in order to continue", exception.getMessage());
              verify(scheduleRepository, never()).findLastPlayed(any());
              verify(broadcastGateway, never()).broadcast(anyString(), anyString());
            }

            private GameEntity game(final String code) {
              final GameEntity game = new GameEntity(UUID.randomUUID());
              game.setCode(code);
              game.setStage(2);
              return game;
            }

            private ScheduleEntity schedule(final GameEntity game, final double snippetDuration, final double answerDuration) {
              final SongEntity song = new SongEntity(UUID.randomUUID());
              song.setSnippetDuration(snippetDuration);
              song.setAnswerDuration(answerDuration);
              song.setName("Track " + snippetDuration);
              song.setAuthors("Artist");
              final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
              final TrackEntity track = new TrackEntity(UUID.randomUUID());
              track.setAlbumId(album);
              track.setSongId(song);
              final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
              category.setGameId(game);
              final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
              schedule.setCategoryId(category);
              schedule.setTrackId(track);
              schedule.setStartedAt(LocalDateTime.now().minusSeconds(5));
              return schedule;
            }
          }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ScheduleServiceImplStage2BroadcastSerializationTest {
            @Mock
            private GameService gameService;
            @Mock
            private CategoryService categoryService;
            @Mock
            private ScheduleRepository scheduleRepository;
            @Mock
            private InterruptRepository interruptRepository;
            @Mock
            private PresenceGateway presenceGateway;
            @Mock
            private BroadcastGateway broadcastGateway;

            private ScheduleServiceImpl service;
            private final ObjectMapper mapper = new ObjectMapper();

            @BeforeEach
            void setUp() {
                service = new ScheduleServiceImpl();
                ReflectionTestUtils.setField(service, "gameService", gameService);
                ReflectionTestUtils.setField(service, "categoryService", categoryService);
                ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
                ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
                ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
                ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
            }

            @Test
            void replaySongBroadcastHasStableRawShapeAndExactlyOneDelivery() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final UUID scheduleId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final ScheduleEntity schedule = mock(ScheduleEntity.class);
                final TrackEntity track = mock(TrackEntity.class);
                final SongEntity song = mock(SongEntity.class);

                when(gameService.findByCode("ROOM", 2)).thenReturn(game);
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
                when(schedule.getTrackId()).thenReturn(track);
                when(track.getSongId()).thenReturn(song);
                when(song.getSnippetDuration()).thenReturn(18.75d);

                service.replaySong(scheduleId, "ROOM");

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("song_repeat", parsed.get("type"));
                assertEquals(18.75d, ((Number) parsed.get("remaining")).doubleValue(), 0.0001d);
                assertEquals(2, parsed.size());
                verify(scheduleRepository).saveAndFlush(schedule);
                verify(interruptRepository).resolveErrors(eq(scheduleId), any());
            }

            @Test
            void revealAnswerBroadcastRemainsMinimalTypeOnlyContract() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final UUID scheduleId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final ScheduleEntity schedule = mock(ScheduleEntity.class);

                when(gameService.findByCode("ROOM", 2)).thenReturn(game);
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

                service.revealAnswer(scheduleId, "ROOM");

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("song_reveal", parsed.get("type"));
                assertEquals(1, parsed.size());
                assertFalse(parsed.containsKey("remaining"));
                assertFalse(parsed.containsKey("scheduleId"));
            }

            @Test
            void progressNextSongBroadcastContainsRequiredSongFieldsAndNoRevealLeak() throws Exception {
                final UUID gameId = UUID.randomUUID();
                final UUID scheduleId = UUID.randomUUID();
                final UUID songId = UUID.randomUUID();
                final GameEntity game = new GameEntity(gameId);
                final ScheduleEntity lastPlayed = mock(ScheduleEntity.class);
                final ScheduleEntity nextSong = mock(ScheduleEntity.class);
                final TrackEntity track = mock(TrackEntity.class);
                final SongEntity song = mock(SongEntity.class);
                final AlbumEntity album = mock(AlbumEntity.class);

                when(gameService.findByCode("ROOM", 2)).thenReturn(game);
                when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
                when(scheduleRepository.findLastPlayed(gameId)).thenReturn(lastPlayed);
                when(lastPlayed.getId()).thenReturn(UUID.randomUUID());
                when(scheduleRepository.findNext(gameId)).thenReturn(Optional.of(nextSong));
                when(nextSong.getTrackId()).thenReturn(track);
                when(nextSong.getId()).thenReturn(scheduleId);
                when(track.getAlbumId()).thenReturn(album);
                when(track.getSongId()).thenReturn(song);
                when(album.getCustomQuestion()).thenReturn("Question?");
                when(track.getCustomAnswer()).thenReturn("Answer!");
                when(song.getId()).thenReturn(songId);
                when(song.getAnswerDuration()).thenReturn(7.5d);
                when(song.getSnippetDuration()).thenReturn(21.0d);

                service.progress("ROOM");

                final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
                final Map<?, ?> parsed = mapper.readValue(captor.getValue(), HashMap.class);
                assertEquals("song_next", parsed.get("type"));
                assertEquals(songId.toString(), parsed.get("songId"));
                assertEquals("Question?", parsed.get("question"));
                assertEquals("Answer!", parsed.get("answer"));
                assertEquals(scheduleId.toString(), parsed.get("scheduleId"));
                assertEquals(7.5d, ((Number) parsed.get("answerDuration")).doubleValue(), 0.0001d);
                assertEquals(21.0d, ((Number) parsed.get("remaining")).doubleValue(), 0.0001d);
                assertFalse(parsed.containsKey("revealed"));
                assertFalse(parsed.containsKey("error"));
                assertFalse(parsed.containsKey("answeringTeam"));
                verify(scheduleRepository).saveAndFlush(nextSong);
            }
    }

}
