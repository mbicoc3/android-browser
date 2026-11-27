package com.example.feature.browser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs
import kotlin.math.roundToInt

interface PullRefreshCallbacks {
    fun onPullProgress(fraction: Float)
    fun onPullTriggered()
    fun onPullCancelled()
}

object OrbitPullRefreshMath {
    /**
     * Converts raw downward drag distance into a damped fraction where 1.0f means ready to trigger.
     */
    fun computeDampedFraction(pullDeltaPx: Float, triggerThresholdPx: Float): Float {
        if (pullDeltaPx <= 0f || triggerThresholdPx <= 0f) return 0f
        val linear = pullDeltaPx / triggerThresholdPx
        return if (linear <= 1f) {
            linear
        } else {
            1f + ((linear - 1f) * 0.35f).coerceAtMost(0.45f)
        }
    }

    fun shouldTriggerRefresh(fraction: Float): Boolean = fraction >= 1f
}

/**
 * [WebView] subclass supporting native pull-down-to-refresh exclusively when scrolled to top
 * and enabled by the active web tab (never on the Start Page).
 */
class OrbitPullRefreshWebView(context: Context) : WebView(context) {

    var pullRefreshEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) {
                    resetPullState(notifyCancel = true)
                }
            }
        }

    var pullRefreshCallbacks: PullRefreshCallbacks? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val triggerThresholdPx = 112f * resources.displayMetrics.density

    private var downRawX = 0f
    private var downRawY = 0f
    private var isTrackingPull = false
    private var isPullActive = false
    private var currentFraction = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pullRefreshEnabled) {
            if (isPullActive || isTrackingPull) {
                resetPullState(notifyCancel = true)
            }
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                isTrackingPull = (scrollY == 0 && !canScrollVertically(-1))
                isPullActive = false
                currentFraction = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isPullActive || isTrackingPull) {
                    resetPullState(notifyCancel = true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    if (isPullActive || isTrackingPull) {
                        resetPullState(notifyCancel = true)
                    }
                    return super.onTouchEvent(event)
                }

                if (isTrackingPull) {
                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY

                    if (!isPullActive) {
                        if (scrollY > 0 || canScrollVertically(-1) ||
                            totalDy < -touchSlop ||
                            (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy))
                        ) {
                            isTrackingPull = false
                        } else if (totalDy > touchSlop && abs(totalDy) > abs(totalDx) * 1.25f) {
                            isPullActive = true
                            val cancelEvent = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.onTouchEvent(cancelEvent)
                            cancelEvent.recycle()

                            currentFraction = OrbitPullRefreshMath.computeDampedFraction(
                                pullDeltaPx = totalDy - touchSlop,
                                triggerThresholdPx = triggerThresholdPx
                            )
                            pullRefreshCallbacks?.onPullProgress(currentFraction)
                            return true
                        }
                    } else {
                        val pullPx = (totalDy - touchSlop).coerceAtLeast(0f)
                        currentFraction = OrbitPullRefreshMath.computeDampedFraction(
                            pullDeltaPx = pullPx,
                            triggerThresholdPx = triggerThresholdPx
                        )
                        pullRefreshCallbacks?.onPullProgress(currentFraction)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPullActive) {
                    val shouldTrigger = event.actionMasked == MotionEvent.ACTION_UP &&
                        OrbitPullRefreshMath.shouldTriggerRefresh(currentFraction)
                    isPullActive = false
                    isTrackingPull = false
                    currentFraction = 0f
                    if (shouldTrigger) {
                        pullRefreshCallbacks?.onPullTriggered()
                    } else {
                        pullRefreshCallbacks?.onPullCancelled()
                    }
                    return true
                }
                isTrackingPull = false
            }
        }

        return super.onTouchEvent(event)
    }

    private fun resetPullState(notifyCancel: Boolean) {
        val wasActive = isPullActive || currentFraction > 0f
        isTrackingPull = false
        isPullActive = false
        currentFraction = 0f
        if (notifyCancel && wasActive) {
            pullRefreshCallbacks?.onPullCancelled()
        }
    }
}

@Composable
fun OrbitWebPullRefreshOverlay(
    pullFraction: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val visible = isRefreshing || pullFraction > 0.04f
    val clampedProgress = pullFraction.coerceIn(0f, 1.45f)
    val offsetY = if (isRefreshing) 16.dp else (clampedProgress * 44f).dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .offset { IntOffset(0, offsetY.roundToPx()) }
                    .size(42.dp)
                    .testTag("web_pull_refresh_indicator")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        val ready = OrbitPullRefreshMath.shouldTriggerRefresh(clampedProgress)
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_reload),
                            tint = if (ready) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = (0.45f + clampedProgress * 0.55f).coerceIn(0.45f, 1f)
                                )
                            },
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(clampedProgress * 260f)
                        )
                    }
                }
            }
        }
    }
}
