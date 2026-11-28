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
 * [WebView] subclass supporting native pull-down-to-refresh ONLY when Chromium itself
 * confirms the page (and any inner scrollable DOM containers) cannot scroll up further
 * via [overScrollBy], preventing pull-to-refresh from hijacking upward page scrolling.
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
    var onVisualContentInvalidated: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val triggerThresholdPx = 112f * density
    private val overscrollActivationThresholdPx = 6f * density
    private val maxGestureLeadBeforeOverscrollPx = 56f * density

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastMoveRawX = 0f
    private var lastMoveRawY = 0f
    private var pullOriginRawY = 0f

    private var isPointerDown = false
    private var canEnterPullThisGesture = false
    private var isPullActive = false
    private var sentCancelToSuper = false
    private var overscrollAccumPx = 0f
    private var currentFraction = 0f

    init {
        overScrollMode = OVER_SCROLL_ALWAYS
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t > 0 || t != oldt) {
            canEnterPullThisGesture = false
        }
        if (l != oldl || t != oldt) {
            onVisualContentInvalidated?.invoke()
        }
    }

    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        if (pullRefreshEnabled &&
            isPointerDown &&
            canEnterPullThisGesture &&
            !isPullActive &&
            isTouchEvent &&
            scrollY <= 0 &&
            deltaY < 0
        ) {
            val totalDx = lastMoveRawX - downRawX
            val totalDy = lastMoveRawY - downRawY
            if (totalDy > 0f && abs(totalDy) > abs(totalDx) * 1.2f) {
                overscrollAccumPx += (-deltaY).toFloat()
                if (overscrollAccumPx >= overscrollActivationThresholdPx) {
                    isPullActive = true
                    pullOriginRawY = lastMoveRawY
                    currentFraction = 0f
                    pullRefreshCallbacks?.onPullProgress(0f)
                }
            }
        }
        return super.overScrollBy(
            deltaX,
            deltaY,
            scrollX,
            scrollY,
            scrollRangeX,
            scrollRangeY,
            maxOverScrollX,
            maxOverScrollY,
            isTouchEvent
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pullRefreshEnabled) {
            if (isPullActive || isPointerDown) {
                isPointerDown = false
                canEnterPullThisGesture = false
                resetPullState(notifyCancel = true)
            }
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastMoveRawX = event.rawX
                lastMoveRawY = event.rawY
                isPointerDown = true
                canEnterPullThisGesture = (scrollY == 0 && !canScrollVertically(-1))
                isPullActive = false
                sentCancelToSuper = false
                overscrollAccumPx = 0f
                currentFraction = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                canEnterPullThisGesture = false
                if (isPullActive) {
                    resetPullState(notifyCancel = true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    canEnterPullThisGesture = false
                    if (isPullActive) {
                        resetPullState(notifyCancel = true)
                    }
                    return super.onTouchEvent(event)
                }

                lastMoveRawX = event.rawX
                lastMoveRawY = event.rawY

                if (isPullActive) {
                    if (!sentCancelToSuper) {
                        sendCancelToSuper(event)
                    }
                    val pullPx = event.rawY - pullOriginRawY
                    if (pullPx <= 0f) {
                        resetPullState(notifyCancel = true)
                        canEnterPullThisGesture = false
                        return super.onTouchEvent(event)
                    }
                    currentFraction = OrbitPullRefreshMath.computeDampedFraction(
                        pullDeltaPx = pullPx,
                        triggerThresholdPx = triggerThresholdPx
                    )
                    pullRefreshCallbacks?.onPullProgress(currentFraction)
                    return true
                }

                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY
                if (totalDy < -touchSlop ||
                    (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)) ||
                    (canEnterPullThisGesture && totalDy > maxGestureLeadBeforeOverscrollPx)
                ) {
                    canEnterPullThisGesture = false
                }

                val handled = super.onTouchEvent(event)
                if (isPullActive && !sentCancelToSuper) {
                    sendCancelToSuper(event)
                }
                return handled
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPointerDown = false
                canEnterPullThisGesture = false
                if (isPullActive) {
                    val shouldTrigger = event.actionMasked == MotionEvent.ACTION_UP &&
                        OrbitPullRefreshMath.shouldTriggerRefresh(currentFraction)
                    isPullActive = false
                    sentCancelToSuper = false
                    overscrollAccumPx = 0f
                    currentFraction = 0f
                    if (shouldTrigger) {
                        pullRefreshCallbacks?.onPullTriggered()
                    } else {
                        pullRefreshCallbacks?.onPullCancelled()
                    }
                    return true
                }
                overscrollAccumPx = 0f
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onVisualContentInvalidated?.invoke()
                }
            }
        }

        return super.onTouchEvent(event)
    }

    private fun sendCancelToSuper(source: MotionEvent) {
        sentCancelToSuper = true
        val cancelEvent = MotionEvent.obtain(source).apply {
            action = MotionEvent.ACTION_CANCEL
        }
        super.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun resetPullState(notifyCancel: Boolean) {
        val wasActive = isPullActive || currentFraction > 0f
        isPullActive = false
        sentCancelToSuper = false
        overscrollAccumPx = 0f
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
