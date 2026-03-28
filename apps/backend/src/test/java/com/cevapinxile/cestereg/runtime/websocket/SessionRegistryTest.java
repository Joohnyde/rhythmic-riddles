package com.cevapinxile.cestereg.runtime.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class SessionRegistryTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SessionRegistryConcurrencyRaceTest {
            private final ExecutorService executor = Executors.newCachedThreadPool();

            @AfterEach
            void tearDown() {
                executor.shutdownNow();
            }

            @Test
            void simultaneousAdminCollisionOnlyOneAcceptedAndStored() throws Exception {
                final SessionRegistry registry = new SessionRegistry();
                final CountDownLatch ready = new CountDownLatch(2);
                final CountDownLatch start = new CountDownLatch(1);

                final WebSocketSession first = openSession("ROOM", 0);
                final WebSocketSession second = openSession("ROOM", 0);

                final Future<Boolean> left = executor.submit(registerTask(registry, first, ready, start));
                final Future<Boolean> right = executor.submit(registerTask(registry, second, ready, start));

                await(ready);
                start.countDown();

                final boolean firstAccepted = left.get(5, TimeUnit.SECONDS);
                final boolean secondAccepted = right.get(5, TimeUnit.SECONDS);

                assertTrue(firstAccepted ^ secondAccepted);
                final WebSocketSession stored = registry.getAdminSession("ROOM");
                assertNotNull(stored);
                assertTrue(stored == first || stored == second);
                assertEquals(1, bool(firstAccepted) + bool(secondAccepted));
                assertTrue(registry.isAdminPresent("ROOM"));
                assertFalse(registry.isTvPresent("ROOM"));
            }

            @Test
            void simultaneousAdminAndTvConnectsAreBothAcceptedForSameRoom() throws Exception {
                final SessionRegistry registry = new SessionRegistry();
                final CountDownLatch ready = new CountDownLatch(2);
                final CountDownLatch start = new CountDownLatch(1);

                final WebSocketSession admin = openSession("ROOM", 0);
                final WebSocketSession tv = openSession("ROOM", 1);

                final Future<Boolean> adminAccepted = executor.submit(registerTask(registry, admin, ready, start));
                final Future<Boolean> tvAccepted = executor.submit(registerTask(registry, tv, ready, start));

                await(ready);
                start.countDown();

                assertTrue(adminAccepted.get(5, TimeUnit.SECONDS));
                assertTrue(tvAccepted.get(5, TimeUnit.SECONDS));
                assertSame(admin, registry.getAdminSession("ROOM"));
                assertSame(tv, registry.getTvSession("ROOM"));
                assertTrue(registry.areBothPresent("ROOM"));
            }

            @Test
            void staleCloseAndReplacementRaceNeverEvictsFreshReplacement() throws Exception {
                final SessionRegistry registry = new SessionRegistry();
                final WebSocketSession oldAdmin = session("ROOM", 0, false);
                final WebSocketSession freshAdmin = openSession("ROOM", 0);
                assertTrue(registry.setSession(oldAdmin));

                final CountDownLatch ready = new CountDownLatch(2);
                final CountDownLatch start = new CountDownLatch(1);

                final Future<Boolean> replacementAccepted = executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return registry.setSession(freshAdmin);
                });
                final Future<Boolean> staleRemoved = executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return registry.removeSession(oldAdmin);
                });

                await(ready);
                start.countDown();

                assertTrue(replacementAccepted.get(5, TimeUnit.SECONDS));
                staleRemoved.get(5, TimeUnit.SECONDS);
                assertSame(freshAdmin, registry.getAdminSession("ROOM"));
                assertTrue(registry.isAdminPresent("ROOM"));
            }

            @Test
            void reconnectChurnAcrossManyClosedReplacementsLeavesSingleFreshAuthority() throws Exception {
                final SessionRegistry registry = new SessionRegistry();
                final List<WebSocketSession> sessions = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    sessions.add(session("ROOM", 0, i == 11));
                }

                assertTrue(registry.setSession(sessions.get(0)));
                for (int i = 0; i < sessions.size() - 1; i++) {
                    assertTrue(registry.removeSession(sessions.get(i)) || i > 0);
                    assertTrue(registry.setSession(sessions.get(i + 1)));
                }

                assertSame(sessions.get(11), registry.getAdminSession("ROOM"));
                assertTrue(registry.isAdminPresent("ROOM"));
            }

            @Test
            void roomIsolationUnderConcurrentCollisionsKeepsEachRoomIndependent() throws Exception {
                final SessionRegistry registry = new SessionRegistry();
                final CountDownLatch ready = new CountDownLatch(4);
                final CountDownLatch start = new CountDownLatch(1);

                final WebSocketSession roomAAdmin1 = openSession("AAAA", 0);
                final WebSocketSession roomAAdmin2 = openSession("AAAA", 0);
                final WebSocketSession roomBTv1 = openSession("BBBB", 1);
                final WebSocketSession roomBTv2 = openSession("BBBB", 1);

                final List<Future<Boolean>> futures = List.of(
                        executor.submit(registerTask(registry, roomAAdmin1, ready, start)),
                        executor.submit(registerTask(registry, roomAAdmin2, ready, start)),
                        executor.submit(registerTask(registry, roomBTv1, ready, start)),
                        executor.submit(registerTask(registry, roomBTv2, ready, start)));

                await(ready);
                start.countDown();
                int accepted = 0;
                for (Future<Boolean> future : futures) {
                    accepted += bool(future.get(5, TimeUnit.SECONDS));
                }

                assertEquals(2, accepted);
                assertTrue(registry.isAdminPresent("AAAA"));
                assertFalse(registry.isTvPresent("AAAA"));
                assertTrue(registry.isTvPresent("BBBB"));
                assertFalse(registry.isAdminPresent("BBBB"));
            }

            private Callable<Boolean> registerTask(
                    final SessionRegistry registry,
                    final WebSocketSession session,
                    final CountDownLatch ready,
                    final CountDownLatch start) {
                return () -> {
                    ready.countDown();
                    await(start);
                    return registry.setSession(session);
                };
            }

            private static void await(final CountDownLatch latch) {
                try {
                    if (!latch.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for latch");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
            }

            private static int bool(final boolean value) {
                return value ? 1 : 0;
            }

            private static WebSocketSession openSession(final String roomCode, final int socketPosition) throws IOException {
                return session(roomCode, socketPosition, true);
            }

            private static WebSocketSession session(
                    final String roomCode,
                    final int socketPosition,
                    final boolean open) throws IOException {
                final WebSocketSession session = mock(WebSocketSession.class);
                final Map<String, Object> attributes = new HashMap<>();
                attributes.put("ROOM_CODE", roomCode);
                attributes.put("SOCKET_POSITION", socketPosition);
                when(session.getAttributes()).thenReturn(attributes);
                lenient().when(session.isOpen()).thenReturn(open);
                lenient().doAnswer(invocation -> null).when(session).close(CloseStatus.POLICY_VIOLATION);
                lenient().doAnswer(invocation -> null).when(session).close();
                return session;
            }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SessionRegistryDeepTest {
            private SessionRegistry registry;

            @BeforeEach
            void setUp() {
                registry = new SessionRegistry();
            }

            @Nested
            class RegistrationGuards {
                @Test
                void rejectsNullSession() {
                    assertFalse(registry.setSession(null));
                }

                @Test
                void rejectsSessionWithoutAttributesAndClosesIt() throws Exception {
                    final WebSocketSession session = mock(WebSocketSession.class);
                    when(session.getAttributes()).thenReturn(new HashMap<>());

                    assertFalse(registry.setSession(session));
                    verify(session).close();
                }

                @Test
                void rejectsSessionWithInvalidSocketPositionAndClosesIt() throws Exception {
                    final WebSocketSession session = session("ROOM", 9, true);

                    assertFalse(registry.setSession(session));
                    verify(session).close();
                }

                @Test
                void acceptsAdminAndTvSessionsIntoSeparateSlots() {
                    final WebSocketSession admin = session("ROOM", 0, true);
                    final WebSocketSession tv = session("ROOM", 1, true);

                    assertTrue(registry.setSession(admin));
                    assertTrue(registry.setSession(tv));
                    assertSame(admin, registry.getAdminSession("ROOM"));
                    assertSame(tv, registry.getTvSession("ROOM"));
                    assertTrue(registry.isAdminPresent("ROOM"));
                    assertTrue(registry.isTvPresent("ROOM"));
                    assertTrue(registry.areBothPresent("ROOM"));
                }

                @Test
                void rejectsCollisionForOpenSessionAndClosesNewOne() throws Exception {
                    final WebSocketSession original = session("ROOM", 0, true);
                    final WebSocketSession replacement = session("ROOM", 0, true);

                    assertTrue(registry.setSession(original));
                    assertFalse(registry.setSession(replacement));
                    assertSame(original, registry.getAdminSession("ROOM"));
                    verify(replacement).close();
                }

                @Test
                void allowsReplacementWhenStoredSessionIsAlreadyClosed() {
                    final WebSocketSession closedOld = session("ROOM", 0, false);
                    final WebSocketSession fresh = session("ROOM", 0, true);

                    assertTrue(registry.setSession(closedOld));
                    assertTrue(registry.setSession(fresh));
                    assertSame(fresh, registry.getAdminSession("ROOM"));
                }
            }

            @Nested
            class RemovalSemantics {
                @Test
                void removeSessionReturnsFalseForNull() {
                    assertFalse(registry.removeSession(null));
                }

                @Test
                void removeSessionReturnsFalseAndClosesUnauthenticatedSession() throws Exception {
                    final WebSocketSession session = mock(WebSocketSession.class);
                    when(session.getAttributes()).thenReturn(new HashMap<>());

                    assertFalse(registry.removeSession(session));
                    verify(session).close();
                }

                @Test
                void removeSessionRemovesOnlyExactInstance() {
                    final WebSocketSession stored = session("ROOM", 1, true);
                    final WebSocketSession stale = session("ROOM", 1, true);

                    assertTrue(registry.setSession(stored));
                    assertFalse(registry.removeSession(stale));
                    assertSame(stored, registry.getTvSession("ROOM"));
                }

                @Test
                void removeSessionClearsOnlyOneSlotAndKeepsOtherSlot() {
                    final WebSocketSession admin = session("ROOM", 0, true);
                    final WebSocketSession tv = session("ROOM", 1, true);

                    assertTrue(registry.setSession(admin));
                    assertTrue(registry.setSession(tv));
                    assertTrue(registry.removeSession(tv));

                    assertSame(admin, registry.getAdminSession("ROOM"));
                    assertNull(registry.getTvSession("ROOM"));
                    assertTrue(registry.isAdminPresent("ROOM"));
                    assertFalse(registry.isTvPresent("ROOM"));
                    assertFalse(registry.areBothPresent("ROOM"));
                }

                @Test
                void staleCloseAfterReconnectMustNotRemoveReplacement() {
                    final WebSocketSession oldClosed = session("ROOM", 1, false);
                    final WebSocketSession replacement = session("ROOM", 1, true);

                    assertTrue(registry.setSession(oldClosed));
                    assertTrue(registry.setSession(replacement));
                    assertFalse(registry.removeSession(oldClosed));
                    assertSame(replacement, registry.getTvSession("ROOM"));
                }
            }

            private WebSocketSession session(final String room, final int pos, final boolean open) {
                final WebSocketSession session = mock(WebSocketSession.class);
                final Map<String, Object> attributes = new HashMap<>();
                attributes.put("ROOM_CODE", room);
                attributes.put("SOCKET_POSITION", pos);
                when(session.getAttributes()).thenReturn(attributes);
                lenient().when(session.isOpen()).thenReturn(open);
                return session;
            }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SessionRegistryRaceStressRepeatedTest {
            private final ExecutorService executor = Executors.newFixedThreadPool(4);

            @AfterEach
            void tearDown() {
                executor.shutdownNow();
            }

            @Test
            void repeatedAdminCollisionRaceAlwaysLeavesExactlyOneAcceptedAuthority() throws Exception {
                for (int i = 0; i < 50; i++) {
                    final SessionRegistry registry = new SessionRegistry();
                    final CountDownLatch ready = new CountDownLatch(2);
                    final CountDownLatch start = new CountDownLatch(1);

                    final WebSocketSession left = openSession("ROOM", 0);
                    final WebSocketSession right = openSession("ROOM", 0);

                    final Future<Boolean> leftAccepted = executor.submit(() -> register(registry, left, ready, start));
                    final Future<Boolean> rightAccepted = executor.submit(() -> register(registry, right, ready, start));

                    await(ready);
                    start.countDown();

                    final boolean l = leftAccepted.get(5, TimeUnit.SECONDS);
                    final boolean r = rightAccepted.get(5, TimeUnit.SECONDS);

                    assertEquals(1, bool(l) + bool(r));
                    final WebSocketSession stored = registry.getAdminSession("ROOM");
                    assertTrue(stored == left || stored == right);
                }
            }

            @Test
            void repeatedStaleCloseVsReplacementRaceNeverRemovesFreshSession() throws Exception {
                for (int i = 0; i < 50; i++) {
                    final SessionRegistry registry = new SessionRegistry();
                    final WebSocketSession oldAdmin = closedSession("ROOM", 0);
                    final WebSocketSession freshAdmin = openSession("ROOM", 0);
                    assertTrue(registry.setSession(oldAdmin));

                    final CountDownLatch ready = new CountDownLatch(2);
                    final CountDownLatch start = new CountDownLatch(1);

                    final Future<Boolean> remove = executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        return registry.removeSession(oldAdmin);
                    });
                    final Future<Boolean> replace = executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        return registry.setSession(freshAdmin);
                    });

                    await(ready);
                    start.countDown();

                    remove.get(5, TimeUnit.SECONDS);
                    assertTrue(replace.get(5, TimeUnit.SECONDS));
                    assertSame(freshAdmin, registry.getAdminSession("ROOM"));
                }
            }

            @Test
            void repeatedRoomIsolationRaceKeepsRoomsIndependentUnderConcurrentConnects() throws Exception {
                for (int i = 0; i < 40; i++) {
                    final SessionRegistry registry = new SessionRegistry();
                    final CountDownLatch ready = new CountDownLatch(4);
                    final CountDownLatch start = new CountDownLatch(1);

                    final WebSocketSession roomAAdmin1 = openSession("ROOM-A", 0);
                    final WebSocketSession roomAAdmin2 = openSession("ROOM-A", 0);
                    final WebSocketSession roomBTv1 = openSession("ROOM-B", 1);
                    final WebSocketSession roomBTv2 = openSession("ROOM-B", 1);

                    final Future<Boolean> a1 = executor.submit(() -> register(registry, roomAAdmin1, ready, start));
                    final Future<Boolean> a2 = executor.submit(() -> register(registry, roomAAdmin2, ready, start));
                    final Future<Boolean> b1 = executor.submit(() -> register(registry, roomBTv1, ready, start));
                    final Future<Boolean> b2 = executor.submit(() -> register(registry, roomBTv2, ready, start));

                    await(ready);
                    start.countDown();

                    assertEquals(2,
                            bool(a1.get(5, TimeUnit.SECONDS))
                            + bool(a2.get(5, TimeUnit.SECONDS))
                            + bool(b1.get(5, TimeUnit.SECONDS))
                            + bool(b2.get(5, TimeUnit.SECONDS)));
                    assertTrue(registry.isAdminPresent("ROOM-A"));
                    assertTrue(registry.isTvPresent("ROOM-B"));
                }
            }

            private static Boolean register(
                    final SessionRegistry registry,
                    final WebSocketSession session,
                    final CountDownLatch ready,
                    final CountDownLatch start) {
                ready.countDown();
                await(start);
                return registry.setSession(session);
            }

            private static int bool(final boolean value) {
                return value ? 1 : 0;
            }

            private static void await(final CountDownLatch latch) {
                try {
                    if (!latch.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for latch");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
            }

            private static WebSocketSession openSession(final String roomCode, final int socketPosition) {
                return session(roomCode, socketPosition, true);
            }

            private static WebSocketSession closedSession(final String roomCode, final int socketPosition) {
                return session(roomCode, socketPosition, false);
            }

            private static WebSocketSession session(
                    final String roomCode,
                    final int socketPosition,
                    final boolean open) {
                final WebSocketSession session = mock(WebSocketSession.class);
                final Map<String, Object> attributes = new HashMap<>();
                attributes.put("ROOM_CODE", roomCode);
                attributes.put("SOCKET_POSITION", socketPosition);
                lenient().when(session.getAttributes()).thenReturn(attributes);
                lenient().when(session.isOpen()).thenReturn(open);
                return session;
            }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SessionRegistryStormAndIsolationTest {
            private SessionRegistry registry;

            @BeforeEach
            void setUp() {
                registry = new SessionRegistry();
            }

            @Nested
            class ConnectDisconnectStorms {
                @Test
                void repeatedAdminCollisionStormKeepsFirstOpenSessionAndRejectsAllOthers() throws Exception {
                    final WebSocketSession accepted = session("ROOM", 0, true);
                    final WebSocketSession rejected1 = session("ROOM", 0, true);
                    final WebSocketSession rejected2 = session("ROOM", 0, true);
                    final WebSocketSession rejected3 = session("ROOM", 0, true);

                    assertTrue(registry.setSession(accepted));
                    assertFalse(registry.setSession(rejected1));
                    assertFalse(registry.setSession(rejected2));
                    assertFalse(registry.setSession(rejected3));

                    assertSame(accepted, registry.getAdminSession("ROOM"));
                    verify(rejected1).close();
                    verify(rejected2).close();
                    verify(rejected3).close();
                }

                @Test
                void staleCloseStormCannotEvictFreshReplacement() {
                    final WebSocketSession stale = session("ROOM", 1, false);
                    final WebSocketSession fresh = session("ROOM", 1, true);

                    assertTrue(registry.setSession(stale));
                    assertTrue(registry.setSession(fresh));
                    assertFalse(registry.removeSession(stale));

                    assertSame(fresh, registry.getTvSession("ROOM"));
                    assertTrue(registry.isTvPresent("ROOM"));
                }

                @Test
                void rapidRemoveAddRemoveSequenceTracksExactInstancesOnly() {
                    final WebSocketSession first = session("ROOM", 0, false);
                    final WebSocketSession second = session("ROOM", 0, true);
                    final WebSocketSession third = session("ROOM", 0, true);

                    assertTrue(registry.setSession(first));
                    assertTrue(registry.setSession(second));
                    assertFalse(registry.removeSession(first));
                    assertFalse(registry.setSession(third));
                    assertTrue(registry.removeSession(second));
                    assertTrue(registry.setSession(third));

                    assertSame(third, registry.getAdminSession("ROOM"));
                }
            }

            @Nested
            class RoomIsolation {
                @Test
                void adminAndTvPresenceAreComputedPerRoomWithoutBleedingAcrossRooms() {
                    final WebSocketSession adminA = session("ROOM-A", 0, true);
                    final WebSocketSession tvB = session("ROOM-B", 1, true);

                    assertTrue(registry.setSession(adminA));
                    assertTrue(registry.setSession(tvB));

                    assertTrue(registry.isAdminPresent("ROOM-A"));
                    assertFalse(registry.isTvPresent("ROOM-A"));
                    assertFalse(registry.areBothPresent("ROOM-A"));

                    assertFalse(registry.isAdminPresent("ROOM-B"));
                    assertTrue(registry.isTvPresent("ROOM-B"));
                    assertFalse(registry.areBothPresent("ROOM-B"));
                }

                @Test
                void removingSessionFromOneRoomLeavesOtherRoomUntouched() {
                    final WebSocketSession adminA = session("ROOM-A", 0, true);
                    final WebSocketSession tvA = session("ROOM-A", 1, true);
                    final WebSocketSession adminB = session("ROOM-B", 0, true);
                    final WebSocketSession tvB = session("ROOM-B", 1, true);

                    assertTrue(registry.setSession(adminA));
                    assertTrue(registry.setSession(tvA));
                    assertTrue(registry.setSession(adminB));
                    assertTrue(registry.setSession(tvB));

                    assertTrue(registry.removeSession(tvA));

                    assertSame(adminA, registry.getAdminSession("ROOM-A"));
                    assertNull(registry.getTvSession("ROOM-A"));
                    assertSame(adminB, registry.getAdminSession("ROOM-B"));
                    assertSame(tvB, registry.getTvSession("ROOM-B"));
                    assertTrue(registry.areBothPresent("ROOM-B"));
                }

                @Test
                void removeInvalidSessionNeverTouchesStoredValidSession() throws Exception {
                    final WebSocketSession valid = session("ROOM", 0, true);
                    final WebSocketSession invalid = mock(WebSocketSession.class);
                    when(invalid.getAttributes()).thenReturn(new HashMap<>());

                    assertTrue(registry.setSession(valid));
                    assertFalse(registry.removeSession(invalid));
                    assertSame(valid, registry.getAdminSession("ROOM"));
                    verify(invalid).close();
                }
            }

            private WebSocketSession session(final String room, final int pos, final boolean open) {
                final WebSocketSession session = mock(WebSocketSession.class);
                final Map<String, Object> attributes = new HashMap<>();
                attributes.put("ROOM_CODE", room);
                attributes.put("SOCKET_POSITION", pos);
                when(session.getAttributes()).thenReturn(attributes);
                lenient().when(session.isOpen()).thenReturn(open);
                return session;
            }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SessionRegistryWebSocketContractTest {
          private SessionRegistry registry;

          @Mock private WebSocketSession adminSession;
          @Mock private WebSocketSession duplicateAdminSession;
          @Mock private WebSocketSession closedAdminSession;
          @Mock private WebSocketSession tvSession;
          @Mock private WebSocketSession replacementTvSession;
          @Mock private WebSocketSession invalidSession;

          @BeforeEach
          void setUp() {
            registry = new SessionRegistry();
          }

          @Test
          void setSessionAcceptsOneAdminAndOneTvPerRoomAndReportsPresence() {
            when(adminSession.getAttributes()).thenReturn(attributes("AKKU", 0));
            when(adminSession.isOpen()).thenReturn(true);
            when(tvSession.getAttributes()).thenReturn(attributes("AKKU", 1));
            when(tvSession.isOpen()).thenReturn(true);

            assertTrue(registry.setSession(adminSession));
            assertTrue(registry.setSession(tvSession));

            assertSame(adminSession, registry.getAdminSession("AKKU"));
            assertSame(tvSession, registry.getTvSession("AKKU"));
            assertTrue(registry.isAdminPresent("AKKU"));
            assertTrue(registry.isTvPresent("AKKU"));
            assertTrue(registry.areBothPresent("AKKU"));
          }

          @Test
          void setSessionRejectsSecondOpenSessionForSameRoomAndClientType() throws Exception {
            when(adminSession.getAttributes()).thenReturn(attributes("AKKU", 0));
            when(adminSession.isOpen()).thenReturn(true);
            when(duplicateAdminSession.getAttributes()).thenReturn(attributes("AKKU", 0));

            assertTrue(registry.setSession(adminSession));
            assertFalse(registry.setSession(duplicateAdminSession));

            assertSame(adminSession, registry.getAdminSession("AKKU"));
            verify(duplicateAdminSession).close();
          }

          @Test
          void setSessionAllowsReplacementWhenStoredSessionIsNotOpen() {
            when(closedAdminSession.getAttributes()).thenReturn(attributes("AKKU", 0));
            when(closedAdminSession.isOpen()).thenReturn(false);
            when(adminSession.getAttributes()).thenReturn(attributes("AKKU", 0));

            assertTrue(registry.setSession(closedAdminSession));
            assertTrue(registry.setSession(adminSession));

            assertSame(adminSession, registry.getAdminSession("AKKU"));
          }

          @Test
          void removeSessionUsesIdentitySoStaleCloseCannotRemoveReplacement() {
            when(tvSession.getAttributes()).thenReturn(attributes("AKKU", 1));
            when(tvSession.isOpen()).thenReturn(true);
            when(replacementTvSession.getAttributes()).thenReturn(attributes("AKKU", 1));

            assertTrue(registry.setSession(tvSession));
            when(tvSession.isOpen()).thenReturn(false);
            assertTrue(registry.setSession(replacementTvSession));

            assertFalse(registry.removeSession(tvSession));
            assertSame(replacementTvSession, registry.getTvSession("AKKU"));
            assertTrue(registry.removeSession(replacementTvSession));
            assertNull(registry.getTvSession("AKKU"));
          }

          @Test
          void setSessionRejectsMissingHandshakeAttributesAndClosesSocket() throws Exception {
            when(invalidSession.getAttributes()).thenReturn(new HashMap<>());

            assertFalse(registry.setSession(invalidSession));

            verify(invalidSession).close();
            verify(invalidSession, never()).isOpen();
          }

          @Test
          void setSessionRejectsInvalidSocketPositionAndClosesSocket() throws Exception {
            when(invalidSession.getAttributes()).thenReturn(attributes("AKKU", 7));

            assertFalse(registry.setSession(invalidSession));

            verify(invalidSession).close();
          }

          private Map<String, Object> attributes(final String roomCode, final int socketPosition) {
            final Map<String, Object> attributes = new HashMap<>();
            attributes.put("ROOM_CODE", roomCode);
            attributes.put("SOCKET_POSITION", socketPosition);
            return attributes;
          }
    }

}
