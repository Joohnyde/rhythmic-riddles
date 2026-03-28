package com.cevapinxile.cestereg.runtime.broadcast;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class WebSocketBroadcastGatewayTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class WebSocketBroadcastGatewayIsolationStressTest {
            @Mock
            private SessionRegistry registry;

            private WebSocketBroadcastGateway gateway;

            @BeforeEach
            void setUp() {
                gateway = new WebSocketBroadcastGateway(registry);
            }

            @Nested
            class RoomIsolationStress {
                @Test
                void repeatedBroadcastsToRoomANeverLeakToRoomB() throws Exception {
                    final WebSocketSession adminA = mock(WebSocketSession.class);
                    final WebSocketSession tvA = mock(WebSocketSession.class);
                    final WebSocketSession adminB = mock(WebSocketSession.class);
                    final WebSocketSession tvB = mock(WebSocketSession.class);

                    when(registry.getAdminSession("ROOM-A")).thenReturn(adminA);
                    when(registry.getTvSession("ROOM-A")).thenReturn(tvA);

                    gateway.broadcast("ROOM-A", "welcome-a-1");
                    gateway.broadcast("ROOM-A", "welcome-a-2");
                    gateway.broadcast("ROOM-A", "welcome-a-3");

                    verify(adminA, times(3)).sendMessage(any(TextMessage.class));
                    verify(tvA, times(3)).sendMessage(any(TextMessage.class));
                    verify(adminB, never()).sendMessage(any(TextMessage.class));
                    verify(tvB, never()).sendMessage(any(TextMessage.class));
                }

                @Test
                void directedAdminSendNeverTouchesTvOrOtherRooms() throws Exception {
                    final WebSocketSession adminA = mock(WebSocketSession.class);
                    final WebSocketSession tvA = mock(WebSocketSession.class);
                    final WebSocketSession adminB = mock(WebSocketSession.class);

                    when(registry.getAdminSession("ROOM-A")).thenReturn(adminA);

                    gateway.toAdmin("ROOM-A", "admin-only");

                    verify(adminA).sendMessage(any(TextMessage.class));
                    verify(tvA, never()).sendMessage(any(TextMessage.class));
                    verify(adminB, never()).sendMessage(any(TextMessage.class));
                }

                @Test
                void directedTvSendNeverTouchesAdminOrOtherRooms() throws Exception {
                    final WebSocketSession adminA = mock(WebSocketSession.class);
                    final WebSocketSession tvA = mock(WebSocketSession.class);
                    final WebSocketSession tvB = mock(WebSocketSession.class);

                    when(registry.getTvSession("ROOM-A")).thenReturn(tvA);

                    gateway.toTv("ROOM-A", "tv-only");

                    verify(tvA).sendMessage(any(TextMessage.class));
                    verify(adminA, never()).sendMessage(any(TextMessage.class));
                    verify(tvB, never()).sendMessage(any(TextMessage.class));
                }
            }

            @Nested
            class BrokenRecipients {
                @Test
                void broadcastContinuesToOtherRecipientWhenAdminSendFails() throws Exception {
                    final WebSocketSession admin = mock(WebSocketSession.class);
                    final WebSocketSession tv = mock(WebSocketSession.class);
                    when(registry.getAdminSession("ROOM")).thenReturn(admin);
                    when(registry.getTvSession("ROOM")).thenReturn(tv);
                    doThrow(new IOException("boom")).when(admin).sendMessage(any(TextMessage.class));

                    gateway.broadcast("ROOM", "snapshot");

                    verify(admin).sendMessage(any(TextMessage.class));
                    verify(tv).sendMessage(any(TextMessage.class));
                }

                @Test
                void sendToSomeoneSkipsClosedSocket() throws Exception {
                    final WebSocketSession socket = mock(WebSocketSession.class);
                    when(socket.isOpen()).thenReturn(false);

                    gateway.sendToSomeone(socket, "payload");

                    verify(socket, never()).sendMessage(any(TextMessage.class));
                }
            }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class WebSocketBroadcastGatewayRoutingTest {
            @Mock
            private SessionRegistry registry;

            private WebSocketBroadcastGateway gateway;

            @BeforeEach
            void setUp() {
                gateway = new WebSocketBroadcastGateway(registry);
            }

            @Nested
            class DirectedRouting {
                @Test
                void toTvSendsOnlyToTvSessionForRoom() throws Exception {
                    final WebSocketSession tv = mock(WebSocketSession.class);
                    when(registry.getTvSession("ROOM-A")).thenReturn(tv);

                    gateway.toTv("ROOM-A", "payload");

                    final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
                    verify(tv).sendMessage(captor.capture());
                    assertEquals("payload", captor.getValue().getPayload());
                    verify(registry, never()).getAdminSession("ROOM-A");
                }

                @Test
                void toAdminSendsOnlyToAdminSessionForRoom() throws Exception {
                    final WebSocketSession admin = mock(WebSocketSession.class);
                    when(registry.getAdminSession("ROOM-A")).thenReturn(admin);

                    gateway.toAdmin("ROOM-A", "payload");

                    final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
                    verify(admin).sendMessage(captor.capture());
                    assertEquals("payload", captor.getValue().getPayload());
                    verify(registry, never()).getTvSession("ROOM-A");
                }

                @Test
                void broadcastSendsToBothSessionsForSameRoom() throws Exception {
                    final WebSocketSession admin = mock(WebSocketSession.class);
                    final WebSocketSession tv = mock(WebSocketSession.class);
                    when(registry.getAdminSession("ROOM-A")).thenReturn(admin);
                    when(registry.getTvSession("ROOM-A")).thenReturn(tv);

                    gateway.broadcast("ROOM-A", "snapshot");

                    verify(admin).sendMessage(any(TextMessage.class));
                    verify(tv).sendMessage(any(TextMessage.class));
                }

                @Test
                void broadcastDoesNotLeakAcrossRooms() throws Exception {
                    final WebSocketSession adminA = mock(WebSocketSession.class);
                    final WebSocketSession tvA = mock(WebSocketSession.class);
                    final WebSocketSession adminB = mock(WebSocketSession.class);
                    final WebSocketSession tvB = mock(WebSocketSession.class);
                    when(registry.getAdminSession("ROOM-A")).thenReturn(adminA);
                    when(registry.getTvSession("ROOM-A")).thenReturn(tvA);

                    gateway.broadcast("ROOM-A", "snapshot-A");

                    verify(adminA).sendMessage(any(TextMessage.class));
                    verify(tvA).sendMessage(any(TextMessage.class));
                    verify(adminB, never()).sendMessage(any(TextMessage.class));
                    verify(tvB, never()).sendMessage(any(TextMessage.class));
                }
            }

            @Nested
            class MissingOrBrokenRecipients {
                @Test
                void toTvSilentlySkipsMissingSession() throws Exception {
                    when(registry.getTvSession("ROOM-A")).thenReturn(null);

                    gateway.toTv("ROOM-A", "payload");

                    verify(registry).getTvSession("ROOM-A");
                }

                @Test
                void toAdminSilentlySkipsMissingSession() throws Exception {
                    when(registry.getAdminSession("ROOM-A")).thenReturn(null);

                    gateway.toAdmin("ROOM-A", "payload");

                    verify(registry).getAdminSession("ROOM-A");
                }

                @Test
                void broadcastSurvivesIoFailureOnTvAndStillAttemptsAdmin() throws Exception {
                    final WebSocketSession tv = mock(WebSocketSession.class);
                    final WebSocketSession admin = mock(WebSocketSession.class);
                    when(registry.getTvSession("ROOM-A")).thenReturn(tv);
                    when(registry.getAdminSession("ROOM-A")).thenReturn(admin);
                    doThrow(new IOException("boom")).when(tv).sendMessage(any(TextMessage.class));

                    gateway.broadcast("ROOM-A", "payload");

                    verify(tv).sendMessage(any(TextMessage.class));
                    verify(admin).sendMessage(any(TextMessage.class));
                }

                @Test
                void sendToSomeoneSkipsNullSocket() throws Exception {
                    gateway.sendToSomeone(null, "payload");
                }

                @Test
                void sendToSomeoneSkipsClosedSocket() throws Exception {
                    final WebSocketSession socket = mock(WebSocketSession.class);
                    when(socket.isOpen()).thenReturn(false);

                    gateway.sendToSomeone(socket, "payload");

                    verify(socket, never()).sendMessage(any(TextMessage.class));
                }

                @Test
                void sendToSomeoneWritesPayloadToOpenSocket() throws Exception {
                    final WebSocketSession socket = mock(WebSocketSession.class);
                    when(socket.isOpen()).thenReturn(true);

                    gateway.sendToSomeone(socket, "payload");

                    final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
                    verify(socket).sendMessage(captor.capture());
                    assertEquals("payload", captor.getValue().getPayload());
                }
            }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class WebSocketBroadcastToRoomTest {
          @Mock private SessionRegistry sessionRegistry;
          @Mock private WebSocketSession adminSession;
          @Mock private WebSocketSession tvSession;
          @Mock private WebSocketSession directSession;

          private WebSocketBroadcastGateway gateway;

          @BeforeEach
          void setUp() {
            gateway = new WebSocketBroadcastGateway(sessionRegistry);
          }

          @Test
          void broadcastSendsSameFrameToBothRegisteredClientSlots() throws Exception {
            when(sessionRegistry.getAdminSession("AKKU")).thenReturn(adminSession);
            when(sessionRegistry.getTvSession("AKKU")).thenReturn(tvSession);

            gateway.broadcast("AKKU", "{\"type\":\"song_next\"}");

            verify(adminSession).sendMessage(argThat(messageWithPayload("{\"type\":\"song_next\"}")));
            verify(tvSession).sendMessage(argThat(messageWithPayload("{\"type\":\"song_next\"}")));
          }

          @Test
          void broadcastStillSendsToAdminWhenTvIsMissing() throws Exception {
            when(sessionRegistry.getAdminSession("AKKU")).thenReturn(adminSession);
            when(sessionRegistry.getTvSession("AKKU")).thenReturn(null);

            gateway.broadcast("AKKU", "{\"type\":\"answer\"}");

            verify(adminSession).sendMessage(argThat(messageWithPayload("{\"type\":\"answer\"}")));
          }

          @Test
          void toTvDoesNothingWhenTvSessionIsMissing() throws Exception {
            when(sessionRegistry.getTvSession("AKKU")).thenReturn(null);

            gateway.toTv("AKKU", "{\"type\":\"new_team\"}");

            verify(sessionRegistry).getTvSession("AKKU");
          }

          @Test
          void toAdminDoesNothingWhenAdminSessionIsMissing() throws Exception {
            when(sessionRegistry.getAdminSession("AKKU")).thenReturn(null);

            gateway.toAdmin("AKKU", "{\"type\":\"song_reveal\"}");

            verify(sessionRegistry).getAdminSession("AKKU");
          }

          @Test
          void sendToSomeoneSkipsNullAndClosedSockets() throws Exception {
            gateway.sendToSomeone(null, "{\"type\":\"welcome\"}");

            when(directSession.isOpen()).thenReturn(false);
            gateway.sendToSomeone(directSession, "{\"type\":\"welcome\"}");

            verify(directSession, never()).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
          }

          @Test
          void sendToSomeoneWritesExactPayloadToOpenSocket() throws Exception {
            when(directSession.isOpen()).thenReturn(true);

            gateway.sendToSomeone(directSession, "{\"type\":\"welcome\",\"stage\":\"songs\"}");

            verify(directSession)
                .sendMessage(argThat(messageWithPayload("{\"type\":\"welcome\",\"stage\":\"songs\"}")));
          }

          private org.mockito.ArgumentMatcher<TextMessage> messageWithPayload(final String expected) {
            return message -> message != null && expected.equals(message.getPayload());
          }
    }

}
