/*
 * Copyright (C) 2026 RisingOS (revived) Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.quickactions.popups.shared.model

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Drawable

/** CallStyle buttons; a null intent means the calling app doesn't offer it. */
data class CallControls(
    val answer: PendingIntent? = null,
    val decline: PendingIntent? = null,
    val hangUp: PendingIntent? = null,
    val speaker: PendingIntent? = null,
    val speakerOn: Boolean? = null,
    val mute: PendingIntent? = null,
    val muted: Boolean? = null,
)

/** Source data owned by the quick actions dynamic island. */
sealed interface SystemEvent {
    val id: String

    data class Bluetooth(
        val deviceName: String,
        val batteryLevel: Int = -1,
        val address: String = "",
        val deviceIcon: Drawable? = null,
        val deviceTypeLabel: String = "",
        val deviceImage: Drawable? = null,
        val leftBatteryLevel: Int? = null,
        val rightBatteryLevel: Int? = null,
        val caseBatteryLevel: Int? = null,
        val leftCharging: Boolean = false,
        val rightCharging: Boolean = false,
        val caseCharging: Boolean = false,
    ) : SystemEvent { override val id = "bluetooth" }

    data class Hotspot(val numDevices: Int) : SystemEvent { override val id = "hotspot" }

    data class Vpn(val isBranded: Boolean = false, val isValidated: Boolean = false) : SystemEvent {
        override val id = "vpn"
    }

    data class Call(
        override val id: String,
        val callerName: CharSequence?,
        val callerPhoto: Drawable?,
        val connected: Boolean,
        val callType: String,
        val callStartTimeMs: Long,
        val actions: List<Notification.Action>,
        val ownerPackage: String,
        val controls: CallControls = CallControls(),
    ) : SystemEvent
}
