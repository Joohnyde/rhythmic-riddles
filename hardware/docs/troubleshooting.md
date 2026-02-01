# Troubleshooting (hardware)

## No codes appear
- verify Arduino is connected and shows a serial device
- confirm baud rate is 9600
- confirm receiver VCC/GND wiring
- ensure receiver DATA is wired to D2 (Uno/Nano)

## Codes spam continuously
- some receivers are noisy; move away from power supplies
- confirm you are using the debounce firmware from this repo
- add a small delay or increase RELEASE_GAP_MS if needed

## Bad range
- add/extend antenna wire on receiver
- change receiver module (RXB6 variants often work better)
- avoid metal enclosures without antenna passthrough

