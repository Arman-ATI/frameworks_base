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

class AODStyleSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    private var aodStyleView: View? = null
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        constraintLayout.findViewById<View?>(R.id.aod_ls)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        aodStyleView = LayoutInflater.from(context).inflate(
            R.layout.keyguard_aod_style,
            constraintLayout,
            false
        ).apply {
            id = R.id.aod_ls
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        constraintLayout.addView(aodStyleView)
    }
    
    override fun bindData(constraintLayout: ConstraintLayout) {
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            connect(
                R.id.aod_ls,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.aod_ls,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            val topMargin = (context.resources.getDimensionPixelSize(R.dimen.status_bar_height) * 1.25f).toInt()
            connect(
                R.id.aod_ls,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                topMargin
            )
            
            constrainHeight(R.id.aod_ls, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.aod_ls, ConstraintSet.MATCH_CONSTRAINT)
            
            setMargin(R.id.aod_ls, ConstraintSet.START, 
                context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start))
            setMargin(R.id.aod_ls, ConstraintSet.END, 
                context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start))
            
            setElevation(R.id.aod_ls, 2f)
            
            createUnifiedBarrierAndNotificationConstraints(constraintSet)
        }
    }
    
    private fun createUnifiedBarrierAndNotificationConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            val barrierViews = mutableListOf<Int>()
            val potentialViews = listOf(
                R.id.aod_ls,
                R.id.lockscreen_clock_view,
                R.id.clock_ls,
                R.id.keyguard_slice_view,
                R.id.keyguard_weather,
                R.id.default_weather_image,
                R.id.default_weather_text,
                R.id.keyguard_info_widgets,
                R.id.keyguard_widgets,
                R.id.peek_display_area_top
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
        aodStyleView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        aodStyleView = null
    }
}
