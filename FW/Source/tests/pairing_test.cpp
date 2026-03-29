#include "airhealth/fw/pairing.hpp"

#include <cstdlib>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {

using airhealth::fw::ConsumerCapabilities;
using airhealth::fw::DeviceInfo;
using airhealth::fw::ClaimError;
using airhealth::fw::ClaimService;
using airhealth::fw::FileClaimStore;
using airhealth::fw::InMemoryClaimStore;
using airhealth::fw::PairingRpcService;
using airhealth::fw::claim_error_to_string;
using airhealth::fw::device_info_to_payload_json;
using airhealth::fw::is_protocol_major_supported;
using airhealth::fw::make_device_info;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

void test_device_info_shape() {
  const DeviceInfo device_info = make_device_info(
      "AH-REV-A",
      true,
      ConsumerCapabilities {
          .claim_required = true,
          .session_resume_supported = true,
          .power_state_reporting_supported = true,
      }
  );

  expect(device_info.hardware_revision == "AH-REV-A",
         "hardware revision should remain stable");
  expect(device_info.supported_modes.size() == 2,
         "device.info should advertise the supported consumer modes");
  expect(device_info.ota_supported, "OTA support flag should be exposed");
}

void test_protocol_major_detection() {
  const DeviceInfo device_info = make_device_info("AH-REV-A", true);

  expect(is_protocol_major_supported(device_info, 1),
         "matching major protocol version should be accepted");
  expect(!is_protocol_major_supported(device_info, 2),
         "unsupported major protocol version should be detectable");
}

void test_device_info_payload_is_stable() {
  const DeviceInfo device_info = make_device_info(
      "AH-REV-B",
      false,
      ConsumerCapabilities {
          .claim_required = true,
          .session_resume_supported = true,
          .power_state_reporting_supported = false,
      }
  );

  const std::string payload = device_info_to_payload_json(device_info);

  expect(
      payload ==
          "{\"protocol_version\":\"1.0.0\",\"protocol_major\":1,"
          "\"protocol_minor\":0,\"protocol_patch\":0,"
          "\"hardware_revision\":\"AH-REV-B\","
          "\"supported_modes\":[\"oral_health\",\"fat_burning\"],"
          "\"ota_supported\":false,"
          "\"consumer_capabilities\":{\"claim_required\":true,"
          "\"session_resume_supported\":true,"
          "\"power_state_reporting_supported\":false}}",
      "device.info payload should remain deterministic for the client"
  );
}

std::filesystem::path make_claim_store_path() {
  return std::filesystem::temp_directory_path() /
      "airhealth-claim-store-test.txt";
}

void test_claim_begin_succeeds_and_persists() {
  InMemoryClaimStore store;
  ClaimService claim_service("DEVICE-123", store);

  const auto result = claim_service.begin_claim("challenge-1");

  expect(result.ok(), "claim.begin should succeed on an unclaimed device");
  expect(result.claim_proof.device_identity == "DEVICE-123",
         "claim proof must be tied to device identity");
  expect(result.claim_proof.challenge == "challenge-1",
         "claim proof must retain the input challenge");
  expect(!result.claim_proof.proof.empty(),
         "claim proof payload should be generated");

  const auto persisted = claim_service.load_claim_state();
  expect(persisted.claimed, "claim state should persist after success");
  expect(persisted.device_identity == "DEVICE-123",
         "persisted state should track device identity");
  expect(persisted.claim_proof == result.claim_proof.proof,
         "persisted proof should match the emitted proof");
}

void test_claim_begin_rejects_invalid_or_repeat_attempts() {
  InMemoryClaimStore store;
  ClaimService first_boot("DEVICE-456", store);

  const auto invalid = first_boot.begin_claim("");
  expect(!invalid.ok(), "empty challenge should be rejected");
  expect(claim_error_to_string(invalid.error) == "empty_challenge",
         "empty challenge must map to a deterministic fault");

  const auto first = first_boot.begin_claim("challenge-2");
  expect(first.ok(), "valid claim should succeed before claim state is set");

  ClaimService second_boot("DEVICE-456", store);
  const auto duplicate = second_boot.begin_claim("challenge-3");
  expect(!duplicate.ok(),
         "repeat claim attempts should fail after state persistence");
  expect(claim_error_to_string(duplicate.error) == "already_claimed",
         "duplicate claims must map to a deterministic fault");
  expect(second_boot.load_claim_state().claimed,
         "claim state should survive service re-creation");
}

void test_file_claim_store_survives_reboot() {
  const auto store_path = make_claim_store_path();
  std::filesystem::remove(store_path);

  FileClaimStore first_store(store_path.string());
  ClaimService first_boot("DEVICE-789", first_store);

  const auto first_claim = first_boot.begin_claim("challenge-4");
  expect(first_claim.ok(), "first boot should persist a successful claim");

  FileClaimStore second_store(store_path.string());
  ClaimService second_boot("DEVICE-789", second_store);

  const auto persisted = second_boot.load_claim_state();
  expect(persisted.claimed, "file-backed claim store should survive reboot");
  expect(persisted.device_identity == "DEVICE-789",
         "rebooted load should retain device identity");
  expect(persisted.claim_proof == first_claim.claim_proof.proof,
         "rebooted load should retain the emitted proof");

  const auto duplicate = second_boot.begin_claim("challenge-5");
  expect(!duplicate.ok(), "repeated claim should fail after reboot");
  expect(claim_error_to_string(duplicate.error) == "already_claimed",
         "rebooted duplicate claim should use deterministic fault codes");

  std::filesystem::remove(store_path);
}

void test_pairing_rpc_service_device_info_contract() {
  InMemoryClaimStore store;
  ClaimService claim_service("DEVICE-123", store);
  const auto device_info = make_device_info("AH-REV-C", true);
  const PairingRpcService rpc_service(device_info, claim_service);

  const auto payload = rpc_service.handle_method("device.info.get");

  expect(
      payload ==
          "{\"ok\":true,\"method\":\"device.info.get\",\"result\":"
          "{\"protocol_version\":\"1.0.0\",\"protocol_major\":1,"
          "\"protocol_minor\":0,\"protocol_patch\":0,"
          "\"hardware_revision\":\"AH-REV-C\","
          "\"supported_modes\":[\"oral_health\",\"fat_burning\"],"
          "\"ota_supported\":true,"
          "\"consumer_capabilities\":{\"claim_required\":true,"
          "\"session_resume_supported\":true,"
          "\"power_state_reporting_supported\":true}}}",
      "device.info.get handler should expose the transport-facing contract"
  );
}

void test_pairing_rpc_service_claim_begin_contract() {
  const auto store_path = make_claim_store_path();
  std::filesystem::remove(store_path);

  FileClaimStore store(store_path.string());
  ClaimService claim_service("DEVICE-321", store);
  const PairingRpcService rpc_service(
      make_device_info("AH-REV-D", true),
      claim_service
  );

  const auto success = rpc_service.handle_method(
      "device.claim.begin",
      "challenge-6"
  );
  const auto persisted = claim_service.load_claim_state();

  expect(
      success ==
          "{\"ok\":true,\"method\":\"device.claim.begin\",\"result\":{"
          "\"device_identity\":\"DEVICE-321\","
          "\"challenge\":\"challenge-6\","
          "\"proof\":\"" +
              persisted.claim_proof + "\"}}",
      "device.claim.begin handler should return the firmware contract payload"
  );

  FileClaimStore rebooted_store(store_path.string());
  ClaimService rebooted_claim_service("DEVICE-321", rebooted_store);
  const PairingRpcService rebooted_rpc_service(
      make_device_info("AH-REV-D", true),
      rebooted_claim_service
  );

  const auto duplicate = rebooted_rpc_service.handle_method(
      "device.claim.begin",
      "challenge-7"
  );
  expect(
      duplicate ==
          "{\"ok\":false,\"method\":\"device.claim.begin\","
          "\"error\":\"already_claimed\"}",
      "device.claim.begin handler should report stable duplicate-claim faults"
  );

  std::filesystem::remove(store_path);
}

}  // namespace

int main() {
  try {
    test_device_info_shape();
    test_protocol_major_detection();
    test_device_info_payload_is_stable();
    test_claim_begin_succeeds_and_persists();
    test_claim_begin_rejects_invalid_or_repeat_attempts();
    test_file_claim_store_survives_reboot();
    test_pairing_rpc_service_device_info_contract();
    test_pairing_rpc_service_claim_begin_contract();
  } catch (const std::exception& error) {
    std::cerr << "pairing_test failed: " << error.what() << "\n";
    return EXIT_FAILURE;
  }

  std::cout << "pairing_test passed\n";
  return EXIT_SUCCESS;
}
