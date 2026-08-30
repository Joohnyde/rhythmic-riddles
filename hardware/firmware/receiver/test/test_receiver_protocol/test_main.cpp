#include <unity.h>

#include "ReceiverProtocol.h"

void setUp() {}
void tearDown() {}

void test_identification_command_returns_receiver_identifier() {
  TEST_ASSERT_EQUAL_STRING(
      ReceiverConfig::DEVICE_IDENTIFIER,
      ReceiverProtocol::responseForCommand(ReceiverConfig::DEVICE_IDENTIFIER));
}

void test_non_identification_command_has_no_response() {
  TEST_ASSERT_NULL(ReceiverProtocol::responseForCommand("NOT_OUR_RECEIVER"));
  TEST_ASSERT_NULL(ReceiverProtocol::responseForCommand(nullptr));
}

int main(int argc, char** argv) {
  UNITY_BEGIN();
  RUN_TEST(test_identification_command_returns_receiver_identifier);
  RUN_TEST(test_non_identification_command_has_no_response);
  return UNITY_END();
}
