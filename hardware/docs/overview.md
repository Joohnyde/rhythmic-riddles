# Hardware overview (433MHz buzzers)

RhytmicRiddles uses low-cost 433MHz RF buttons for team buzzers.

## Components
- 433MHz RF transmitters (one per team)
- 433MHz RF receiver (connected to laptop)
- Arduino (or compatible) reading receiver output and printing button codes to USB Serial

## Data flow
1. Team presses button.
2. Receiver emits a code.
3. Arduino reads RF code via `RCSwitch` and prints a single line to Serial.
4. Admin app reads Serial and maps `button_id → team`.

## Firmware
Arduino sketch is in:
- `hardware/receiver-firmware/arduino/RhytmicRiddlesReceiver/RhytmicRiddlesReceiver.ino`

## Operational rules
- Buttons are assigned to teams during Lobby by pressing the button once.
- The firmware debounces “held” buttons by suppressing repeats for ~250ms gaps.

See:
- `hardware/docs/wiring.md`
- `hardware/docs/pairing.md`
- `hardware/docs/troubleshooting.md`

