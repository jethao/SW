#include "device_info_service.hpp"

#include <cstdint>

#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/logging/log.h>
#include <zephyr/sys/util.h>

LOG_MODULE_REGISTER(airhealth_device_info_service, LOG_LEVEL_INF);

namespace airhealth::fw::app {

namespace {

bt_uuid_128 kServiceUuid =
    BT_UUID_INIT_128(BT_UUID_128_ENCODE(0x6e400001, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e));
bt_uuid_128 kDeviceInfoUuid =
    BT_UUID_INIT_128(BT_UUID_128_ENCODE(0x6e400002, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e));

std::string g_device_info_payload;

ssize_t read_device_info(
    struct bt_conn* conn,
    const struct bt_gatt_attr* attr,
    void* buf,
    std::uint16_t len,
    std::uint16_t offset
) {
  ARG_UNUSED(conn);
  ARG_UNUSED(attr);
  return bt_gatt_attr_read(
      conn,
      attr,
      buf,
      len,
      offset,
      g_device_info_payload.data(),
      g_device_info_payload.size()
  );
}

BT_GATT_SERVICE_DEFINE(
    airhealth_gatt_service,
    BT_GATT_PRIMARY_SERVICE(&kServiceUuid.uuid),
    BT_GATT_CHARACTERISTIC(
        &kDeviceInfoUuid.uuid,
        BT_GATT_CHRC_READ,
        BT_GATT_PERM_READ,
        read_device_info,
        nullptr,
        nullptr
    )
);

const bt_data kAdvertisingData[] = {
    BT_DATA_BYTES(BT_DATA_FLAGS, BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR),
    BT_DATA(BT_DATA_NAME_COMPLETE, CONFIG_BT_DEVICE_NAME, sizeof(CONFIG_BT_DEVICE_NAME) - 1),
};

const bt_data kScanResponseData[] = {
    BT_DATA(BT_DATA_UUID128_ALL, kServiceUuid.val, sizeof(kServiceUuid.val)),
};

}  // namespace

int init_device_info_service(const DeviceInfo& device_info) {
  g_device_info_payload = "{"
                          "\"ok\":true,"
                          "\"method\":\"device.info.get\","
                          "\"result\":" +
      device_info_to_payload_json(device_info) + "}";
  LOG_INF(
      "Prepared device.info payload (%u bytes)",
      static_cast<unsigned int>(g_device_info_payload.size())
  );
  return 0;
}

int start_device_info_advertising() {
  const int rc = bt_le_adv_start(
      BT_LE_ADV_CONN_FAST_1,
      kAdvertisingData,
      ARRAY_SIZE(kAdvertisingData),
      kScanResponseData,
      ARRAY_SIZE(kScanResponseData)
  );
  if (rc != 0) {
    LOG_ERR("Failed to start BLE advertising (%d)", rc);
    return rc;
  }

  LOG_INF("BLE advertising started");
  return 0;
}

const std::string& device_info_payload() {
  return g_device_info_payload;
}

}  // namespace airhealth::fw::app
