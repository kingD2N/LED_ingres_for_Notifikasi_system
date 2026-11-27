#
# SPDX-FileCopyrightText: 2026 kingD2N
# SPDX-License-Identifier: Apache-2.0
#
# Pakai dari BoardConfig.mk (set INGRES_ADDONS_ROM sama seperti di device.mk):
#   INGRES_ADDONS_ROM := los | aospa
#   -include vendor/ingres/addons/BoardConfigAddons.mk

INGRES_ADDONS_PATH := vendor/ingres/addons
INGRES_ADDONS_ROM ?= los

INGRES_ADDONS_GAMEKEYS ?= true
INGRES_ADDONS_LEDS ?= true
INGRES_ADDONS_PERF ?= true
INGRES_ADDONS_PARTS ?= false
INGRES_ADDONS_JAMESDSP ?= false
INGRES_ADDONS_MIUICAMERA ?= true

ifeq ($(INGRES_ADDONS_GAMEKEYS),true)
-include vendor/xiaomi/gamekey/BoardConfigVendor.mk
endif

# Android 17: app platform dengan target SDK < 37 berjalan sebagai platform_app_36.
# Deteksi dari sepolicy sistem supaya ROM Android 16 (Lunaris) tidak terpengaruh.
INGRES_ADDONS_A17 := $(shell grep -qs 'domain=platform_app_36' system/sepolicy/private/seapp_contexts && echo true)

ifeq ($(INGRES_ADDONS_LEDS),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/private
ifeq ($(INGRES_ADDONS_A17),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/private-a17
endif
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/vendor
else ifeq ($(INGRES_ADDONS_JAMESDSP),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/vendor
endif

# Ingres Parts + CPU/GPU per Aplikasi (packages/apps/IngresParts): SELinux dari repo-nya sendiri
ifneq ($(filter true,$(INGRES_ADDONS_PARTS) $(INGRES_ADDONS_PERF)),)
INGRES_PARTS_APP := $(INGRES_ADDONS_PARTS)
INGRES_PARTS_PERF := $(INGRES_ADDONS_PERF)
-include packages/apps/IngresParts/BoardConfigParts.mk
endif

ifeq ($(INGRES_ADDONS_MIUICAMERA),true)
ifneq ($(wildcard device/xiaomi/miuicamera-ingres/system.prop),)
TARGET_SYSTEM_PROP += device/xiaomi/miuicamera-ingres/system.prop
endif
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/miuicamera-$(INGRES_ADDONS_ROM)
endif

# Dolby (hardware/dolby) menaruh manifest HAL-nya di DEVICE_MANIFEST_FILE. Pada
# sm8450-common yang memakai DEVICE_MANIFEST_SKUS, hal itu membuat cek VINTF saat
# membuat zip OTA ikut memeriksa SKU kosong (manifest tanpa target-level/kernel
# FCM) -> "Kernel FCM version is not specified". Pindahkan ke setiap SKU.
ifneq ($(DEVICE_MANIFEST_SKUS),)
INGRES_ADDONS_SKU_MANIFESTS := $(filter hardware/dolby/%,$(DEVICE_MANIFEST_FILE))
ifneq ($(INGRES_ADDONS_SKU_MANIFESTS),)
DEVICE_MANIFEST_FILE := $(filter-out $(INGRES_ADDONS_SKU_MANIFESTS),$(DEVICE_MANIFEST_FILE))
$(foreach sku, $(call to-upper, $(DEVICE_MANIFEST_SKUS)), \
    $(eval DEVICE_MANIFEST_$(sku)_FILES += $(INGRES_ADDONS_SKU_MANIFESTS)))
endif
endif
