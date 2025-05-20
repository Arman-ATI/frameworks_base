/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
 */
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import javax.inject.Inject
import com.android.systemui.lockscreen.LockScreenWidgets

class KeyguardWidgetViewSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {

    private var widgetView: LockScreenWidgets? = null
    private val TAG = "KeyguardWidgetViewSection"

    private fun createWidgetView(): LockScreenWidgets? {
        Log.d(TAG, "Creating LockScreenWidgets view")
        return try {
            val layoutInflater = android.view.LayoutInflater.from(context)
            val view = layoutInflater.inflate(R.layout.keyguard_clock_widgets, null) as LockScreenWidgets
            
            view.apply {
                id = R.id.keyguard_widgets
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                )
                visibility = View.VISIBLE
            }
            
            Log.d(TAG, "Successfully inflated LockScreenWidgets")
            view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inflate LockScreenWidgets", e)
            null
        }
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "addViews called")
        
        val existingView = constraintLayout.findViewById<View?>(R.id.keyguard_widgets)
        
        if (existingView != null) {
            Log.d(TAG, "Found existing widget view")
            widgetView = existingView as? LockScreenWidgets
            return
        }
        
        if (widgetView != null) {
            Log.d(TAG, "Reusing existing widget view instance")
            (widgetView?.parent as? ViewGroup)?.removeView(widgetView)
        } else {
            Log.d(TAG, "Creating new widget view")
            widgetView = createWidgetView()
        }
        
        widgetView?.let { view ->
            try {
                constraintLayout.addView(view)
                Log.d(TAG, "Successfully added widget view")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add widget view", e)
            }
        }
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "bindData called")
        widgetView?.let { view ->
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                Log.d(TAG, "Set widget view visibility to VISIBLE")
            }
            view.requestLayout()
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        Log.d(TAG, "applyConstraints called")
        
        widgetView ?: run {
            Log.w(TAG, "Widget view is null, skipping constraints")
            return
        }
        
        try {
            constraintSet.apply {
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                
                val anchorViews = listOf(
                    R.id.keyguard_info_widgets,
                    R.id.keyguard_weather,
                    R.id.default_weather_image,
                    R.id.clock_ls,
                    R.id.lockscreen_clock_view,
                    R.id.aod_ls
                )
                
                var positioned = false
                for (anchorId in anchorViews) {
                    try {
                        constraintSet.getConstraint(anchorId)
                        connect(
                            R.id.keyguard_widgets,
                            ConstraintSet.TOP,
                            anchorId,
                            ConstraintSet.BOTTOM,
                            12
                        )
                        positioned = true
                        Log.d(TAG, "Positioned widgets below: ${context.resources.getResourceEntryName(anchorId)}")
                        break
                    } catch (e: Exception) {
                        continue
                    }
                }
                
                if (!positioned) {
                    val topMargin = (context.resources.getDimensionPixelSize(R.dimen.status_bar_height) * 1.5f).toInt()
                    connect(
                        R.id.keyguard_widgets,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                        topMargin
                    )
                    Log.d(TAG, "No anchor found, positioned at top")
                }
                
                constrainHeight(R.id.keyguard_widgets, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.keyguard_widgets, ConstraintSet.MATCH_CONSTRAINT)
                
                setMargin(R.id.keyguard_widgets, ConstraintSet.START, 0)
                setMargin(R.id.keyguard_widgets, ConstraintSet.END, 0)
                
                setElevation(R.id.keyguard_widgets, 2f)
                
                createUnifiedBarrierAndNotificationConstraints(constraintSet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply constraints", e)
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
                Log.d(TAG, "Created barrier with ${barrierViews.size} views")
            }
            
            try {
                constraintSet.getConstraint(R.id.left_aligned_notification_icon_container)
                try {
                    connect(
                        R.id.left_aligned_notification_icon_container,
                        ConstraintSet.TOP,
                        R.id.smart_space_barrier_bottom,
                        ConstraintSet.BOTTOM,
                        context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                    )
                } catch (e: Exception) {
                    connect(
                        R.id.left_aligned_notification_icon_container,
                        ConstraintSet.TOP,
                        R.id.smart_space_barrier_bottom,
                        ConstraintSet.BOTTOM,
                        8
                    )
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "removeViews called")
        widgetView?.let { view ->
            try {
                constraintLayout.removeView(view)
                Log.d(TAG, "Successfully removed widget view")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove widget view", e)
            }
        }
        widgetView = null
    }
}
