# Pairing buttons to teams

Pairing is done in software (not RF pairing).

## Workflow
1. Start a game and go to Lobby (Stage 0).
2. Click “Add team” in Admin UI.
3. Ask the team to press their buzzer once.
4. The system reads the latest code from the USB receiver.
5. That `button_id` is stored for the team.

## Troubleshooting
- If multiple codes appear: ensure firmware debounce logic is loaded
- If no codes: check wiring and Arduino serial port selection

