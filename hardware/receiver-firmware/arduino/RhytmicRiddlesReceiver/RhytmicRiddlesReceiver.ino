#include <RCSwitch.h>

RCSwitch rf;

unsigned long lastCode = 0;
unsigned long lastSeenMs = 0;
bool pressed = false;

const unsigned long RELEASE_GAP_MS = 250;  // time with no repeats => treat as released

void setup() {
  Serial.begin(9600);
  rf.enableReceive(0); // D2
}

void loop() {
  unsigned long now = millis();

  // If we've been "pressed" but nothing received for a while => release
  if (pressed && (now - lastSeenMs > RELEASE_GAP_MS)) {
    pressed = false;
    lastCode = 0;
  }

  if (rf.available()) {
    unsigned long code = rf.getReceivedValue();
    rf.resetAvailable();

    if (code == 0) return;          // ignore unknown/noise
    lastSeenMs = now;

    // First code in a press => emit once
    if (!pressed) {
      pressed = true;
      lastCode = code;
      Serial.println(code);
    } else {
      // Still holding: ignore repeats (even if they spam in)
      // If you want to support multi-button holds simultaneously, keep logic per code.
      (void)lastCode;
    }
  }
}
