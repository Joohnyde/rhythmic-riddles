#pragma once

#include <stddef.h>
#include <stdint.h>

template <size_t MaxActiveButtons>
class ReceiverButtonFilter {
 public:
  explicit ReceiverButtonFilter(const uint32_t timeoutMs) : timeoutMs_(timeoutMs) {}

  bool shouldEmit(const uint32_t code, const uint32_t nowMs) {
    if (code == 0) {
      return false;
    }

    int reusableSlot = -1;

    for (size_t i = 0; i < MaxActiveButtons; i++) {
      const uint32_t elapsed = nowMs - buttons_[i].lastPressedMs;

      if (buttons_[i].code == code && elapsed < timeoutMs_) {
        return false;
      }

      if (buttons_[i].code == 0 || elapsed >= timeoutMs_) {
        reusableSlot = static_cast<int>(i);
      }
    }

    if (reusableSlot == -1) {
      return false;
    }

    buttons_[reusableSlot].code = code;
    buttons_[reusableSlot].lastPressedMs = nowMs;
    return true;
  }

 private:
  struct ButtonState {
    uint32_t code = 0;
    uint32_t lastPressedMs = 0;
  };

  ButtonState buttons_[MaxActiveButtons] = {};
  uint32_t timeoutMs_;
};
