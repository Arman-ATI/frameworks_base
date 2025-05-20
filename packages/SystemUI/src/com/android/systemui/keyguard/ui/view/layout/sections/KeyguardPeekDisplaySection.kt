/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
 */
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.notifications.ui.PeekDisplayView
import com.android.systemui.notifications.ui.PeekDisplayHolderLinearLayout
import com.android.systemui.res.R
import javax.inject.Inject

class KeyguardPeekDisplaySection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    companion object {
        private const val TAG = "KeyguardPeekDisplaySection"
        private const val PEEK_DISPLAY_LOCATION_TOP = 0
        private const val PEEK_DISPLAY_LOCATION_BOTTOM = 1
    }
    
    private var peekDisplayHolderTop: PeekDisplayHolderLinearLayout? = null
    private var peekDisplayTopView: PeekDisplayView? = null
    
    private var peekDisplayHolderBottom: PeekDisplayHolderLinearLayout? = null
    private var peekDisplayBottomView: PeekDisplayView? = null
    
    private var peekDisplayEnabled = false
    private var peekDisplayLocation = PEEK_DISPLAY_LOCATION_BOTTOM
    private var contentObserver: ContentObserver? = null
    private var constraintLayoutRef: ConstraintLayout? = null
    private val handler = Handler(context.mainLooper)
    
    private var screenStateReceiver: BroadcastReceiver? = null

    private fun registerScreenStateReceiver() {
        Log.d(TAG, "registerScreenStateReceiver called")
        
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen ON - triggering alignment fix")
                        triggerPeekDisplayToggle()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "User present - triggering alignment fix")
                        triggerPeekDisplayToggle()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        context.registerReceiver(screenStateReceiver, filter)
        Log.d(TAG, "Screen state receiver registered")
    }
    
    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
                screenStateReceiver = null
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
    }
    
    private fun triggerPeekDisplayToggle() {
        if (peekDisplayEnabled) {
            Log.d(TAG, "Peek display enabled, no toggle needed")
            return
        }
        
        Log.d(TAG, "Triggering full lifecycle toggle")
        
        constraintLayoutRef?.let { layout ->
            try {
                val originalEnabled = peekDisplayEnabled
                val originalLocation = peekDisplayLocation
                
                peekDisplayEnabled = true
                updatePeekDisplayState(layout)
                bindData(layout)
                
                val constraintSet = ConstraintSet()
                constraintSet.clone(layout)
                applyConstraints(constraintSet)
                constraintSet.applyTo(layout)
                
                layout.requestLayout()
                layout.invalidate()
                
                try {
                    peekDisplayEnabled = originalEnabled
                    peekDisplayLocation = originalLocation
                    
                    updatePeekDisplayState(layout)
                    
                    val disabledConstraintSet = ConstraintSet()
                    disabledConstraintSet.clone(layout)
                    applyConstraints(disabledConstraintSet)
                    disabledConstraintSet.applyTo(layout)
                    
                    layout.requestLayout()
                    layout.invalidate()
                    
                    Log.d(TAG, "Toggle completed")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during disable phase", e)
                    peekDisplayEnabled = originalEnabled
                    peekDisplayLocation = originalLocation
                    updatePeekDisplayVisibility()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during toggle", e)
                peekDisplayEnabled = Settings.Secure.getIntForUser(
                    context.contentResolver,
                    "peek_display_notifications", 0, UserHandle.USER_CURRENT
                ) == 1
                peekDisplayLocation = Settings.Secure.getIntForUser(
                    context.contentResolver,
                    "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT
                )
                updatePeekDisplayVisibility()
            }
        }
    }

    private fun registerContentObserver(constraintLayout: ConstraintLayout) {
        val handler = Handler(context.mainLooper)
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                updatePeekDisplayState(constraintLayout)
            }
        }
        val contentResolver: ContentResolver = context.contentResolver
        
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_notifications"),
            false,
            contentObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_location"),
            false,
            contentObserver!!
        )
    }
    
    private fun unregisterContentObserver() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }
    
    private fun updatePeekDisplayState(constraintLayout: ConstraintLayout) {
        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1
        
        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT
        )
        
        Log.d(TAG, "State updated - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")
        
        updatePeekDisplayVisibility()
        applyLocationConstraints(constraintLayout)
    }
    
    private fun updatePeekDisplayVisibility() {
        if (!peekDisplayEnabled) {
            peekDisplayHolderTop?.visibility = View.GONE
            peekDisplayHolderBottom?.visibility = View.GONE
            return
        }
        
        when (peekDisplayLocation) {
            PEEK_DISPLAY_LOCATION_TOP -> {
                peekDisplayHolderTop?.visibility = View.VISIBLE
                peekDisplayHolderBottom?.visibility = View.GONE
                peekDisplayTopView?.updatePeekDisplayState()
            }
            PEEK_DISPLAY_LOCATION_BOTTOM -> {
                peekDisplayHolderTop?.visibility = View.GONE
                peekDisplayHolderBottom?.visibility = View.VISIBLE
                peekDisplayBottomView?.updatePeekDisplayState()
            }
        }
    }

    private fun applyLocationConstraints(constraintLayout: ConstraintLayout) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        applyConstraints(constraintSet)
        constraintSet.applyTo(constraintLayout)
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "addViews called")

        constraintLayoutRef = constraintLayout

        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1

        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT
        )

        Log.d(TAG, "Initial - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")

        try {
            createTopPeekDisplay(constraintLayout)
            createBottomPeekDisplay(constraintLayout)
            
            updatePeekDisplayVisibility()
            registerContentObserver(constraintLayout)
            registerScreenStateReceiver()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in addViews", e)
        }
    }

    private fun createTopPeekDisplay(constraintLayout: ConstraintLayout) {
        constraintLayout.findViewById<View?>(R.id.peek_display_area_top)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        peekDisplayHolderTop = PeekDisplayHolderLinearLayout(context).apply {
            id = R.id.peek_display_area_top
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val peekDisplayView = PeekDisplayView(context).apply {
            id = R.id.peek_display_top
        }
        
        peekDisplayHolderTop?.addView(peekDisplayView)
        peekDisplayTopView = peekDisplayView
        
        constraintLayout.addView(peekDisplayHolderTop)
    }

    private fun createBottomPeekDisplay(constraintLayout: ConstraintLayout) {
        constraintLayout.findViewById<View?>(R.id.peek_display_area_bottom)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        peekDisplayHolderBottom = PeekDisplayHolderLinearLayout(context).apply {
            id = R.id.peek_display_area_bottom
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val peekDisplayView = PeekDisplayView(context).apply {
            id = R.id.peek_display_bottom
        }
        
        peekDisplayHolderBottom?.addView(peekDisplayView)
        peekDisplayBottomView = peekDisplayView
        
        constraintLayout.addView(peekDisplayHolderBottom)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        try {
            if (peekDisplayEnabled) {
                when (peekDisplayLocation) {
                    PEEK_DISPLAY_LOCATION_TOP -> peekDisplayTopView?.updatePeekDisplayState()
                    PEEK_DISPLAY_LOCATION_BOTTOM -> peekDisplayBottomView?.updatePeekDisplayState()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bindData", e)
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        try {
            applyTopPeekDisplayConstraints(constraintSet)
            applyBottomPeekDisplayConstraints(constraintSet)
            updateSmartSpaceBarrier(constraintSet)
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyConstraints", e)
        }
    }

    private fun applyTopPeekDisplayConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            connect(
                R.id.peek_display_area_top,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.peek_display_area_top,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            val anchorViews = listOf(
                R.id.keyguard_widgets,
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
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        anchorId,
                        ConstraintSet.BOTTOM,
                        12
                    )
                    positioned = true
                    Log.d(TAG, "Positioned peek top below: ${context.resources.getResourceEntryName(anchorId)}")
                    break
                } catch (e: Exception) {
                    continue
                }
            }
            
            if (!positioned) {
                val topMargin = (context.resources.getDimensionPixelSize(R.dimen.status_bar_height) * 1.5f).toInt()
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP,
                    topMargin
                )
                Log.d(TAG, "No anchor found for peek top, positioned at top")
            }
            
            constrainHeight(R.id.peek_display_area_top, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.peek_display_area_top, ConstraintSet.MATCH_CONSTRAINT)
            
            setMargin(R.id.peek_display_area_top, ConstraintSet.START, 0)
            setMargin(R.id.peek_display_area_top, ConstraintSet.END, 0)
            
            setElevation(R.id.peek_display_area_top, 3f)
        }
    }

    private fun applyBottomPeekDisplayConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            connect(
                R.id.peek_display_area_bottom,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.peek_display_area_bottom,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            if (constraintSet.getConstraint(R.id.keyguard_indication_area) != null) {
                connect(
                    R.id.peek_display_area_bottom,
                    ConstraintSet.BOTTOM,
                    R.id.keyguard_indication_area,
                    ConstraintSet.TOP,
                    16
                )
            } else if (constraintSet.getConstraint(R.id.start_button) != null) {
                connect(
                    R.id.peek_display_area_bottom,
                    ConstraintSet.BOTTOM,
                    R.id.start_button,
                    ConstraintSet.TOP,
                    16
                )
            } else {
                connect(
                    R.id.peek_display_area_bottom,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    64
                )
            }
            
            constrainHeight(R.id.peek_display_area_bottom, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.peek_display_area_bottom, ConstraintSet.MATCH_CONSTRAINT)
            
            setMargin(R.id.peek_display_area_bottom, ConstraintSet.START, 0)
            setMargin(R.id.peek_display_area_bottom, ConstraintSet.END, 0)
            
            setElevation(R.id.peek_display_area_bottom, 3f)
        }
    }

    private fun updateSmartSpaceBarrier(constraintSet: ConstraintSet) {
        val barrierIds = mutableListOf<Int>()
        
        val potentialViews = listOf(
            R.id.aod_ls,
            R.id.lockscreen_clock_view,
            R.id.clock_ls,
            R.id.keyguard_slice_view,
            R.id.keyguard_weather,
            R.id.default_weather_image,
            R.id.default_weather_text,
            R.id.keyguard_info_widgets,
            R.id.keyguard_widgets
        )
        
        potentialViews.forEach { viewId ->
            try {
                constraintSet.getConstraint(viewId)
                barrierIds.add(viewId)
            } catch (e: Exception) {
            }
        }
        
        if (peekDisplayLocation == PEEK_DISPLAY_LOCATION_TOP) {
            barrierIds.add(R.id.peek_display_area_top)
        }
        
        if (barrierIds.isNotEmpty()) {
            constraintSet.createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *barrierIds.toIntArray()
            )
        }
        
        if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
            try {
                constraintSet.connect(
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

    override fun removeViews(constraintLayout: ConstraintLayout) {
        unregisterContentObserver()
        unregisterScreenStateReceiver()
        
        constraintLayoutRef = null
        handler.removeCallbacksAndMessages(null)
        
        try {
            peekDisplayHolderTop?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
            
            peekDisplayHolderBottom?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
            
            peekDisplayHolderTop = null
            peekDisplayTopView = null
            peekDisplayHolderBottom = null
            peekDisplayBottomView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in removeViews", e)
        }
    }
}
