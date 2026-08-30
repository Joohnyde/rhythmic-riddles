# Receiver Firmware (Arduino)

This firmware reads 433MHz RF button codes using the `RCSwitch` library and emits one serial event per accepted press.

## Runtime behavior

- Baud rate: **9600**
- Identification: prints `RHYTMIC_RIDDLES` at startup and echoes the same identifier when queried by the backend.
- Valid RF output: `<decimal_code>\n`
- RF code `0` is ignored.
- Repeated signals from the **same button code** are suppressed for `BUTTON_TIMEOUT_MS` (**100 ms**).
- Different button codes are tracked independently in up to `MAX_ACTIVE_BUTTONS` (**32**) slots.
- An empty or expired slot can be reused for a later button.

The suppression window prevents a held/noisy RF button from flooding the backend while still allowing fast independent buttons.

## Automated firmware tests

The anti-spam state machine is isolated in `ReceiverButtonFilter.h`, `ReceiverConfig.h` owns the timeout, slot capacity, and serial identifier, and `ReceiverProtocol.h` owns the discovery-response decision used by the real sketch. PlatformIO runs it as native C++ with Unity, so tests can control button codes and `millis()` timestamps deterministically without RF hardware.

First-time setup:

Use a current **PlatformIO Core 6.x or newer**. Avoid legacy distribution packages such as PlatformIO 4.3.4 on modern Python. PlatformIO recommends its isolated installer for developer machines:

```bash
curl -fsSL -o get-platformio.py https://raw.githubusercontent.com/platformio/platformio-core-installer/master/get-platformio.py
python3 get-platformio.py
export PATH="$HOME/.platformio/penv/bin:$PATH"
pio --version
```

If `which pio` still resolves to an old `/usr/bin/pio`, remove/disable that package or invoke `$HOME/.platformio/penv/bin/pio` explicitly.

Run the firmware logic tests from the repository root:

```bash
pio test -d hardware/firmware/receiver -e native
```

Compile the actual Arduino Nano sketch with the pinned `RCSwitch` dependency:

```bash
pio run -d hardware/firmware/receiver -e nanoatmega328
```

The native tests lock the exact firmware configuration (`100 ms`, `32` slots, and `RHYTMIC_RIDDLES`), prove the discovery command response/rejection contract, then cover zero/invalid RF values, first presses, repeated presses inside the 100 ms window, the exact timeout boundary, independent buttons, all 32 active slots, capacity saturation/drop behavior, expired-slot reuse, and 32-bit `millis()` wraparound.

These tests do not pretend to simulate RF propagation, receiver electronics, antenna quality, or USB hardware. Those remain physical smoke concerns.

## Wiring requirements

The firmware calls:

```cpp
rf.enableReceive(0);
```

Interrupt `0` maps to **D2** on standard Arduino Uno/Nano boards.

Receiver connections:

- **VCC → 5V**
- **GND → GND**
- **DATA / D0 / OUT → D2**
- A **17cm antenna wire** soldered to the `ANT` pad is strongly recommended for stable range.

Compatible receiver modules (MVP):

- XY-MK-5V
- RXB6 (recommended for improved stability)

For detailed wiring diagrams, flashing guidance, physical smoke testing and backend integration, see:

`docs/developer-guide/hardware/hardware-setup-guide.md`
