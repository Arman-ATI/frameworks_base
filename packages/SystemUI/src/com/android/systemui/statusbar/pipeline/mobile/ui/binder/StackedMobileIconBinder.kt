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

package com.android.systemui.statusbar.pipeline.mobile.ui.binder

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.binder.IconViewBinder
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.pipeline.mobile.ui.SignalIconLoader
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelImpl
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelKairos
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewBinding
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StackedMobileIcon
import com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView
import com.android.systemui.util.composable.kairos.rememberKairosActivatable

object StackedMobileIconBinder {
    @OptIn(ExperimentalKairosApi::class)
    fun bind(
        view: SingleBindableStatusBarComposeIconView,
        mobileIconsViewModel: MobileIconsViewModel,
        viewModelFactory: StackedMobileIconViewModelImpl.Factory,
        kairosViewModelFactory: StackedMobileIconViewModelKairos.Factory,
        kairosNetwork: KairosNetwork,
    ): ModernStatusBarViewBinding {
        return SingleBindableStatusBarComposeIconView.withDefaultBinding(
            view = view,
            shouldBeVisible = { mobileIconsViewModel.isStackable.value },
        ) { _, tintFlow ->
            view.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    view.composeView.apply {
                        setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                        )
                        setContent {
                            val viewModel: StackedMobileIconViewModel =
                                if (Flags.statusBarMobileIconKairos()) {
                                    rememberKairosActivatable(
                                        "StackedMobileIconBinder",
                                        kairosNetwork,
                                    ) {
                                        kairosViewModelFactory.create()
                                    }
                                } else {
                                    rememberViewModel("StackedMobileIconBinder") {
                                        viewModelFactory.create()
                                    }
                                }
                            val tint by tintFlow.collectAsStateWithLifecycle()
                            if (viewModel.isIconVisible) {
                                CompositionLocalProvider(LocalContentColor provides Color(tint)) {
                                    if (viewModel.useCustomOverlays) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.onSizeChanged { view.requestLayout() }
                                        ) {
                                            // Secondary icon (SIM 2)
                                            viewModel.secondaryIcon?.let { icon ->
                                                val loader = SignalIconLoader(view.context)
                                                val drawable = loader.loadSignalIcon(
                                                    icon.level,
                                                    icon.numberOfLevels
                                                )
                                                drawable?.let {
                                                    DrawableIcon(
                                                        drawable = it,
                                                        tint = tint,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }

                                            // Primary icon (SIM 1)
                                            viewModel.primaryIcon?.let { icon ->
                                                val loader = SignalIconLoader(view.context)
                                                val drawable = loader.loadSignalIcon(
                                                    icon.level,
                                                    icon.numberOfLevels
                                                )
                                                drawable?.let {
                                                    DrawableIcon(
                                                        drawable = it,
                                                        tint = tint,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }

                                            // Network type icon
                                            viewModel.primaryNetworkTypeIcon?.let { networkIcon ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val networkImageView = ImageView(view.context)
                                                    IconViewBinder.bind(networkIcon, networkImageView)
                                                    networkImageView.drawable?.let { drawable ->
                                                        DrawableIcon(
                                                            drawable = drawable,
                                                            tint = tint,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }

                                                    // Roaming indicator
                                                    if (viewModel.primaryRoaming) {
                                                        Text(
                                                            text = "R",
                                                            fontSize = 6.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(tint)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        StackedMobileIcon(
                                            viewModel,
                                            modifier = Modifier.onSizeChanged { view.requestLayout() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DrawableIcon(
        drawable: Drawable,
        tint: Int,
        modifier: Modifier = Modifier
    ) {
        drawable.setTint(tint)

        val bitmap = drawable.toBitmap()
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    }
}
