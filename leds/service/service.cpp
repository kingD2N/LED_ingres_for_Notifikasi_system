/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "leds-ingres"

#include "Leds.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using aidl::vendor::lineage::leds::Leds;

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    std::shared_ptr<Leds> leds = ndk::SharedRefBase::make<Leds>();

    const std::string instance = std::string(Leds::descriptor) + "/default";
    binder_status_t status = AServiceManager_addService(leds->asBinder().get(), instance.c_str());
    CHECK_EQ(status, STATUS_OK) << "Gagal mendaftarkan " << instance;

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // tidak seharusnya sampai sini
}
