/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/leds/BnLeds.h>

#include <array>
#include <mutex>
#include <string>
#include <vector>

namespace aidl::vendor::lineage::leds {

class Leds : public BnLeds {
  public:
    Leds();

    ndk::ScopedAStatus isEnabled(bool* _aidl_return) override;
    ndk::ScopedAStatus setEnabled(bool enabled) override;
    ndk::ScopedAStatus getCurrentEffect(int32_t* _aidl_return) override;
    ndk::ScopedAStatus setCurrentEffect(int32_t effect) override;
    ndk::ScopedAStatus getAvailableEffects(std::vector<std::string>* _aidl_return) override;
    ndk::ScopedAStatus getFrequency(int32_t* _aidl_return) override;
    ndk::ScopedAStatus setFrequency(int32_t frequency) override;
    ndk::ScopedAStatus getAllColors(std::vector<int32_t>* _aidl_return) override;
    ndk::ScopedAStatus setColor(int32_t index, int32_t color) override;
    ndk::ScopedAStatus flushConfig(bool useCustomColors) override;
    ndk::ScopedAStatus isStatusLightEnabled(bool* _aidl_return) override;
    ndk::ScopedAStatus setStatusLightEnabled(bool enabled) override;
    ndk::ScopedAStatus setSystemState(int32_t state) override;

  private:
    static constexpr size_t kNumColors = 9;

    void applyLocked();
    void applyStatusLocked();
    void applyUserEffectLocked();
    void turnOffLocked();

    std::mutex mLock;
    bool mEnabled = false;
    int32_t mEffect = 0;
    int32_t mFrequency = 0;
    std::array<int32_t, kNumColors> mColors{};
    bool mUseCustomColors = false;
    bool mStatusLight = true;
    int32_t mSystemState = STATE_OFF;
    int32_t mAppliedState = -1;
};

}  // namespace aidl::vendor::lineage::leds
