#!/system/bin/sh
# ============================================================
#  LED Control - POCO F4 GT (ingres)  [native init service version]
#  Driver: /sys/class/leds/aw22xxx_led (Awinic AW22xxx)
#
#  Versi ini dijalankan lewat init.rc (bukan Magisk service.sh),
#  jadi jalan sendiri sebagai root - semua wrapper `su -c` dibuang
#  karena tidak perlu dan tidak akan tersedia tanpa Magisk.
#  Nunggu boot juga dihapus dari sini - itu sekarang jadi tugas
#  init lewat trigger `on property:sys.boot_completed=1` di
#  init.ledcontrol.rc, bukan polling dari dalam script.
#
#  Mapping FINAL - dikonfirmasi bekerja penuh di device (POCO F4 GT /
#  ingres, AxionOS), berdasarkan LED_POCO_F4_GT_ingres_Final.zip:
#    - Battery Full     -> effect=15
#    - Notifikasi       -> effect=2   (layar harus mati)
#    - Charging proses  -> task0 "0 255 0" (mekanisme lama)
#    - Battery low      -> effect=8
#
#  PENTING: effect JANGAN PERNAH diisi 0 (numeric) - pernah bikin
#  device freeze di pengujian sebelumnya.
#
#  Prioritas: full > charging > low battery > notifikasi
#  DEBUG: tiap loop nulis status ke DEBUGLOG (overwrite terus).
# ============================================================

LED="/sys/class/leds/aw22xxx_led"
BATT="/sys/class/power_supply/battery/status"
CAP="/sys/class/power_supply/battery/capacity"
DEBUGLOG="/data/local/tmp/led_status.txt"

LOW_BATT_THRESHOLD=15   # persen baterai dianggap "low battery"

FULL_EFFECT=15
NOTIF_EFFECT=2
LOWBATT_EFFECT=8

SENSE_DELAY=2

# --- tweak bawaan module (tidak berkaitan dgn LED, tetap dipertahankan) ---
for q in /sys/block/*/queue/scheduler; do
    echo mq-deadline > "$q" 2>/dev/null
done
for q in /sys/block/*/queue; do
    echo 0   > "$q/add_random"    2>/dev/null
    echo 0   > "$q/rotational"    2>/dev/null
    echo 0   > "$q/iostats"       2>/dev/null
    echo 256 > "$q/read_ahead_kb" 2>/dev/null
done
device_config put activity_manager max_cached_processes 64 2>/dev/null
service call activity 64 i32 64 2>/dev/null
# ---------------------------------------------------------------------

# dipakai buat Full, Notifikasi, Battery low (semua lewat effect+cfg)
led_apply_effect() {
    # $1 = nilai effect (angka index)
    echo 0    > "$LED/hwen"   2>/dev/null
    echo 1    > "$LED/hwen"   2>/dev/null
    echo "$1" > "$LED/effect" 2>/dev/null
    echo 1    > "$LED/cfg"    2>/dev/null
}

# dipakai KHUSUS buat Charging proses (mekanisme lama, task0-based)
led_apply_task0_charging() {
    echo none > "$LED/trigger"    2>/dev/null
    echo 1    > "$LED/hwen"       2>/dev/null
    echo "0 255 0" > "$LED/task0" 2>/dev/null
    echo 200  > "$LED/brightness" 2>/dev/null
}

led_off() {
    echo 0 > "$LED/brightness" 2>/dev/null
    echo 0 > "$LED/hwen"       2>/dev/null
}

notif_count() {
    dumpsys notification 2>/dev/null | grep -c "NotificationRecord"
}

state="off"    # off | full | charging | lowbatt | notif

while true; do
    batt="$(cat "$BATT" 2>/dev/null)"
    cap="$(cat "$CAP" 2>/dev/null)"
    wraw="$(dumpsys power 2>/dev/null | grep -m1 -i 'akefulness')"
    cnt="$(notif_count)"

    case "$wraw" in
        *[Aa]sleep*|*[Dd]ozing*) scr_off=1 ;;
        *) scr_off=0 ;;
    esac

    if [ "$batt" = "Full" ] || { [ -n "$cap" ] && [ "$cap" -ge 100 ] 2>/dev/null; }; then
        target="full"
    elif [ "$batt" = "Charging" ]; then
        target="charging"
    elif [ -n "$cap" ] && [ "$cap" -le "$LOW_BATT_THRESHOLD" ] 2>/dev/null; then
        target="lowbatt"
    elif [ "${cnt:-0}" -gt 0 ] 2>/dev/null && [ "$scr_off" = "1" ]; then
        target="notif"
    else
        target="off"
    fi

    echo "$(date '+%H:%M:%S') batt=$batt cap=$cap cnt=$cnt scr_off=$scr_off target=$target state=$state" > "$DEBUGLOG" 2>/dev/null

    if [ "$target" = "off" ]; then
        if [ "$state" != "off" ]; then
            led_off
            state="off"
        fi
    elif [ "$state" != "$target" ]; then
        case "$target" in
            full)     led_apply_effect "$FULL_EFFECT" ;;
            notif)    led_apply_effect "$NOTIF_EFFECT" ;;
            lowbatt)  led_apply_effect "$LOWBATT_EFFECT" ;;
            charging) led_apply_task0_charging ;;
        esac
        state="$target"
    fi

    sleep "$SENSE_DELAY"
done
