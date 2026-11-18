# CPU / GPU / Governor per aplikasi — Rencana Implementasi

> **Untuk pekerja agentik:** WAJIB pakai superpowers:subagent-driven-development (disarankan) atau superpowers:executing-plans untuk mengerjakan rencana ini task demi task. Langkah memakai kotak centang (`- [ ]`).

**Tujuan:** App `IngresPerf` di repo addons: profil CPU/GPU/governor per aplikasi, tersimpan permanen, diterapkan otomatis (termasuk setelah reboot) lewat property + trigger `init`.

**Arsitektur:** Logika murni (Java, tanpa import Android) di `org.ingres.perf.logic` diuji di host dengan `javac`/`java`. Lapisan Android (Kotlin) hanya perekat: penyimpanan, deteksi aplikasi di depan, penulis property, UI Compose. Penulisan sysfs dilakukan `init` lewat `perf/init/init.ingres_perf.rc`.

**Tech Stack:** Java 17 (logika, uji host), Kotlin + Compose (app, pola sama dengan `leds/app`), Soong (`android_app`, `prebuilt_etc`), sepolicy system_ext.

**Spec:** `docs/superpowers/specs/2026-11-18-per-app-cpu-gpu-design.md`

## Batasan Global
- Cluster CPU: little `policy0`, big `policy4`, prime `policy7`. Frekuensi CPU dalam kHz, GPU dalam Hz.
- Governor GPU tidak ikut v1. Thermal tidak disentuh.
- Nilai hanya dari daftar kernel (`scaling_available_frequencies`, `scaling_available_governors`, GPU `available_frequencies`); min <= max.
- Diterapkan hanya setelah `sys.boot_completed`. Nilai saat keluar = snapshot sebelum diterapkan; kalau ada profil `*` (Default), itu dasarnya.
- Profil permanen (penyimpanan device-protected). Entri `*` = Default. Ada sakelar utama dan "Reset semua".
- Debounce 300 ms saat pindah aplikasi; layar mati = pulihkan ke dasar.
- Entri di Setelan lewat activity `com.android.settings.action.EXTRA_SETTINGS`; tanpa patch Setelan atau framework.
- Ikon: `docs/superpowers/assets/ic_launcher_art.png`, latar `#524558`. Ikon app LEDs tidak diubah.
- Commit sebagai kingD2N, tanpa trailer. Bahasa string UI: Indonesia (`values-in`) dan Inggris (`values`).

## Fokus Tinjauan (tiap baris punya tes di task pemilik)
1. Daftar kernel tidak terbaca/kosong (misal GPU ditolak SELinux): field itu disembunyikan/dilewati, tidak crash. (Task 1, 4)
2. Nilai tersimpan tidak ada lagi di daftar kernel (kernel diganti): dibuang saat diterapkan. (Task 1)
3. Pindah aplikasi cepat berturut-turut: yang terakhir menang, tanpa kedip pemulihan di antaranya. (Task 2)
4. Paket profil sudah dicopot: diabaikan dan dibersihkan dari daftar. (Task 2)
5. Reboot hanya dengan profil Default: diterapkan sekali setelah boot selesai, bukan sebelum. (Task 2)

## Struktur Berkas
- `perf/app/java/org/ingres/perf/logic/` — logika murni: `Profile`, `ProfileCodec`, `NodeReader`, `Limits`, `Snapshot`, `Validator`, `PerfNodes`, `PropPayload`, `PerfStateMachine`, `Debouncer`.
- `perf/app/test/host/org/ingres/perf/logic/HostTests.java` — uji host (tanpa JUnit; `check(name, cond)`, exit code 1 bila gagal).
- `perf/app/kotlin/org/ingres/perf/` — perekat Android: `PerfApplication`, `ForegroundWatcher`, `SysfsNodeReader`, `PropSink`, `ProfileStore`, `ui/PerfActivity`, `ui/PerfScreens`.
- `perf/init/init.ingres_perf.rc`, `perf/Android.bp` (modul rc), `perf/app/Android.bp`, `perf/app/AndroidManifest.xml`, `perf/app/res/`.
- `sepolicy/perf-private/`, `sepolicy/perf-private-a17/`; ubah `addons.mk`, `BoardConfigAddons.mk`, `README.md`.

Perintah uji host (dipakai semua task): `javac -d /tmp/perfout $(find perf/app/java perf/app/test -name '*.java') && java -cp /tmp/perfout org.ingres.perf.logic.HostTests perf/init/init.ingres_perf.rc` — lulus bila cetak `ALL PASS (<n>)`.

---

### Task 1: Model profil, kodek, batas kernel, validasi

**Files:** Create `logic/Profile.java`, `ProfileCodec.java`, `NodeReader.java`, `Limits.java`, `Validator.java`, `PerfNodes.java`; Test `HostTests.java`.

**Interfaces — Produces:**
- `final class Profile`: `static Profile empty()`, `boolean isEmpty()`, `int minKhz(int cluster)`, `int maxKhz(int cluster)`, `String governor(int cluster)`, `long gpuMinHz()`, `long gpuMaxHz()`, `Builder` (`cluster(int, int minKhz, int maxKhz, String gov)`, `gpu(long min, long max)`, `build()`). Indeks cluster 0=little, 1=big, 2=prime; 0/null = tidak diatur.
- `ProfileCodec`: `String encode(Profile)`, `Profile decode(String)` (tidak pernah melempar; sampah → `empty()`), `String encodeAll(Map<String,Profile>)`, `Map<String,Profile> decodeAll(String)` (satu baris `paket=profil`, kunci `*` = Default; baris rusak dilewati).
- `interface NodeReader { String read(String path); }` (null bila tak terbaca).
- `Limits.read(NodeReader)`: `int[] cpuFreqsKhz(int cluster)`, `String[] cpuGovernors(int cluster)`, `long[] gpuFreqsHz()`; larik kosong bila tak terbaca.
- `Validator.validate(Profile, Limits) -> Profile`: buang nilai di luar daftar; daftar kosong = field dibuang; bila min > max buang min.
- `PerfNodes`: `String cpuMin(int)`, `cpuMax(int)`, `cpuGov(int)`, `cpuAvailFreqs(int)`, `cpuAvailGovs(int)` di `/sys/devices/system/cpu/cpufreq/policy{0,4,7}/…`; konstanta `GPU_MIN`, `GPU_MAX`, `GPU_AVAIL` di `/sys/class/kgsl/kgsl-3d0/devfreq/{min_freq,max_freq,available_frequencies}`.

- [ ] **Step 1: Tulis tes gagal** di `HostTests.java`: `codecRoundTrip` (encode→decode sama), `codecGarbageGivesEmpty` ("", "x=", "c0max=abc"), `decodeAllSkipsBadLines`, `validatorDropsValueNotInList` (max 9999999 tidak ada → 0; ini juga tes Fokus 2), `validatorDropsMinWhenAboveMax`, `limitsEmptyWhenNodeUnreadable` (NodeReader palsu mengembalikan null → semua larik kosong; Fokus 1), `validatorEmptyLimitsDropsField`.
- [ ] **Step 2: Jalankan** perintah uji host; harapan: gagal kompilasi (kelas belum ada).
- [ ] **Step 3: Implementasikan** kelas di atas (Java murni, tanpa import `android.*`). Format kodek bebas selama round-trip dan tahan sampah.
- [ ] **Step 4: Jalankan** perintah uji host; harapan `ALL PASS`.
- [ ] **Step 5: Commit** `ingres: addons: perf: Add profile model, codec and validator`.

### Task 2: Snapshot, payload property, mesin status, debounce

**Files:** Create `logic/Snapshot.java`, `PropPayload.java`, `PerfStateMachine.java`, `Debouncer.java`; Test `HostTests.java`.

**Interfaces — Consumes:** Task 1 (`Profile`, `Limits`, `Validator`, `PerfNodes`, `NodeReader`).
**Produces:**
- `Snapshot.read(NodeReader)`: nilai saat ini per cluster (min, max, governor) dan GPU (min, max); getter sama seperti `Profile`; `Snapshot.fromProfile`-tidak ada (YAGNI).
- `PropPayload.build(Profile target, Snapshot base, Limits limits, int seq) -> Map<String,String>`: selalu berisi semua kunci `sys.ingres.perf.cpu{0,4,7}_{min,max,gov,floor}`, `sys.ingres.perf.gpu_{min,max,floor}` dan `sys.ingres.perf.apply`=seq. Field kosong di target diisi nilai dari `base` (init selalu menulis nilai konkret). `*_floor` = frekuensi terendah daftar kernel (kunci urutan tulis agar min/max tidak ditolak kernel). `static List<String> keys()`.
- `interface Sink { void write(Map<String,String> props); }`
- `PerfStateMachine(Sink, Supplier<Snapshot>, Supplier<Limits>, Predicate<String> isInstalled)` dengan `setEnabled(boolean)`, `setProfiles(Map<String,Profile>)`, `onForeground(String pkg)`, `onScreenOff()`, `onBootCompleted()`, `Map<String,Profile> profiles()` (sudah dibersihkan dari paket tak terpasang).
- `Debouncer(Scheduler, long delayMs)` dengan `submit(String)`; `interface Scheduler { void post(Runnable r, long delayMs); void cancel(); }`; nilai terakhir yang menang.

- [ ] **Step 1: Tulis tes gagal:** `payloadFillsEmptyFieldsFromSnapshot`, `payloadHasFloorAndApplySeq`, `smAppliesAppProfileOnForeground`, `smTakesSnapshotOnlyOnce` (A→B berprofil: satu snapshot, tanpa pemulihan di antara; Fokus 3), `smRestoresSnapshotWhenLeavingToUnprofiledApp`, `smFallsBackToDefaultProfile`, `smRestoresToDefaultWhenLeavingAppProfile`, `smScreenOffRestoresBase`, `smDisabledRestoresAndIgnores`, `smBootAppliesDefaultOnlyAfterBootCompleted` (sebelum `onBootCompleted` tidak ada tulis; sesudahnya sekali; Fokus 5), `smDropsUninstalledPackages` (Fokus 4), `debouncerLastWins` (tiga `submit` cepat → satu callback nilai terakhir).
- [ ] **Step 2: Jalankan** perintah uji host; harapan gagal kompilasi.
- [ ] **Step 3: Implementasikan** kelas-kelas di atas. Mesin status memvalidasi lewat `Validator` tiap kali menerapkan (bukan hanya saat disimpan) sehingga Fokus 2 terpenuhi juga di jalur terapan.
- [ ] **Step 4: Jalankan** perintah uji host; harapan `ALL PASS`.
- [ ] **Step 5: Commit** `ingres: addons: perf: Add payload builder, state machine and debouncer`.

### Task 3: init rc, modul Soong, sepolicy, konsistensi

**Files:** Create `perf/init/init.ingres_perf.rc`, `perf/Android.bp`, `sepolicy/perf-private/{ingres_perf.te,property_contexts}`, `sepolicy/perf-private-a17/ingres_perf.te`; Modify `HostTests.java`, `addons.mk`, `BoardConfigAddons.mk`.

**Interfaces — Consumes:** `PropPayload.keys()`, `PerfNodes`.
**Produces:** modul `init.ingres_perf.rc` (`prebuilt_etc`, `system_ext_specific: true`, `sub_dir: "init"`); toggle `INGRES_ADDONS_PERF ?= true`; paket `IngresPerf` dan `init.ingres_perf.rc` masuk `PRODUCT_PACKAGES` bila toggle `true`.

- [ ] **Step 1: Tulis tes gagal** `rcMatchesPayloadAndNodes(rcPath)`: rc punya satu pemicu `on property:sys.ingres.perf.apply=*`; tiap kunci dari `PropPayload.keys()` muncul sebagai `${kunci}`; tiap node `PerfNodes` muncul di baris `write`; per node urutannya: tulis `*_floor` ke min, lalu max, lalu min target, lalu governor CPU sebelum semuanya.
- [ ] **Step 2: Jalankan**; harapan gagal (rc belum ada).
- [ ] **Step 3: Tulis rc** dengan `write <node> ${sys.ingres.perf.<kunci>}`. Sepolicy: `type ingres_perf_prop` + `system_ext_internal_prop`/`system_internal_prop`, `set_prop(platform_app, ingres_perf_prop)`; berkas `-a17` untuk `platform_app_36` (ikut pola `sepolicy/private-a17`); `property_contexts`: `sys.ingres.perf. u:object_r:ingres_perf_prop:s0`. `BoardConfigAddons.mk`: tambah kedua direktori hanya bila `INGRES_ADDONS_PERF=true` (yang `-a17` hanya bila `INGRES_ADDONS_A17=true`).
- [ ] **Step 4: Jalankan** perintah uji host; harapan `ALL PASS`.
- [ ] **Step 5: Commit** `ingres: addons: perf: Add init rc, sepolicy and build wiring`.

> **Titik periksa di HP (keputusan init vs daemon):** setelah build pertama dipasang, di terminal root: `setprop sys.ingres.perf.cpu0_max <nilai valid>; setprop sys.ingres.perf.apply 1` lalu `cat` node-nya. Bila `init` menolak (lihat `dmesg | grep avc` untuk `init`), hentikan dan minta rencana cadangan (daemon native seperti HAL LED).

### Task 4: Perekat Android — penyimpanan, pembaca, penulis, pengawas aplikasi

**Files:** Create `kotlin/org/ingres/perf/{PerfApplication,ForegroundWatcher,SysfsNodeReader,PropSink,ProfileStore}.kt`, `perf/app/Android.bp`, `perf/app/AndroidManifest.xml`; Modify `HostTests.java` (tes pengkabelan tidak perlu).

**Interfaces — Consumes:** `NodeReader`, `Sink`, `PerfStateMachine`, `Debouncer`, `ProfileCodec`.
**Produces:**
- `class SysfsNodeReader : NodeReader` — membaca berkas dengan trim; null bila gagal (GPU ditolak SELinux → daftar kosong; Fokus 1).
- `class PropSink : Sink` — `SystemProperties.set` tiap kunci, `apply` terakhir.
- `class ProfileStore(ctx: Context)` — pakai `createDeviceProtectedStorageContext()`; `fun load(): Map<String,Profile>`, `fun save(Map<String,Profile>)`, `var enabled: Boolean`, `fun resetAll()`.
- `class ForegroundWatcher(ctx, onTop: (String) -> Unit, onScreenOff: () -> Unit)` — `registerTaskStackListener` (`ActivityTaskManager.getService()`), paket dari task teratas terbaru; `ACTION_SCREEN_OFF`.
- `class PerfApplication : Application` — `persistent` di manifest; rangkai `ProfileStore` → `PerfStateMachine` → `PropSink`; `ForegroundWatcher` → `Debouncer(300 ms, Handler)` → `onForeground`; `BOOT_COMPLETED` → `onBootCompleted`. Android.bp: `IngresPerf`, `platform_apis`, `privileged`, `system_ext_specific`, `certificate: "platform"`, `package_name: "org.ingres.perf"`, srcs Kotlin + Java logika.

- [ ] **Step 1:** Tulis kelas-kelas dan manifest (izin: `MANAGE_ACTIVITY_TASKS`, `RECEIVE_BOOT_COMPLETED`, `QUERY_ALL_PACKAGES`; `android:persistent="true"`).
- [ ] **Step 2: Verifikasi statis:** `xmllint --noout perf/app/AndroidManifest.xml` tanpa error; perintah uji host tetap `ALL PASS`.
- [ ] **Step 3: Commit** `ingres: addons: perf: Add Android glue (store, watcher, sink)`.

### Task 5: UI Compose, entri Setelan, ikon

**Files:** Create `kotlin/org/ingres/perf/ui/{PerfActivity,PerfScreens}.kt`, `res/values/strings.xml`, `res/values-in/strings.xml`, `res/values/colors.xml`, `res/mipmap-anydpi/ic_launcher.xml`, `res/drawable-nodpi/ic_launcher_art.png` (salin dari `docs/superpowers/assets/`); Modify `AndroidManifest.xml`.

**Interfaces — Consumes:** `ProfileStore`, `Limits.read(SysfsNodeReader())`, `Validator`.
**Produces:** `PerfActivity` (ComponentActivity) menampilkan: sakelar utama, entri "Default" di paling atas, daftar aplikasi berikon peluncur (pencarian), ketuk → halaman detail (dropdown min/max/governor per cluster dari `Limits`, min/max GPU; bagian GPU disembunyikan bila `gpuFreqsHz()` kosong), tombol "Reset semua" (dengan konfirmasi). Manifest: filter `com.android.settings.action.EXTRA_SETTINGS`, metadata `com.android.settings.category` = `com.android.settings.category.ia.system`, judul/ringkasan/urutan. Ikon adaptif: `<background>` = `@color/ic_launcher_background` (`#524558`), `<foreground>` = `@drawable/ic_launcher_art`.

- [ ] **Step 1:** Tulis UI dan sumber daya; string Indonesia dan Inggris lengkap.
- [ ] **Step 2: Verifikasi statis:** `xmllint --noout` semua XML di `perf/app/res` dan manifest; `python3 -c "from PIL import Image; assert Image.open('perf/app/res/drawable-nodpi/ic_launcher_art.png').size==(432,432)"`; perintah uji host `ALL PASS`.
- [ ] **Step 3: Commit** `ingres: addons: perf: Add settings UI, Settings entry and app icon`.

### Task 6: Dokumentasi dan uji di HP (AxionOS)

**Files:** Modify `README.md`.

- [ ] **Step 1:** Tambah bagian "CPU/GPU per aplikasi" di README (toggle `INGRES_ADDONS_PERF`, cara mematikan, titik periksa Task 3).
- [ ] **Step 2: Push** ke branch `addons`; build AxionOS lewat menu 1 (sync + build).
- [ ] **Step 3: Uji di HP**, laporkan hasilnya: (a) entri muncul di Setelan; (b) profil di aplikasi X mengubah `scaling_max_freq` saat X dibuka dan memulihkan saat keluar; (c) profil Default tetap berlaku setelah reboot; (d) sakelar utama dan "Reset semua" memulihkan. Perintah baca-saja untuk daftar frekuensi dan governor ikut dikirim di sini.
- [ ] **Step 4: Commit** `ingres: addons: perf: Document per-app CPU/GPU control`.
