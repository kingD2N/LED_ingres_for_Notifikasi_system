#
# SPDX-FileCopyrightText: 2026 kingD2N
# SPDX-License-Identifier: Apache-2.0
#
# Paket tambahan ROM POCO F4 GT (ingres):
#   GameKeys, LED (app Leds + lampu status), Dolby Lunaris, JamesDSP, MIUI Camera.
#
# Pakai dari device.mk:
#   INGRES_ADDONS_ROM := los      # LineageOS / Lunaris (dan turunan LineageOS)
#   INGRES_ADDONS_ROM := aospa    # AOSPA / PenguinOS
#   $(call inherit-product-if-exists, vendor/ingres/addons/addons.mk)
# Matikan satu paket dengan INGRES_ADDONS_<NAMA> := false sebelum inherit,
# misal INGRES_ADDONS_MIUICAMERA := false.

INGRES_ADDONS_PATH := vendor/ingres/addons
INGRES_ADDONS_ROM ?= los

INGRES_ADDONS_GAMEKEYS ?= true
INGRES_ADDONS_LEDS ?= true
INGRES_ADDONS_DOLBY ?= true
INGRES_ADDONS_JAMESDSP ?= true
INGRES_ADDONS_MIUICAMERA ?= true

PRODUCT_SOONG_NAMESPACES += \
    $(INGRES_ADDONS_PATH)

# GameKeys (Ingres-Centre/android_vendor_xiaomi_gamekey)
ifeq ($(INGRES_ADDONS_GAMEKEYS),true)
$(call inherit-product-if-exists, vendor/xiaomi/gamekey/common-vendor.mk)
endif

# LED: app Leds + service aw22xxx + lampu status (notifikasi / baterai)
ifeq ($(INGRES_ADDONS_LEDS),true)
PRODUCT_PACKAGES += \
    IngresLeds \
    IngresAddonsFrameworksOverlay \
    vendor.lineage.leds-service.ingres
endif

# Dolby Lunaris
ifeq ($(INGRES_ADDONS_DOLBY),true)
ifeq ($(INGRES_ADDONS_ROM),aospa)
# AOSPA/PenguinOS: blob Dolby sudah ada di vendor/xiaomi/sm8450-common,
# cukup ganti UI XiaomiDolby dengan LunarisDolby (packages/apps/DolbyUI).
PRODUCT_PACKAGES += \
    LunarisDolby
else
# LineageOS/Lunaris: stack Dolby lengkap dari hardware/dolby.
$(call inherit-product-if-exists, hardware/dolby/dolby.mk)
endif
endif

# JamesDSP (efek audio legacy, cocok dengan audio HAL HIDL ingres)
ifeq ($(INGRES_ADDONS_JAMESDSP),true)
$(call inherit-product-if-exists, vendor/JamesDSP/config.mk)
endif

# MIUI Camera (vendor dari Gurinbone, konfigurasi dari Ingres-stuff)
ifeq ($(INGRES_ADDONS_MIUICAMERA),true)
MIUICAMERA_DEVICE_PATH := device/xiaomi/miuicamera-ingres
ifneq ($(wildcard $(MIUICAMERA_DEVICE_PATH)/configs),)
PRODUCT_SOONG_NAMESPACES += \
    $(MIUICAMERA_DEVICE_PATH)

PRODUCT_COPY_FILES += \
    $(MIUICAMERA_DEVICE_PATH)/configs/public.libraries-xiaomi.txt:$(TARGET_COPY_OUT_SYSTEM)/etc/public.libraries-xiaomi.txt \
    $(MIUICAMERA_DEVICE_PATH)/configs/privapp-permissions-miuicamera.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/privapp-permissions-miuicamera.xml \
    $(MIUICAMERA_DEVICE_PATH)/configs/miuicamera-hiddenapi-package-allowlist.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/miuicamera-hiddenapi-package-allowlist.xml
endif
$(call inherit-product-if-exists, vendor/xiaomi/miuicamera-ingres/miuicamera-ingres-vendor.mk)
endif
