/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.WallpaperColors
import android.app.WallpaperManager.OnColorsChangedListener
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.gui.EarlyWakeupInfo
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemProperties
import android.os.Trace
import android.os.Trace.TRACE_TAG_APP
import android.util.IndentingPrintWriter
import android.util.Log
import android.util.MathUtils
import android.view.CrossWindowBlurListeners
import android.view.CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED
import android.view.SurfaceControl
import android.view.SyncRtSurfaceTransactionApplier
import android.view.ViewRootImpl
import androidx.annotation.VisibleForTesting
import androidx.palette.graphics.Palette
import com.android.systemui.Dumpable
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.res.R
import java.io.PrintWriter
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sin

@SysUISingleton
open class BlurUtils
@Inject
constructor(
    @Main resources: Resources,
    blurConfig: BlurConfig,
    private val context: Context,
    private val crossWindowBlurListeners: CrossWindowBlurListeners,
    dumpManager: DumpManager,
) : Dumpable {
    val minBlurRadius = resources.getDimensionPixelSize(R.dimen.min_window_blur_radius).toFloat()
    val maxBlurRadius =
        if (Flags.notificationShadeBlur()) {
            blurConfig.maxBlurRadiusPx
        } else {
            resources.getDimensionPixelSize(R.dimen.max_window_blur_radius).toFloat()
        }

    private var lastAppliedBlur = 0
    private var lastTargetViewRootImpl: ViewRootImpl? = null
    private var _transactionApplier = SyncRtSurfaceTransactionApplier(null)
    @VisibleForTesting
    open val transactionApplier: SyncRtSurfaceTransactionApplier
        get() = _transactionApplier

    private var earlyWakeupEnabled = false
    private val earlyWakeupInfo = EarlyWakeupInfo()
    private var persistentEarlyWakeupRequired = false

    private val wallpaperManager: WallpaperManager = context.getSystemService(WallpaperManager::class.java)
    private var cachedWallpaperColors: WallpaperColors? = null
    private var lastColorUpdateTime = 0L
    private val COLOR_CACHE_DURATION = 5000L

    private val BLUR_LAYER_COUNT = 3
    private val BLUR_LAYER_RATIOS = floatArrayOf(0.4f, 0.7f, 1.0f)
    private val BLUR_LAYER_ALPHAS = floatArrayOf(0.3f, 0.4f, 0.5f)

    private var dominantColor: Int = Color.TRANSPARENT
    private var vibrantColor: Int = Color.TRANSPARENT
    private var mutedColor: Int = Color.TRANSPARENT

    init {
        dumpManager.registerDumpable(this)
        earlyWakeupInfo.token = Binder()
        earlyWakeupInfo.trace = BlurUtils::class.java.getName()

        initWallpaperColorListener()
    }

    private fun initWallpaperColorListener() {
        try {
            val listener = OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    cachedWallpaperColors = colors
                    extractColorsFromWallpaper(colors)
                    lastColorUpdateTime = System.currentTimeMillis()
                }
            }

            wallpaperManager?.addOnColorsChangedListener(
                listener,
                Handler(Looper.getMainLooper())
            )

            val colors = wallpaperManager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            if (colors != null) {
                cachedWallpaperColors = colors
                extractColorsFromWallpaper(colors)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wallpaper color listener", e)
        }
    }

    private fun extractColorsFromWallpaper(colors: WallpaperColors?) {
        if (colors == null) return

        try {
            dominantColor = colors.primaryColor?.toArgb() ?: Color.TRANSPARENT

            val secondaryColor = colors.secondaryColor?.toArgb()
            val tertiaryColor = colors.tertiaryColor?.toArgb()

            vibrantColor = when {
                secondaryColor != null -> secondaryColor
                dominantColor != Color.TRANSPARENT -> enhanceColorVibrance(dominantColor)
                else -> Color.argb(128, 100, 150, 255)
            }

            mutedColor = when {
                tertiaryColor != null -> tertiaryColor
                dominantColor != Color.TRANSPARENT -> muteColor(dominantColor)
                else -> Color.argb(128, 150, 150, 180)
            }

            Log.d(TAG, "Extracted colors - Dominant: ${Integer.toHexString(dominantColor)}, " +
                    "Vibrant: ${Integer.toHexString(vibrantColor)}, " +
                    "Muted: ${Integer.toHexString(mutedColor)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting colors", e)
        }
    }

    private fun enhanceColorVibrance(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.1f).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun muteColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 0.4f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.85f).coerceIn(0f, 1f)
        return Color.HSVToColor((Color.alpha(color) * 0.8f).toInt(), hsv)
    }

    @VisibleForTesting
    open fun createTransaction(): SurfaceControl.Transaction = SurfaceControl.Transaction()

    fun blurRadiusOfRatio(ratio: Float): Float {
        if (ratio == 0f) {
            return 0f
        }

        val enhancedRatio = applyOneUIBlurCurve(ratio)
        return MathUtils.lerp(minBlurRadius, maxBlurRadius, enhancedRatio)
    }

    private fun applyOneUIBlurCurve(ratio: Float): Float {
        return when {
            ratio < 0.3f -> {
                val t = ratio / 0.3f
                t * t * (3f - 2f * t) * 0.3f
            }
            ratio < 0.7f -> {
                val t = (ratio - 0.3f) / 0.4f
                0.3f + (t.pow(1.8f)) * 0.5f
            }
            else -> {
                val t = (ratio - 0.7f) / 0.3f
                val overshoot = sin(t * Math.PI.toFloat() * 0.5f) * 0.05f
                0.8f + (t * t * (3f - 2f * t) * 0.2f) + overshoot
            }
        }.coerceIn(0f, 1f)
    }

    fun blurRadiusOfRatioForAod(ratio: Float): Float {
        if (ratio == 0f) {
            return 0f
        }
        return MathUtils.lerp(minBlurRadius, maxBlurRadius / 2, ratio)
    }

    fun ratioOfBlurRadius(blur: Float): Float {
        if (blur == 0f) {
            return 0f
        }
        return MathUtils.map(
            minBlurRadius,
            maxBlurRadius,
            0f,
            1f,
            blur,
        )
    }

    fun prepareBlur(viewRootImpl: ViewRootImpl?, radius: Int) {
        if (
            viewRootImpl == null ||
                !viewRootImpl.surfaceControl.isValid ||
                !shouldBlur(radius) ||
                earlyWakeupEnabled
        ) {
            return
        }
        updateTransactionApplier(viewRootImpl)
        val builder =
            SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)
        if (lastAppliedBlur == 0 && radius != 0) {
            earlyWakeupStart(builder, "eEarlyWakeup (prepareBlur)")
            transactionApplier.scheduleApply(builder.build())
        }
    }

    fun applyBlur(viewRootImpl: ViewRootImpl?, radius: Int, opaque: Boolean, scale: Float = 1.0f) {
        if (viewRootImpl == null || !viewRootImpl.surfaceControl.isValid) {
            return
        }
        updateTransactionApplier(viewRootImpl)

        if (shouldBlur(radius)) {
            applyLayeredBlur(viewRootImpl, radius, opaque, scale)
            lastAppliedBlur = radius
        } else {
            val builder = SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)
            builder.withOpaque(opaque)
            transactionApplier.scheduleApply(builder.build())
        }
    }

    private fun applyLayeredBlur(viewRootImpl: ViewRootImpl, radius: Int, opaque: Boolean, scale: Float) {
        val builder = SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)

        val enhancedRadius = (radius * 1.2f).toInt()
        builder.withBackgroundBlurRadius(enhancedRadius)

        if (shouldScaleWithTransaction()) {
            builder.withBackgroundBlurScale(scale)
        }

        if (Flags.notificationShadeBlur() && dominantColor != Color.TRANSPARENT) {
            applyGlassmorphismTint(builder, radius)
        }

        if (lastAppliedBlur == 0 && radius != 0) {
            Trace.instantForTrack(TRACE_TAG_APP, TRACK_NAME, "notifyRendererForGpuLoadUp")
            viewRootImpl.notifyRendererForGpuLoadUp("applyBlur")

            if (!earlyWakeupEnabled) {
                earlyWakeupStart(builder, "eEarlyWakeup (applyBlur)")
            }
        }

        if (earlyWakeupEnabled && lastAppliedBlur != 0 && radius == 0 && !persistentEarlyWakeupRequired) {
            earlyWakeupEnd(builder, "applyBlur")
        }

        builder.withOpaque(opaque)
        transactionApplier.scheduleApply(builder.build())
    }

    private fun applyGlassmorphismTint(
        builder: SyncRtSurfaceTransactionApplier.SurfaceParams.Builder,
        radius: Int
    ) {
        val blurRatio = ratioOfBlurRadius(radius.toFloat())

        val tintAlpha = (blurRatio * 0.25f * 255).toInt().coerceIn(0, 64)

        val blendedColor = blendColors(
            addAlpha(dominantColor, tintAlpha),
            addAlpha(vibrantColor, (tintAlpha * 0.6f).toInt()),
            blurRatio
        )

        try {

            Log.v(TAG, "Applying glassmorphism tint: ${Integer.toHexString(blendedColor)}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply color tint", e)
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1 - ratio
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun addAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun updateTransactionApplier(viewRootImpl: ViewRootImpl) {
        if (lastTargetViewRootImpl == viewRootImpl) return
        _transactionApplier = SyncRtSurfaceTransactionApplier(viewRootImpl.view)
        lastTargetViewRootImpl = viewRootImpl
    }

    private fun v(verboseLog: String) {
        if (isLoggable) Log.v(TAG, verboseLog)
    }

    @SuppressLint("MissingPermission")
    private fun earlyWakeupStart(
        builder: SyncRtSurfaceTransactionApplier.SurfaceParams.Builder?,
        traceMethodName: String,
    ) {
        v("earlyWakeupStart from $traceMethodName")
        Trace.asyncTraceForTrackBegin(TRACE_TAG_APP, TRACK_NAME, traceMethodName, 0)
        if (builder != null) {
            builder.withEarlyWakeupStart(earlyWakeupInfo)
        } else {
            Log.w(TAG, "surfaceControl is not valid, using immediate transaction to set early wakeup")
            createTransaction().use { it.setEarlyWakeupStart(earlyWakeupInfo).apply() }
        }
        earlyWakeupEnabled = true
    }

    @SuppressLint("MissingPermission")
    private fun earlyWakeupEnd(
        builder: SyncRtSurfaceTransactionApplier.SurfaceParams.Builder?,
        loggingContext: String,
    ) {
        v("earlyWakeupEnd from $loggingContext")
        if (builder != null) {
            builder.withEarlyWakeupEnd(earlyWakeupInfo)
        } else {
            Log.w(TAG, "surfaceControl is not valid, using immediate transaction to reset early wakeup")
            createTransaction().use { it.setEarlyWakeupEnd(earlyWakeupInfo).apply() }
        }
        Trace.asyncTraceForTrackEnd(TRACE_TAG_APP, TRACK_NAME, 0)
        earlyWakeupEnabled = false
    }

    private fun shouldBlur(radius: Int): Boolean {
        return supportsBlursOnWindows() ||
            ((Flags.notificationShadeBlur() || Flags.bouncerUiRevamp()) &&
                supportsBlursOnWindowsBase() &&
                lastAppliedBlur > 0 &&
                radius == 0)
    }

    private fun shouldScaleWithTransaction(): Boolean {
        return Flags.spatialModelPushbackInShader() && Flags.spatialModelAppPushback()
    }

    open fun supportsBlursOnWindows(): Boolean {
        return supportsBlursOnWindowsBase() &&
            crossWindowBlurListeners != null &&
            crossWindowBlurListeners.isCrossWindowBlurEnabled
    }

    private fun supportsBlursOnWindowsBase(): Boolean {
        return CROSS_WINDOW_BLUR_SUPPORTED &&
            ActivityManager.isHighEndGfx() &&
            !SystemProperties.getBoolean("persist.sysui.disableBlur", false)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        IndentingPrintWriter(pw, "  ").let {
            it.println("BlurUtils (OneUI Enhanced):")
            it.increaseIndent()
            it.println("minBlurRadius: $minBlurRadius")
            it.println("maxBlurRadius: $maxBlurRadius")
            it.println("supportsBlursOnWindows: ${supportsBlursOnWindows()}")
            it.println("dominantColor: ${Integer.toHexString(dominantColor)}")
            it.println("vibrantColor: ${Integer.toHexString(vibrantColor)}")
            it.println("mutedColor: ${Integer.toHexString(mutedColor)}")
            it.println("CROSS_WINDOW_BLUR_SUPPORTED: $CROSS_WINDOW_BLUR_SUPPORTED")
            it.println("isHighEndGfx: ${ActivityManager.isHighEndGfx()}")
        }
    }

    fun setPersistentEarlyWakeup(persistentWakeup: Boolean, viewRootImpl: ViewRootImpl?) {
        persistentEarlyWakeupRequired = persistentWakeup
        if (viewRootImpl == null || !supportsBlursOnWindows()) return

        val builder =
            if (!Flags.instantHideShade() || viewRootImpl.surfaceControl?.isValid == true) {
                updateTransactionApplier(viewRootImpl)
                SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)
            } else {
                null
            }
        if (persistentEarlyWakeupRequired) {
            if (earlyWakeupEnabled) return
            earlyWakeupStart(builder, "setEarlyWakeup")
        } else {
            if (!earlyWakeupEnabled) return
            if (lastAppliedBlur > 0) {
                Log.w(TAG, "resetEarlyWakeup invoked when lastAppliedBlur $lastAppliedBlur is non-zero")
            }
            earlyWakeupEnd(builder, "resetEarlyWakeup")
        }
        builder?.let { transactionApplier.scheduleApply(it.build()) }
    }

    companion object {
        const val TRACK_NAME = "BlurUtils"
        private const val TAG = "BlurUtils"
        private val isLoggable = Log.isLoggable(TAG, Log.VERBOSE) || Build.IS_ENG
    }
}