/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.KairosBuilder
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.State as KairosState
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.flatMap
import com.android.systemui.kairos.map
import com.android.systemui.kairos.stateOf
import com.android.systemui.kairosBuilder
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.connectivity.ui.MobileContextProvider
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.SignalIconLoader
import com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSim
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription
import com.android.systemui.statusbar.pipeline.mobile.ui.model.tryParseDualSim
import com.android.systemui.util.composable.kairos.hydratedComposeStateOf
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

@OptIn(ExperimentalKairosApi::class)
class StackedMobileIconViewModelKairos
@AssistedInject
constructor(
    mobileIcons: MobileIconsViewModelKairos,
    @ShadeDisplayAware private val context: Context,
    private val mobileContextProvider: MobileContextProvider,
) : KairosBuilder by kairosBuilder(), StackedMobileIconViewModel {

    private val signalIconLoader = SignalIconLoader(context)

    private val isStackable: Boolean by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.isStackable",
            mobileIcons.isStackable,
            initialValue = false,
        )

    private val iconList: KairosState<List<MobileIconViewModelKairos>> =
        combine(mobileIcons.icons, mobileIcons.activeSubscriptionId) { iconsBySubId, activeSubId ->
            buildList {
                activeSubId?.let { iconsBySubId[activeSubId]?.let { add(it) } }
                addAll(iconsBySubId.values.asSequence().filter { it.subscriptionId != activeSubId })
            }
        }

    override val useCustomOverlays: Boolean
        get() = signalIconLoader.hasOverlayIcons()

    override val dualSim: DualSim? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.dualSim",
            iconList.flatMap { icons ->
                icons
                    .map { vm -> vm.icon.map { vm.subscriptionId to it } } // Map subId to icon
                    .combine { signalIcons -> tryParseDualSim(signalIcons) }
            },
            initialValue = null,
        )

    override val contentDescription: String? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.contentDescription",
            iconList.flatMap { icons ->
                icons
                    .map { it.contentDescription }
                    .combine { contentDescriptions ->
                        tryParseContentDescriptions(contentDescriptions)
                    }
            },
            initialValue = null,
        )

    override val networkTypeIcon: Icon.Resource? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.networkTypeIcon",
            iconList.flatMap { icons -> icons.firstOrNull()?.networkTypeIcon ?: stateOf(null) },
            initialValue = null,
        )

    override val mobileContext: Context? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.mobileContext",
            iconList.map { icons ->
                icons.firstOrNull()?.let {
                    mobileContextProvider.getMobileContextForSub(it.subscriptionId, context)
                }
            },
            initialValue = null,
        )

    override val roaming: Boolean by
        hydratedComposeStateOf(
            name = "roaming",
            source = iconList.flatMap { icons -> icons.firstOrNull()?.roaming ?: stateOf(false) },
            initialValue = false,
        )

    override val isIconVisible: Boolean
        get() = isStackable && dualSim != null

    override val primaryIcon: SignalIconModel.Cellular? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.primaryIcon",
            iconList.flatMap { icons ->
                icons.firstOrNull()?.icon?.map { it as? SignalIconModel.Cellular } ?: stateOf(null)
            },
            initialValue = null,
        )

    override val secondaryIcon: SignalIconModel.Cellular? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.secondaryIcon",
            iconList.flatMap { icons ->
                icons.getOrNull(1)?.icon?.map { it as? SignalIconModel.Cellular } ?: stateOf(null)
            },
            initialValue = null,
        )

    override val primarySubId: Int? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.primarySubId",
            iconList.map { it.firstOrNull()?.subscriptionId },
            initialValue = null,
        )

    override val secondarySubId: Int? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.secondarySubId",
            iconList.map { it.getOrNull(1)?.subscriptionId },
            initialValue = null,
        )

    override val primaryNetworkTypeIcon: Icon.Resource? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.primaryNetworkTypeIcon",
            iconList.flatMap { icons -> icons.firstOrNull()?.networkTypeIcon ?: stateOf(null) },
            initialValue = null,
        )

    override val secondaryNetworkTypeIcon: Icon.Resource? by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.secondaryNetworkTypeIcon",
            iconList.flatMap { icons -> icons.getOrNull(1)?.networkTypeIcon ?: stateOf(null) },
            initialValue = null,
        )

    override val primaryRoaming: Boolean by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.primaryRoaming",
            iconList.flatMap { icons -> icons.firstOrNull()?.roaming ?: stateOf(false) },
            initialValue = false,
        )

    override val secondaryRoaming: Boolean by
        hydratedComposeStateOf(
            "StackedMobileIconViewModelKairos.secondaryRoaming",
            iconList.flatMap { icons -> icons.getOrNull(1)?.roaming ?: stateOf(false) },
            initialValue = false,
        )

    private fun tryParseContentDescriptions(
        contentDescriptions: List<MobileContentDescription?>
    ): String? {
        if (contentDescriptions.size != 2 || null in contentDescriptions) return null

        return contentDescriptions.joinToString(" ") { it?.loadContentDescription(context) ?: "" }
    }

    @AssistedFactory
    interface Factory {
        fun create(): StackedMobileIconViewModelKairos
    }
}
