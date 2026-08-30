#pragma once

#include <string.h>

#include "ReceiverConfig.h"

namespace ReceiverProtocol {
inline const char* responseForCommand(const char* command) {
  if (command != nullptr && strcmp(command, ReceiverConfig::DEVICE_IDENTIFIER) == 0) {
    return ReceiverConfig::DEVICE_IDENTIFIER;
  }
  return nullptr;
}
}  // namespace ReceiverProtocol
