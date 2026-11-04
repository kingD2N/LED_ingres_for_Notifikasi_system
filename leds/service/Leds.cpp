/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 *
 * Kontrol LED RGB belakang POCO F4 GT (ingres), driver leds-aw22xxx.
 *
 * Lampu status (notifikasi / charging / baterai) memakai urutan sysfs dari
 * kingD2N/LED_ingres_for_Notifikasi_system yang sudah dites di device.
 * Lampu status didahulukan; kalau status kembali "off", efek pilihan
 * pengguna di app Leds dipasang lagi.
 */

#define LOG_TAG "leds-ingres"

#include "Leds.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>

namespace aidl::vendor::lineage::leds {

namespace {

constexpr char kLedPath[] = "/sys/class/leds/aw22xxx_led/";
constexpr char kStatusLightProp[] = "persist.sys.ingres_leds.status_light";

// Urutan = aw22xxx_cfg_name[] di driver, index 0 (led_off) sengaja tidak
// dipakai: effect=0 pernah membuat device freeze. Index app i -> driver i + 1.
const std::vector<std::string> kEffects = {
        "On",           "Breath",       "Collision",    "Skyline",      "Flower",
        "Audio skyline", "Audio flower", "Red (1/3)",    "Green (1/3)",  "Blue (1/3)",
        "Red (2/4)",    "Green (2/4)",  "Blue (2/4)",   "Red",          "Green",
        "Blue",         "Yellow",       "Game",
};

// Efek bawaan lampu status (index driver), sesuai skrip LED kingD2N.
constexpr int kFullEffect = 15;
constexpr int kNotificationEffect = 2;
constexpr int kLowBatteryEffect = 8;

bool writeNode(const std::string& node, const std::string& value) {
    if (!android::base::WriteStringToFile(value, std::string(kLedPath) + node)) {
        PLOG(ERROR) << "Gagal menulis " << value << " ke " << node;
        return false;
    }
    return true;
}

void applyStockEffect(int driverEffect) {
    writeNode("hwen", "0");
    writeNode("hwen", "1");
    writeNode("effect", std::to_string(driverEffect));
    writeNode("cfg", "1");
}

}  // namespace

Leds::Leds() {
    mStatusLight = android::base::GetBoolProperty(kStatusLightProp, true);
}

ndk::ScopedAStatus Leds::isEnabled(bool* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    *_aidl_return = mEnabled;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::setEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(mLock);
    mEnabled = enabled;
    mAppliedState = -1;
    applyLocked();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::getCurrentEffect(int32_t* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    *_aidl_return = mEffect;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::setCurrentEffect(int32_t effect) {
    if (effect < 0 || effect >= static_cast<int32_t>(kEffects.size())) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    std::lock_guard<std::mutex> lock(mLock);
    mEffect = effect;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::getAvailableEffects(std::vector<std::string>* _aidl_return) {
    *_aidl_return = kEffects;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::getFrequency(int32_t* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    *_aidl_return = mFrequency;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::setFrequency(int32_t frequency) {
    if (frequency < 0) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    std::lock_guard<std::mutex> lock(mLock);
    mFrequency = frequency;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::getAllColors(std::vector<int32_t>* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    _aidl_return->assign(mColors.begin(), mColors.end());
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::setColor(int32_t index, int32_t color) {
    if (index < 0 || index >= static_cast<int32_t>(kNumColors)) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    std::lock_guard<std::mutex> lock(mLock);
    mColors[index] = color & 0xFFFFFF;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::flushConfig(bool useCustomColors) {
    std::lock_guard<std::mutex> lock(mLock);
    mUseCustomColors = useCustomColors;
    mAppliedState = -1;
    applyLocked();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::isStatusLightEnabled(bool* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    *_aidl_return = mStatusLight;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::setStatusLightEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(mLock);
    mStatusLight = enabled;
    android::base::SetProperty(kStatusLightProp, enabled ? "true" : "false");
    mAppliedState = -1;
    applyLocked();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Leds::setSystemState(int32_t state) {
    if (state < STATE_OFF || state > STATE_NOTIFICATION) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    std::lock_guard<std::mutex> lock(mLock);
    if (state == mSystemState) {
        return ndk::ScopedAStatus::ok();
    }
    mSystemState = state;
    applyLocked();
    return ndk::ScopedAStatus::ok();
}

void Leds::applyLocked() {
    int32_t want = (mStatusLight && mSystemState != STATE_OFF) ? mSystemState : STATE_OFF;
    if (want != STATE_OFF) {
        if (want != mAppliedState) {
            applyStatusLocked();
            mAppliedState = want;
        }
        return;
    }
    // tidak ada status sistem: kembali ke efek pilihan pengguna / mati
    if (mEnabled) {
        applyUserEffectLocked();
    } else {
        turnOffLocked();
    }
    mAppliedState = STATE_OFF;
}

void Leds::applyStatusLocked() {
    LOG(INFO) << "Lampu status " << mSystemState;
    switch (mSystemState) {
        case STATE_FULL:
            applyStockEffect(kFullEffect);
            break;
        case STATE_NOTIFICATION:
            applyStockEffect(kNotificationEffect);
            break;
        case STATE_LOW_BATTERY:
            applyStockEffect(kLowBatteryEffect);
            break;
        case STATE_CHARGING:
            writeNode("trigger", "none");
            writeNode("hwen", "1");
            writeNode("task0", "0 255 0");
            writeNode("brightness", "200");
            break;
        default:
            break;
    }
}

void Leds::applyUserEffectLocked() {
    LOG(INFO) << "Efek pengguna " << mEffect << (mUseCustomColors ? " (warna sendiri)" : "");
    writeNode("hwen", "0");
    writeNode("hwen", "1");
    if (mUseCustomColors) {
        writeNode("frq", std::to_string(mFrequency));
        for (size_t i = 0; i < kNumColors; i++) {
            writeNode("rgb", android::base::StringPrintf("%zx %x", i, mColors[i]));
        }
    }
    writeNode("effect", std::to_string(mEffect + 1));
    // cfg 1 = efek bawaan firmware, 2 = pakai warna & frekuensi sendiri
    writeNode("cfg", mUseCustomColors ? "2" : "1");
}

void Leds::turnOffLocked() {
    writeNode("brightness", "0");
    writeNode("hwen", "0");
}

}  // namespace aidl::vendor::lineage::leds
