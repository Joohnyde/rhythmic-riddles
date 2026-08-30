#include <stdint.h>
#include <unity.h>

#include "ReceiverButtonFilter.h"
#include "ReceiverConfig.h"

namespace {
ReceiverButtonFilter<ReceiverConfig::MAX_ACTIVE_BUTTONS> filter(
    ReceiverConfig::BUTTON_TIMEOUT_MS);
}

void setUp() {
  filter = ReceiverButtonFilter<ReceiverConfig::MAX_ACTIVE_BUTTONS>(
      ReceiverConfig::BUTTON_TIMEOUT_MS);
}

void tearDown() {}

void test_firmware_configuration_matches_receiver_contract() {
  TEST_ASSERT_EQUAL_UINT32(100, ReceiverConfig::BUTTON_TIMEOUT_MS);
  TEST_ASSERT_EQUAL_UINT32(32, ReceiverConfig::MAX_ACTIVE_BUTTONS);
  TEST_ASSERT_EQUAL_STRING("RHYTMIC_RIDDLES", ReceiverConfig::DEVICE_IDENTIFIER);
}

void test_zero_code_is_ignored() {
  TEST_ASSERT_FALSE(filter.shouldEmit(0, 10));
}

void test_first_press_is_emitted_and_repeat_inside_timeout_is_suppressed() {
  TEST_ASSERT_TRUE(filter.shouldEmit(101, 1000));
  TEST_ASSERT_FALSE(filter.shouldEmit(101, 1099));
}

void test_same_button_is_emitted_again_at_timeout_boundary() {
  TEST_ASSERT_TRUE(filter.shouldEmit(101, 1000));
  TEST_ASSERT_TRUE(filter.shouldEmit(101, 1100));
}

void test_different_buttons_do_not_suppress_each_other() {
  TEST_ASSERT_TRUE(filter.shouldEmit(101, 1000));
  TEST_ASSERT_TRUE(filter.shouldEmit(202, 1001));
  TEST_ASSERT_FALSE(filter.shouldEmit(101, 1050));
  TEST_ASSERT_FALSE(filter.shouldEmit(202, 1051));
}

void test_all_configured_button_slots_are_tracked_independently() {
  for (uint32_t code = 1; code <= ReceiverConfig::MAX_ACTIVE_BUTTONS; code++) {
    TEST_ASSERT_TRUE(filter.shouldEmit(code, 500));
  }

  for (uint32_t code = 1; code <= ReceiverConfig::MAX_ACTIVE_BUTTONS; code++) {
    TEST_ASSERT_FALSE(filter.shouldEmit(code, 599));
  }
}

void test_new_code_is_dropped_while_all_slots_are_inside_timeout() {
  for (uint32_t code = 1; code <= ReceiverConfig::MAX_ACTIVE_BUTTONS; code++) {
    TEST_ASSERT_TRUE(filter.shouldEmit(code, 500));
  }

  TEST_ASSERT_FALSE(filter.shouldEmit(999, 550));
  TEST_ASSERT_FALSE(filter.shouldEmit(999, 551));
}

void test_expired_slot_is_reused_and_new_code_is_then_suppressed() {
  for (uint32_t code = 1; code <= ReceiverConfig::MAX_ACTIVE_BUTTONS; code++) {
    TEST_ASSERT_TRUE(filter.shouldEmit(code, 100));
  }

  TEST_ASSERT_TRUE(filter.shouldEmit(999, 200));
  TEST_ASSERT_FALSE(filter.shouldEmit(999, 250));
}

void test_millis_wraparound_preserves_timeout_window() {
  const uint32_t nearWrap = UINT32_MAX - 50;

  TEST_ASSERT_TRUE(filter.shouldEmit(101, nearWrap));
  TEST_ASSERT_FALSE(filter.shouldEmit(101, 25));
  TEST_ASSERT_TRUE(filter.shouldEmit(101, 50));
}

int main(int argc, char** argv) {
  UNITY_BEGIN();
  RUN_TEST(test_firmware_configuration_matches_receiver_contract);
  RUN_TEST(test_zero_code_is_ignored);
  RUN_TEST(test_first_press_is_emitted_and_repeat_inside_timeout_is_suppressed);
  RUN_TEST(test_same_button_is_emitted_again_at_timeout_boundary);
  RUN_TEST(test_different_buttons_do_not_suppress_each_other);
  RUN_TEST(test_all_configured_button_slots_are_tracked_independently);
  RUN_TEST(test_new_code_is_dropped_while_all_slots_are_inside_timeout);
  RUN_TEST(test_expired_slot_is_reused_and_new_code_is_then_suppressed);
  RUN_TEST(test_millis_wraparound_preserves_timeout_window);
  return UNITY_END();
}
