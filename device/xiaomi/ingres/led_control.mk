# led_control.mk
# Include ini dari device.mk utama situ:
#   $(call inherit-product, device/xiaomi/ingres/led_control.mk)
# atau copy-paste isinya langsung ke device.mk kalau lebih simpel.

PRODUCT_COPY_FILES += \
    device/xiaomi/ingres/ledcontrol.sh:$(TARGET_COPY_OUT_VENDOR)/bin/ledcontrol.sh \
    device/xiaomi/ingres/init.ledcontrol.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.ledcontrol.rc
