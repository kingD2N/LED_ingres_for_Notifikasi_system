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
INGRES_ADDONS_JAMESDSP ?= true
INGRES_ADDONS_MIUICAMERA ?= true

ifeq ($(INGRES_ADDONS_GAMEKEYS),true)
-include vendor/xiaomi/gamekey/BoardConfigVendor.mk
endif

ifeq ($(INGRES_ADDONS_LEDS),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/private
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/vendor
else ifeq ($(INGRES_ADDONS_JAMESDSP),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/vendor
endif

ifeq ($(INGRES_ADDONS_MIUICAMERA),true)
ifneq ($(wildcard device/xiaomi/miuicamera-ingres/system.prop),)
TARGET_SYSTEM_PROP += device/xiaomi/miuicamera-ingres/system.prop
endif
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_ADDONS_PATH)/sepolicy/miuicamera-$(INGRES_ADDONS_ROM)
endif
