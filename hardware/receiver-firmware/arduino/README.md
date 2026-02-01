# Receiver firmware (Arduino)

This firmware reads 433MHz RF button codes via `RCSwitch` and emits **one** `button_id` per physical press on Serial.

## Behavior
- On first code received for a press: `Serial.println(code)`
- Repeated codes while holding the button are ignored.
- If no repeats are seen for `RELEASE_GAP_MS` (default: 250ms), the press is considered released.

## Wiring expectation
- `rf.enableReceive(0)` expects receiver data pin connected to **D2** on typical Arduino boards.

## TODOs to fill later
- Exact receiver module model and wiring diagram
- Serial device path expectations per OS
- How the backend/service reads the serial port
