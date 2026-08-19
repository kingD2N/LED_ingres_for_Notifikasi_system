# LED Control - POCO F4 GT (ingres)

Native init service (bukan Magisk module) untuk kontrol LED notifikasi
`aw22xxx_led` di POCO F4 GT / ingres, dibangun buat nempel permanen di
ROM build (AxionOS / LineageOS 23.2 based).

## State yang ditangani

| Kondisi | Aksi |
|---|---|
| Battery full (plugged + `Full`/capacity 100%) | `effect=15` |
| Charging (proses) | `task0 "0 255 0"` |
| Battery low (≤15%, tidak charging) | `effect=8` |
| Notifikasi pending + layar mati | `effect=2` |

Prioritas: full > charging > low battery > notifikasi.

## Integrasi ke device tree

1. Copy folder `device/xiaomi/ingres/` di repo ini ke device tree situ
   (merge dengan yang sudah ada, jangan overwrite file lain).
2. Di `device.mk` utama, tambahkan:
   ```
   $(call inherit-product, device/xiaomi/ingres/led_control.mk)
   ```
3. Copy `sepolicy/vendor/led_control.te` ke folder sepolicy yang
   terdaftar di `BOARD_SEPOLICY_DIRS` / `BOARD_VENDOR_SEPOLICY_DIRS`
   pada `BoardConfig.mk` situ.
4. Tempel isi `sepolicy/vendor/file_contexts.snippet` ke
   `file_contexts` yang sudah ada di folder sepolicy yang sama.
5. Build & flash seperti biasa.

