package com.cevapinxile.cestereg.e2e.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.cevapinxile.cestereg.common.exception.E2eGameFixtureValidationException;
import com.cevapinxile.cestereg.e2e.E2eGameFixtureRequest;
import com.cevapinxile.cestereg.e2e.E2eGameFixtureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * High-ROI contract suite for the real E2eGameFixtureService implementation.
 *
 * <p>The uploaded code contains only the service interface, not its implementation or repositories.
 * Keep this class abstract and add a concrete subclass in the implementation module, for example:
 *
 * <pre>{@code
 * @SpringBootTest
 * @ActiveProfiles("e2e")
 * class E2eGameFixtureServiceImplTest extends E2eGameFixtureServiceContractTest {
 *   @Autowired E2eGameFixtureService service;
 *   @Autowired GameRepository games;
 *
 *   @Override protected E2eGameFixtureService service() { return service; }
 *   @Override protected E2eGameFixtureRequest validFixture(String roomCode) { return Fixtures.valid(roomCode); }
 *   @Override protected void assertFixtureExists(String roomCode) { assertThat(games.findByRoomCode(roomCode)).isPresent(); }
 *   @Override protected void assertFixtureDoesNotExist(String roomCode) { assertThat(games.findByRoomCode(roomCode)).isEmpty(); }
 *   @Override protected void assertRuntimeStateReset(String roomCode) { ... assert schedules/interrupts/sockets are cleared ... }
 * }
 * }</pre>
 */
@DisplayName("E2E game fixture service contract")
public abstract class E2eGameFixtureServiceContractTest {

  protected abstract E2eGameFixtureService service();

  protected abstract E2eGameFixtureRequest validFixture(String roomCode);

  protected abstract void assertFixtureExists(String roomCode);

  protected abstract void assertFixtureDoesNotExist(String roomCode);

  protected abstract void assertRuntimeStateReset(String roomCode);

  @Test
  void createFixturePersistsACompleteGameGraph() throws E2eGameFixtureValidationException {
    String roomCode = "AKKU";

    service().createFixture(validFixture(roomCode));

    assertFixtureExists(roomCode);
  }

  @Test
  void resetRuntimeStateDeletesOrClearsTheSeededRoomCompletely()
      throws E2eGameFixtureValidationException {
    String roomCode = "AKKU";
    service().createFixture(validFixture(roomCode));

    service().resetRuntimeState(roomCode);

    assertRuntimeStateReset(roomCode);
  }

  @Test
  void resetRuntimeStateIsIdempotentForMissingRoomsSoE2eCleanupIsSafe() {
    assertThatCode(() -> service().resetRuntimeState("NOPE")).doesNotThrowAnyException();

    assertFixtureDoesNotExist("NOPE");
  }

  @Test
  void createFixtureCanRecreateTheSameRoomAfterCleanup() throws E2eGameFixtureValidationException {
    String roomCode = "AKKU";

    service().createFixture(validFixture(roomCode));
    service().resetRuntimeState(roomCode);
    service().createFixture(validFixture(roomCode));

    assertFixtureExists(roomCode);
  }
}
