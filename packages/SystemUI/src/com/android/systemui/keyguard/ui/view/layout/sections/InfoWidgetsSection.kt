/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
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
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import javax.inject.Inject

class InfoWidgetsSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    private var infoWidgetsView: View? = null
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        
        constraintLayout.findViewById<View?>(R.id.keyguard_info_widgets)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        infoWidgetsView = LayoutInflater.from(context).inflate(
            R.layout.keyguard_info_widgets,
            constraintLayout,
            false
        ).apply {
            id = R.id.keyguard_info_widgets
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        constraintLayout.addView(infoWidgetsView)
    }
    
    override fun bindData(constraintLayout: ConstraintLayout) {
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        
        constraintSet.apply {
            connect(R.id.keyguard_info_widgets, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(R.id.keyguard_info_widgets, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            
            when {
                constraintSet.getConstraint(R.id.lockscreen_clock_view) != null -> {
                    connect(R.id.keyguard_info_widgets, ConstraintSet.TOP, R.id.lockscreen_clock_view, ConstraintSet.BOTTOM, 12)
                }
                constraintSet.getConstraint(R.id.keyguard_weather) != null -> {
                    connect(R.id.keyguard_info_widgets, ConstraintSet.TOP, R.id.keyguard_weather, ConstraintSet.BOTTOM, 12)
                }
                constraintSet.getConstraint(R.id.default_weather_image) != null -> {
                    connect(R.id.keyguard_info_widgets, ConstraintSet.TOP, R.id.default_weather_image, ConstraintSet.BOTTOM, 12)
                }
                constraintSet.getConstraint(R.id.clock_ls) != null -> {
                    connect(R.id.keyguard_info_widgets, ConstraintSet.TOP, R.id.clock_ls, ConstraintSet.BOTTOM, 12)
                }
                constraintSet.getConstraint(R.id.keyguard_slice_view) != null -> {
                    connect(R.id.keyguard_info_widgets, ConstraintSet.TOP, R.id.keyguard_slice_view, ConstraintSet.BOTTOM, 12)
                }
                else -> {
                    val topMargin = (context.resources.getDimensionPixelSize(R.dimen.status_bar_height) * 1.5f).toInt()
                    connect(R.id.keyguard_info_widgets, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, topMargin)
                }
            }
            
            constrainHeight(R.id.keyguard_info_widgets, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.keyguard_info_widgets, ConstraintSet.MATCH_CONSTRAINT)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.START, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.END, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.BOTTOM, 6)
            setElevation(R.id.keyguard_info_widgets, 1f)
            
            createUnifiedBarrierAndNotificationConstraints(constraintSet)
        }
    }
    
    private fun createUnifiedBarrierAndNotificationConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            val barrierViews = mutableListOf<Int>()
            val potentialViews = listOf(
                R.id.lockscreen_clock_view,
                R.id.keyguard_slice_view,
                R.id.keyguard_weather,
                R.id.default_weather_image,
                R.id.default_weather_text,
                R.id.clock_ls,
                R.id.keyguard_info_widgets,
                R.id.keyguard_widgets
            )
            
            potentialViews.forEach { viewId ->
                try {
                    constraintSet.getConstraint(viewId)
                    barrierViews.add(viewId)
                } catch (e: Exception) {
                }
            }
            
            if (barrierViews.isNotEmpty()) {
                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    *barrierViews.toIntArray()
                )
            }
            
            if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
                try {
                    connect(
                        R.id.left_aligned_notification_icon_container,
                        ConstraintSet.TOP,
                        R.id.smart_space_barrier_bottom,
                        ConstraintSet.BOTTOM,
                        context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                    )
                } catch (e: Exception) {
                }
            }
        }
    }
    
    override fun removeViews(constraintLayout: ConstraintLayout) {
        infoWidgetsView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        infoWidgetsView = null
    }
}
