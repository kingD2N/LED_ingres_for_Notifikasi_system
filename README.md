# ingres addons (branch `addons`)

Paket tambahan untuk semua ROM POCO F4 GT / Redmi K50 Gaming (ingres):
LineageOS 24, AOSPA, PenguinOS, Lunaris.

| Paket | Sumber | Path di source |
|---|---|---|
| GameKeys (tombol pop-up + app) | [Ingres-Centre/android_vendor_xiaomi_gamekey](https://github.com/Ingres-Centre/android_vendor_xiaomi_gamekey) `master` | `vendor/xiaomi/gamekey` |
| LED: app Leds + service + lampu status | repo ini (`leds/`, app dari [Ingres-Centre/android_packages_apps_leds](https://github.com/Ingres-Centre/android_packages_apps_leds)) | `vendor/ingres/addons` |
| Dolby Lunaris (LineageOS / Lunaris) | [Ghosuto/hardware_dolby](https://github.com/Ghosuto/hardware_dolby) `16` | `hardware/dolby` |
| Dolby Lunaris (AOSPA / PenguinOS, UI saja) | [Gurinbone/packages_apps_DolbyUI](https://github.com/Gurinbone/packages_apps_DolbyUI) `main` | `packages/apps/DolbyUI` |
| JamesDSP | [MillenniumOSS/android_vendor_JamesDSP](https://github.com/MillenniumOSS/android_vendor_JamesDSP) `sixteen` | `vendor/JamesDSP` |
| MIUI Camera | [Gurinbone/android_vendor_xiaomi_miuicamera-ingres](https://github.com/Gurinbone/android_vendor_xiaomi_miuicamera-ingres) `lineage-23.2` + [Ingres-stuff/android_device_xiaomi_miuicamera-ingres](https://github.com/Ingres-stuff/android_device_xiaomi_miuicamera-ingres) `lineage-21` | `vendor/xiaomi/miuicamera-ingres`, `device/xiaomi/miuicamera-ingres` |

Semua sudah tercantum di local manifest masing-masing ROM.

## Pemakaian di device tree

```make
# device.mk
INGRES_ADDONS_ROM := los      # atau aospa
$(call inherit-product-if-exists, vendor/ingres/addons/addons.mk)

# BoardConfig.mk (paling bawah)
INGRES_ADDONS_ROM := los      # atau aospa
-include vendor/ingres/addons/BoardConfigAddons.mk
```

Matikan satu paket: `INGRES_ADDONS_GAMEKEYS / _LEDS / _DOLBY / _JAMESDSP / _MIUICAMERA := false`.

## LED

- `vendor.lineage.leds-service.ingres` (system_ext) mengatur `/sys/class/leds/aw22xxx_led`
  dan menyediakan `vendor.lineage.leds.ILeds/default` untuk app Leds.
- Lampu status (dari [LED_ingres_for_Notifikasi_system](https://github.com/kingD2N/LED_ingres_for_Notifikasi_system)):
  baterai penuh = efek 15, charging = hijau, baterai lemah (≤15%) = efek 8,
  notifikasi saat layar mati = efek 2. Bisa dimatikan dari app Leds ("Lampu status").
  Efek pilihan di app dipasang lagi setelah lampu status selesai.
