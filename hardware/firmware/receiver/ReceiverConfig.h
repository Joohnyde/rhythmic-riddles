#pragma once

#include <stddef.h>
#include <stdint.h>

namespace ReceiverConfig {
constexpr uint32_t BUTTON_TIMEOUT_MS = 100;
constexpr size_t MAX_ACTIVE_BUTTONS = 32;
constexpr char DEVICE_IDENTIFIER[] = "RHYTMIC_RIDDLES";
}  // namespace ReceiverConfig
