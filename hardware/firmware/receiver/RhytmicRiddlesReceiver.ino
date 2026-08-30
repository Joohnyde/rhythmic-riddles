#include <RCSwitch.h>
#include "ReceiverButtonFilter.h"
#include "ReceiverConfig.h"
#include "ReceiverProtocol.h"

RCSwitch rf;

ReceiverButtonFilter<ReceiverConfig::MAX_ACTIVE_BUTTONS> buttonFilter(
    ReceiverConfig::BUTTON_TIMEOUT_MS);

void setup() {
  Serial.begin(9600);
  rf.enableReceive(0); // D2

  Serial.println(ReceiverConfig::DEVICE_IDENTIFIER);
}

void loop() {
  // Respond to identification request from backend
  if (Serial.available()) {
    String command = Serial.readStringUntil('\n');
    command.trim();

    const char* response = ReceiverProtocol::responseForCommand(command.c_str());
    if (response != nullptr) {
      Serial.println(response);
    }
  }

  if (!rf.available()) {
    return;
  }

  const uint32_t code = static_cast<uint32_t>(rf.getReceivedValue());
  rf.resetAvailable();

  if (buttonFilter.shouldEmit(code, static_cast<uint32_t>(millis()))) {
    Serial.println(code);
  }
}
