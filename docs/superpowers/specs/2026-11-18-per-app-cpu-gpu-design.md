# CPU / GPU / Governor per aplikasi (ingres-addons)

Status: disetujui kingD2N (2026-11-18). Dicoba di AxionOS dulu.

## Tujuan dan alasan
kingD2N ingin mengatur secara manual, per aplikasi: batas frekuensi CPU (tiap
cluster: little policy0, big policy4, prime policy7), governor CPU, dan frekuensi
GPU, supaya performa ROM bisa diatur sesuai aplikasi (hemat untuk satu aplikasi,
tenaga penuh untuk game tertentu). Tampilannya seperti "FPS per aplikasi": ada
daftar aplikasi di Setelan.

Pengaturan harus **permanen**: sekali diatur, tersimpan dan tetap berlaku
sesudah reboot (kata kingD2N: "paten"). Nilai sysfs di kernel pasti kembali ke
awal tiap reboot, jadi yang dipertahankan adalah profilnya; layanan menerapkannya
lagi otomatis.

## Yang diputuskan bersama
- Pilihan A: layanan add-on + halaman sendiri di repo addons (bukan patch
  framework/Setelan per ROM). Dipilih kingD2N.
- Profil tersimpan permanen dan diterapkan ulang setelah boot dan setiap aplikasi
  dibuka.

## Goals
- Per aplikasi: min/max frekuensi tiap cluster CPU, governor CPU, min/max
  frekuensi GPU. Kosong = tidak diubah.
- Entri "Default" di paling atas daftar: profil untuk semua aplikasi yang tidak
  punya profil sendiri. Juga diterapkan setelah boot selesai (mengisi kebutuhan
  "frekuensi tidak berubah setelah reboot").
- Satu kode untuk semua ROM yang memuat ingres-addons (13 ROM), tanpa patch
  framework.
## Non-goals
- Tidak mengubah thermal. Tidak menggantikan Kernel Manager. Tidak mengatur
  governor GPU, ukuran cluster, atau core online di versi pertama.
## Anti-goals (dianggap gagal walau jalan)
- HP lambat permanen tanpa jalan keluar.
- Menimpa pengaturan modul/Kernel Manager kingD2N secara diam-diam.
- Bootloop karena salah atur.

## Pendekatan yang dipertimbangkan
A. Add-on: app platform-signed + penulis sysfs. **Dipilih.** Satu kode untuk semua ROM.
B. Meniru FPS persis (patch Setelan + framework + init): tampilan sama, tapi patch
   per ROM dan pemulihan nilai rumit. Ditolak.
C. Perf HAL/hint bawaan: tidak bisa atur frekuensi manual. Ditolak.

## Rancangan (pilihan saya, bisa dikoreksi)
1. **App `IngresPerf`** di `perf/app` (pola sama dengan `leds/app`: Compose,
   platform-signed, priv-app system_ext). Memegang profil di DataStore (permanen),
   mendeteksi aplikasi di depan lewat TaskStackListener (seperti GameKeys),
   menerapkan profil, dan memulihkan saat keluar.
2. **Entri di Setelan** lewat activity dengan metadata EXTRA_SETTINGS (tanpa patch
   aplikasi Setelan). Kategori tepatnya dicek di Setelan AxionOS saat implementasi.
3. **Penulis sysfs**: pilihan pertama lewat property + trigger `init` (init sudah
   boleh menulis node ini di rc vendor, tidak butuh domain SELinux baru). App
   menulis semua nilai ke `sys.ingres.perf.*` lalu satu property `apply`;
   `on property:sys.ingres.perf.apply=*` menulis semua node sekaligus. Cadangan
   kalau init atau sepolicy menolak: daemon native seperti HAL LED. Diputuskan
   lewat uji coba singkat di HP.
4. **Keamanan**
   - Nilai hanya dari daftar yang dibaca kernel (`scaling_available_frequencies`,
     `scaling_available_governors`, GPU `available_frequencies`); min <= max.
   - Diterapkan hanya setelah `sys.boot_completed`, jadi tidak bisa bootloop.
   - Saat keluar dari aplikasi, nilai yang dipulihkan adalah nilai yang terbaca
     sebelum diterapkan (bukan default pabrik), jadi pengaturan modul/Kernel
     Manager tetap dihormati. Profil "Default" mengubah pemulihan itu: kalau ada
     profil Default, itulah nilai dasarnya.
   - Sakelar utama "Aktifkan" dan tombol "Reset semua".
   - Debounce ~300 ms saat berpindah aplikasi; layar mati dipulihkan ke dasar.

## Diserahkan ke pembangun (pilihan saya, kalau tidak setuju kabari)
- Detail UI: satu halaman daftar aplikasi, ketuk membuka halaman detail dengan
  dropdown per cluster, governor, dan GPU.
- Cara membaca daftar frekuensi GPU dari app (sepolicy lintas partisi): uji
  coba di HP; cadangan: init menulis daftarnya ke property saat boot.
- Multi-window: pakai aplikasi top-resumed.

## Ikon
Ikon app `IngresPerf` memakai gambar yang dikirim kingD2N (foto profil GitHub-nya).
Berkas siap pakai: `docs/superpowers/assets/ic_launcher_art.png` (432x432,
foreground adaptif; gambar mengisi area aman 72/108 di tengah). Latar adaptif:
warna solid #524558. Ikon app LEDs tidak diubah (belum diminta).

## Verifikasi
- Baca-saja di HP: daftar frekuensi dan governor tiap policy dan GPU (satu perintah).
- Buka aplikasi dengan profil: `scaling_max_freq` berubah; keluar: kembali.
- Reboot: profil Default terterap setelah boot selesai.
