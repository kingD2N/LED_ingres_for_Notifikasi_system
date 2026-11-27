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

JamesDSP **mati bawaan** sejak 26 Nov 2026 (bentrok dengan suara video Dolby Vision); nyalakan dengan `INGRES_ADDONS_JAMESDSP := true`.

Matikan satu paket: `INGRES_ADDONS_GAMEKEYS / _LEDS / _DOLBY / _JAMESDSP / _MIUICAMERA / _DEBLOAT / _PERF := false`.
`INGRES_ADDONS_PARTS` (Ingres Parts) mati bawaan, lihat bagian di bawah.

## LED

- `vendor.lineage.leds-service.ingres` (system_ext) mengatur `/sys/class/leds/aw22xxx_led`
  dan menyediakan `vendor.lineage.leds.ILeds/default` untuk app Leds.
- Lampu status (dari [LED_ingres_for_Notifikasi_system](https://github.com/kingD2N/LED_ingres_for_Notifikasi_system)):
  baterai penuh = efek 15, charging = hijau, baterai lemah (≤15%) = efek 8,
  notifikasi saat layar mati = efek 2. Bisa dimatikan dari app Leds ("Lampu status").
  Efek pilihan di app dipasang lagi setelah lampu status selesai.

## CPU/GPU per aplikasi

App `IngresPerf` (`perf/`) menambah entri **CPU/GPU per Aplikasi** di Setelan > Sistem: frekuensi
min/max tiap cluster CPU (little/big/prime), governor CPU, dan min/max GPU, per aplikasi. Entri
**Default** dipakai setelah boot dan saat aplikasi di depan tidak punya profil. Profil tersimpan
permanen (storage device-protected), ada sakelar utama dan "Reset semua".

- Alur: app menyimpan profil, lalu mengisi property `sys.ingres.perf.*` dan terakhir
  `sys.ingres.perf.apply`; `init` (`perf/init/init.ingres_perf.rc`) yang menulis node sysfs.
- Hanya field yang diatur profil yang ditulis (kolom kosong dilewati, `init` mencatat error
  harmless di kmsg). Nilai hanya dari daftar kernel; urutan tulis governor, lantai, max, min.
- Nilai dasar (snapshot) diambil sebelum profil diterapkan dan dipulihkan saat keluar.
  Default diterapkan ~20 detik setelah boot selesai (menghindari tabrakan skrip boot vendor).
- Matikan: `INGRES_ADDONS_PERF := false` di `device.mk`. Hapus semua profil lewat "Reset semua".
- Penyimpanan memakai SharedPreferences device-protected (bukan DataStore).

Titik periksa di HP (setelah build pertama), terminal root:

```
setprop sys.ingres.perf.cpu0_max <nilai valid>; setprop sys.ingres.perf.apply 1
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
dmesg | grep avc | grep -e init -e perf
ls -Z /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq /sys/class/kgsl/kgsl-3d0/devfreq/max_freq
```

Bila `init` ditolak SELinux (avc), minta rencana cadangan (daemon native seperti HAL LED).
GPU: label node kgsl di ingres adalah `vendor_sysfs_kgsl`; aturan tulis untuk `init` ada di
`sepolicy/perf-vendor` dan dipasang otomatis bila tipenya ada di source. Bagian GPU di UI
disembunyikan bila daftar frekuensi tak terbaca oleh app.

Modul tuning lain yang menulis `scaling_max_freq`/governor berkala (mis. loop `while true; do sleep 5`
di `service.sh` modul KernelSU) menimpa profil dalam hitungan detik. Matikan bagian CPU modul itu
selama profil per aplikasi dipakai.

## Ingres Parts (uji coba)

App `IngresParts` (`parts/`) menambah entri **Ingres Parts** di halaman utama Setelan. **Mati bawaan**;
nyalakan dengan `INGRES_ADDONS_PARTS := true` di `device.mk` dan `BoardConfig.mk` (sebelum include
addons). Saat ini baru dinyalakan di branch `evox-cnb`.

| Fitur | Node | Catatan |
|---|---|---|
| Batas cas (normal / optimal 80% / batas 50–95%) | `qcom-battery/night_charging`, `input_suspend` | lanjut cas lagi 5% di bawah batas |
| Bypass charging (eksperimen) | `power_supply/battery/constant_charge_current` = 0 | arus asli disimpan dan dipulihkan, juga setelah reboot |
| Profil thermal (hemat/harian/seimbang/gaming/performa) | `thermal_message/sconfig` (11/0/20/13/10) | seimbang = bawaan ROM (20), tidak ditulis bila tak diubah |
| Mode game: daftar game, profil thermal game, sentuhan 480 Hz, overlay FPS & suhu | `touch_dev/bump_sample_rate` (1 = 480 Hz, 0 = 240 Hz, driver Goodix), `sde-crtc-0/measured_fps` | di luar game 240 Hz |
| Sentuhan 480 Hz di semua aplikasi | `touch_dev/bump_sample_rate` | pengganti "High touch polling rate" Setelan: sakelar itu dihilangkan (sm8450-common, `INGRES_ADDONS_PARTS`) |
| Mode warna Flat | `disp-DSI-0/disp_param` = `6 1` | ditulis ulang saat layar menyala (DC dimming tidak didukung panel) |
| Pembersih speaker | nada 165 Hz 30 detik ke speaker bawaan | volume dikembalikan setelahnya |
| Fitur lain | LED, CPU/GPU per Aplikasi, Dolby | hanya yang terpasang (GameKeys tidak punya halaman setelan) |

- Alur sama dengan Perf: app mengisi `sys.ingres.parts.*`, `init` (`parts/init/init.ingres_parts.rc`)
  menulis sysfs. Flat memakai pemicu `sys.ingres.parts.flat_apply` (nonce) supaya bisa ditulis ulang.
- SELinux: `sepolicy/parts-private` (+`-a17`) untuk properti dan `sysfs_thermal`; `sepolicy/parts-vendor`
  (+`-a17`) untuk node baterai/sentuh/layar/FPS, dipasang otomatis bila keempat tipenya ada di source.
- Logika murni di `parts/app/java` diuji di host:
  `javac -d out $(find app/java app/test -name "*.java") && java -cp out org.ingres.parts.logic.HostTests`
- Satu pintu: selama Ingres Parts terpasang, ikon LEDs, entri Setelan > Sistem > CPU/GPU per Aplikasi,
  serta entri Setelan > Suara dan ikon Dolby dimatikan (`EntryPoints.kt`). Ketiganya kini activity-alias
  (`.LauncherEntry` / `.SettingsEntry`); activity aslinya dibuka dari menu Ingres Parts. Tile Quick Settings
  Dolby dan tombol equalizer aplikasi musik tetap jalan. ROM tanpa Ingres Parts tidak berubah.
- Modul KernelSU yang menulis `sconfig` (mis. DThermal) menimpa profil thermal; matikan salah satunya.

Titik periksa di HP, terminal root:

```
getprop | grep ingres.parts
cat /sys/class/qcom-battery/input_suspend /sys/devices/virtual/thermal/thermal_message/sconfig
dmesg | grep avc | grep -e ingres -e init
```

## Debloat

Modul `IngresDebloat` (`debloat/Android.bp`) membuang aplikasi lewat `overrides`, jadi aplikasi
itu tidak ikut dibangun ke ROM. Nama yang tidak ada di ROM tertentu diabaikan, sehingga satu
daftar dipakai untuk LineageOS, Lunaris, AOSPA, dan PenguinOS. Daftar lengkap ada di
`debloat/ingres-debloat.txt` (juga terpasang di `/system_ext/etc/ingres-debloat.txt`).

Yang dibuang: screensaver, easter egg, bookmark lama, live wallpaper picker, WAP push, layanan
cetak bawaan, backup log panggilan, musik/EQ AOSP lama, viewer tag NFC, updater OTA resmi ROM
(tidak berguna untuk build unofficial ingres), aksesibilitas Google, Speech Services/Google TTS,
Android Auto stub, Digital Wellbeing, Personal Safety, Family Link, Device Policy, "Hey Google"
enrollment, Google Feedback, Recorder, dan aplikasi Google yang bisa dipasang lagi dari Play
Store (Calendar, Drive, Gemini, Gmail, Maps, YouTube).

Sengaja **tidak** dibuang dari zip debloat contoh, karena berisiko:

| Aplikasi | Alasan tetap dipasang |
|---|---|
| Stk (SIM Toolkit) | menu SIM operator (mis. menu Telkomsel) butuh ini |
| PrintSpooler | aplikasi yang memanggil fitur cetak bisa crash tanpa ini |
| QuickAccessWallet, GWP | pembayaran NFC / Google Wallet |
| Seedvault | satu-satunya aplikasi backup di build vanilla (tanpa GApps) |
| OmniJaws | penyedia cuaca untuk lockscreen/clock ROM; widget bisa error tanpa ini |
| Chrome / Trichrome, Photos | browser dan galeri utama build GMS; Trichrome dipakai WebView |
| EmergencyInfo | info darurat (keselamatan) |
| PartnerSetup, GooglePartnerSetup | dipakai Setup Wizard Google |
| Turbo (Device Health Services) | baterai adaptif dan perkiraan sisa baterai |
| bcr (perekam panggilan), MatLog | fitur ROM; MatLog berguna untuk ambil log tanpa PC |

Matikan debloat: `INGRES_ADDONS_DEBLOAT := false` di `device.mk` sebelum baris addons.
