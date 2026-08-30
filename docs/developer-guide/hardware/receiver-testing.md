# Receiver testing

Receiver coverage is deliberately split into **firmware logic**, **backend serial-adapter**, and optional **physical hardware** layers. They protect different failure modes and should not be collapsed into one Java suite.

## 1. Arduino firmware tests — PlatformIO / Unity

The firmware contains both the backend discovery handshake and real anti-spam logic: it remembers recent button codes in 32 slots and suppresses a repeated signal from the same code for 100 ms. The suppression logic lives in `ReceiverButtonFilter.h`; `ReceiverConfig.h` owns the runtime constants and identifier; and `ReceiverProtocol.h` owns the discovery response. All three are used directly by `RhytmicRiddlesReceiver.ino`.

PlatformIO's native test runner executes that exact state machine on the development machine with Unity. Tests supply synthetic RF codes and timestamps, which is equivalent to mocking decoded button presses **after `RCSwitch` has decoded the radio signal**. No Arduino or RF transmitter is needed.

First-time setup:

Use a current **PlatformIO Core 6.x or newer**. Do not rely on an old distro package such as Ubuntu's legacy PlatformIO 4.3.4; that release is incompatible with modern Python/Click and fails before any project command runs. For a developer machine, PlatformIO recommends its isolated installer:

```bash
curl -fsSL -o get-platformio.py https://raw.githubusercontent.com/platformio/platformio-core-installer/master/get-platformio.py
python3 get-platformio.py
export PATH="$HOME/.platformio/penv/bin:$PATH"
pio --version
```

If `which pio` still points at an old `/usr/bin/pio`, remove/disable that legacy package or invoke `$HOME/.platformio/penv/bin/pio` explicitly.

Run the firmware regression suite from the repository root:

```bash
pio test -d hardware/firmware/receiver -e native
```

`-d`/`--project-dir` makes the firmware project explicit, so the command does not depend on a remembered working directory.

It covers:

- the timeout, slot capacity, and `RHYTMIC_RIDDLES` identifier used by the test are the same constants compiled into the sketch;
- the firmware discovery command returns that identifier and unrelated commands do not;
- RF code `0` is ignored;
- first valid press is emitted;
- same code inside the 100 ms timeout is suppressed;
- same code at the timeout boundary is accepted again;
- different codes do not suppress one another;
- all 32 configured active-button slots remain independent;
- an unexpected 33rd code is dropped while all slots are still active instead of bypassing anti-spam state;
- expired slots can be reused;
- unsigned 32-bit `millis()` wraparound preserves the timeout calculation.

Also compile the **actual Nano firmware**, including the real Arduino framework and pinned `RCSwitch` library:

```bash
pio run -d hardware/firmware/receiver -e nanoatmega328
```

The native tests prove deterministic firmware logic; the Nano build proves the sketch that is flashed to the receiver still compiles for the target MCU. PlatformIO is catalogued as its own `platformio` framework type. PlatformIO catalog paths are relative to `hardware/firmware/receiver`, matching the repository's framework-relative catalog convention.

## 2. Backend serial adapter — JUnit

`BuzzerSerialAdapterTest` existed before the firmware test module and already covered the Java/USB side well: valid forwarding, ignored empty/identification frames, disconnect cleanup, serial-port open failure, listener framing/events, ordered bursts, service failures and stop cleanup.

The only additional Java cases retained are the genuinely distinct discovery handshake paths:

- a device that answers `RHYTMIC_RIDDLES` is accepted and gets the production listener;
- a device with the wrong identification response is closed and rejected.

Run it without connected hardware:

```bash
cd apps/backend
mvn test -Dtest=BuzzerSerialAdapterTest
```

Do not add firmware anti-spam cases here. By the time Java receives a line, the Arduino has already made the suppression decision.

## 3. Optional physical smoke

Connect a Nano programmed with `hardware/firmware/receiver/RhytmicRiddlesReceiver.ino`, start the backend, and use a paired RF button.

Verify:

1. the backend discovers the receiver through the `RHYTMIC_RIDDLES` handshake;
2. one press produces one application buzzer event;
3. holding/repeating the same physical button does not flood events inside the firmware's 100 ms suppression window;
4. another button can still be observed independently.

The repository contains no RF transmitter, so it cannot truthfully generate a physical 433 MHz press in normal automation. RF propagation, antenna/receiver quality and USB-electrical behavior therefore remain manual/HIL smoke concerns.

## Ownership summary

- **PlatformIO/Unity firmware tests:** decoded RF code + time → firmware suppression decision.
- **JUnit `BuzzerSerialAdapterTest`:** serial device discovery + complete serial frame → `BuzzerService` call.
- **Backend concurrency tests:** simultaneous team buzz arbitration and game invariants.
- **Playwright full-product E2E:** buzzer boundary → real Admin/TV user-visible behavior.
- **Physical smoke:** actual RF receiver, antenna, Nano and USB chain.
