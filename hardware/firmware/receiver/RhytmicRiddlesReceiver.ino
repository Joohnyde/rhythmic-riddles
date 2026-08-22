#include <RCSwitch.h>

RCSwitch rf;

const unsigned long BUTTON_TIMEOUT_MS = 100;
const int MAX_ACTIVE_BUTTONS = 32;

struct ButtonState {
  unsigned long code;
  unsigned long lastPressedMs;
};

ButtonState buttons[MAX_ACTIVE_BUTTONS] = {};

void setup() {
  Serial.begin(9600);
  rf.enableReceive(0); // D2

  Serial.println("RHYTMIC_RIDDLES");
}

void loop() {
  // Respond to identification request from backend
  if (Serial.available()) {
    String command = Serial.readStringUntil('\n');
    command.trim();

    if (command == "RHYTMIC_RIDDLES") {
      Serial.println("RHYTMIC_RIDDLES");
    }
  }

  if (!rf.available()) {
    return;
  }

  unsigned long code = rf.getReceivedValue();
  rf.resetAvailable();

  if (code == 0) {
    return;
  }

  unsigned long now = millis();
  int freeSlot = -1;

  for (int i = 0; i < MAX_ACTIVE_BUTTONS; i++) {

    // Same button still in timeout
    if (buttons[i].code == code &&
        now - buttons[i].lastPressedMs < BUTTON_TIMEOUT_MS) {
      return;
    }

    // Empty or expired slot can be reused
    if (buttons[i].code == 0 ||
        now - buttons[i].lastPressedMs >= BUTTON_TIMEOUT_MS) {
      freeSlot = i;
    }
  }

  if (freeSlot != -1) {
    buttons[freeSlot] = {code, now};
  }

  Serial.println(code);
}
