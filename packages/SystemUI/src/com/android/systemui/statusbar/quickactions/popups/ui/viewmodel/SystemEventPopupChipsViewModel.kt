/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.quickactions.popups.ui.viewmodel

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.shared.CallType
import com.android.systemui.statusbar.quickactions.popups.data.SystemEventConnectivityManager
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings
import com.android.systemui.statusbar.quickactions.popups.shared.SystemEventFeature
import com.android.systemui.statusbar.quickactions.popups.shared.model.CallControls
import com.android.systemui.statusbar.quickactions.popups.shared.model.SystemEvent
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs its own session of the island event sources while the dynamic island is enabled and exposes
 * its system events as island chips, each behind its own [SystemEventFeature] switch.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SystemEventPopupChipsViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val connectivityProvider: Provider<SystemEventConnectivityManager>,
    activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val notifCollection: CommonNotifCollection,
    private val mapper: SystemEventPopupMapper,
) : HydratedActivatable() {

    private val callEvents: Flow<List<SystemEvent.Call>> =
        activeNotificationsInteractor.allRepresentativeNotifications.map { notifications ->
            notifications.values.mapNotNull { it.toIslandCall() }
        }

    val chips: List<QuickActionChipModel.PopupChip> by
        DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled(
                context,
                Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND,
                defaultValue = false,
            )
            .distinctUntilChanged()
            .flatMapLatest { enabled -> if (enabled) connectivityChips() else flowOf(emptyList()) }
            .hydratedStateOf(traceName = "chips", initialValue = emptyList())

    /** The events whose own switch is on, which changes as the switches are flipped. */
    private fun enabledFeatures(): Flow<Set<SystemEventFeature>> {
        val features = SystemEventFeature.values().toList()
        return combine(
                features.map { feature ->
                    DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled(
                        context,
                        feature.settingKey,
                    )
                }
            ) { enabled ->
                features.filterIndexed { index, _ -> enabled[index] }.toSet()
            }
            .distinctUntilChanged()
    }

    private fun connectivityChips(): Flow<List<QuickActionChipModel.PopupChip>> = channelFlow {
        val connectivity = connectivityProvider.get()
        // The source keeps the event for as long as the device stays connected, so each
        // connection is shown for a short while rather than for as long as it lasts.
        val bluetooth =
            BriefEvent<SystemEvent.Bluetooth>(
                scope = this,
                durationMs = BLUETOOTH_CHIP_MS,
                keyOf = { it.address },
                clear = connectivity::clearBluetooth,
            )
        val hotspot =
            BriefEvent<SystemEvent.Hotspot>(
                scope = this,
                durationMs = HOTSPOT_CHIP_MS,
                keyOf = { it.id },
                clear = connectivity::clearHotspot,
            )
        val vpn =
            BriefEvent<SystemEvent.Vpn>(
                scope = this,
                durationMs = VPN_CHIP_MS,
                keyOf = { it.id },
                clear = connectivity::clearVpn,
            )
        try {
            launch {
                enabledFeatures().collect { enabled ->
                    if (SystemEventFeature.BLUETOOTH in enabled) connectivity.startBluetooth()
                    else connectivity.stopBluetooth()
                    if (SystemEventFeature.HOTSPOT in enabled) connectivity.startHotspot()
                    else connectivity.stopHotspot()
                    if (SystemEventFeature.VPN in enabled) connectivity.startVpn()
                    else connectivity.stopVpn()
                }
            }
            combine(
                    connectivity.bluetoothEvent,
                    connectivity.hotspotEvent,
                    connectivity.vpnEvent,
                    callEvents,
                    enabledFeatures(),
                ) { bluetoothEvent, hotspotEvent, vpnEvent, calls, enabled ->
                    Events(
                        bluetoothEvent,
                        hotspotEvent,
                        vpnEvent,
                        calls.takeIf { SystemEventFeature.CALLS in enabled }.orEmpty(),
                    )
                }
                .collect { (bluetoothEvent, hotspotEvent, vpnEvent, calls) ->
                    bluetooth.onEvent(bluetoothEvent)
                    hotspot.onEvent(hotspotEvent)
                    vpn.onEvent(vpnEvent)
                    send(
                        listOfNotNull(
                            bluetoothEvent?.let {
                                mapper.toChip(it, connectivity::disconnectBluetooth)
                            },
                            hotspotEvent?.let { mapper.toChip(it) },
                            vpnEvent?.let { mapper.toChip(it) },
                        ) + calls.map { mapper.toChip(it) }
                    )
                }
        } finally {
            bluetooth.cancel()
            hotspot.cancel()
            vpn.cancel()
            connectivity.stopBluetooth()
            connectivity.stopHotspot()
            connectivity.stopVpn()
        }
    }

    private data class Events(
        val bluetooth: SystemEvent.Bluetooth?,
        val hotspot: SystemEvent.Hotspot?,
        val vpn: SystemEvent.Vpn?,
        val calls: List<SystemEvent.Call>,
    )

    private fun ActiveNotificationModel.toIslandCall(): SystemEvent.Call? {
        if (callType == CallType.None) return null
        val sbn = notifCollection.getEntry(key)?.sbn ?: return null
        val notification = sbn.notification
        val person =
            notification.extras.getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java)
        return SystemEvent.Call(
            id = sbn.key,
            callerName = person?.name,
            callerPhoto = person?.icon?.loadDrawable(context),
            connected = callType == CallType.Ongoing,
            callType =
                if (callType == CallType.Incoming) "Phone:incoming" else "Phone:ongoing",
            callStartTimeMs = notification.`when`,
            actions = notification.actions?.toList().orEmpty(),
            ownerPackage = sbn.packageName,
            controls = notification.toCallControls(),
        )
    }

    private fun Notification.toCallControls(): CallControls {
        fun pi(key: String) = extras.getParcelable(key, PendingIntent::class.java)
        val all = actions?.toList().orEmpty()
        val muteAction =
            all.firstOrNull {
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_MUTE ||
                    it.semanticAction == Notification.Action.SEMANTIC_ACTION_UNMUTE
            }
        val speakerAction =
            all.firstOrNull { it.title?.contains("speaker", ignoreCase = true) == true }
        return CallControls(
            answer = pi(Notification.EXTRA_ANSWER_INTENT),
            decline = pi(Notification.EXTRA_DECLINE_INTENT),
            hangUp = pi(Notification.EXTRA_HANG_UP_INTENT),
            speaker = speakerAction?.actionIntent,
            speakerOn = null,
            mute = muteAction?.actionIntent,
            muted =
                muteAction?.let { it.semanticAction == Notification.Action.SEMANTIC_ACTION_UNMUTE },
        )
    }

    /**
     * Clears an event [durationMs] after it first shows up, and again for every later event whose
     * [keyOf] differs, so a source that holds its event can still be shown briefly.
     */
    private class BriefEvent<T : Any>(
        private val scope: CoroutineScope,
        private val durationMs: Long,
        private val keyOf: (T) -> Any,
        private val clear: () -> Unit,
    ) {
        private var shownKey: Any? = null
        private var expiry: Job? = null

        fun onEvent(event: T?) {
            if (event == null) {
                shownKey = null
                expiry?.cancel()
                return
            }
            val key = keyOf(event)
            shownKey = key
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(durationMs)
                    clear()
                }
        }

        fun cancel() {
            expiry?.cancel()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): SystemEventPopupChipsViewModel
    }

    private companion object {
        const val BLUETOOTH_CHIP_MS = 8_000L
        const val HOTSPOT_CHIP_MS = 8_000L
        const val VPN_CHIP_MS = 8_000L
    }
}
