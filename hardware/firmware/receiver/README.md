
# Receiver Firmware (Arduino)

This firmware reads 433MHz RF button codes using the `RCSwitch` library and emits exactly **one** `button_id` per physical press over the Serial interface.


## Runtime Behavior

- On first valid RF code detected: `Serial.println(code)`
- Repeated codes while the button is held are ignored
- A press is considered released after `RELEASE_GAP_MS` (default: 250ms) without repeated signals

This guarantees deterministic “one press → one event” semantics for backend processing.



## Wiring Requirements

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

For detailed wiring diagrams, soldering guidance, and assembly instructions, see:

`docs/hardware/hardware-setup-guide.md`



## Serial Interface

- Baud rate: **9600**
- Output format: `<decimal_code>\n`
- One line per physical press

Typical device paths:

- **Windows:** `COMx`
- **Linux:** `/dev/ttyUSB0` or `/dev/ttyACM0`
- **macOS:** `/dev/tty.usbserial-*` or `/dev/tty.usbmodem-*`

The backend service reads the configured serial port and treats each line as a discrete button event.

For full hardware integration details, pairing workflow, and troubleshooting, refer to:

`docs/hardware/hardware-setup-guide.md`
