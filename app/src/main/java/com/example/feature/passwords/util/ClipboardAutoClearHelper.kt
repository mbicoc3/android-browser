package com.example.feature.passwords.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies sensitive credentials/passwords to Android clipboard with Android 13+ EXTRA_IS_SENSITIVE
 * flag and schedules automatic clearing after the configured delay.
 */
class ClipboardAutoClearHelper(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var clearJob: Job? = null
    private var lastCopiedSecret: String? = null

    fun copySensitive(
        label: String,
        value: String,
        clearAfterSeconds: Int,
        onCountdownTick: (Int?) -> Unit
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, value).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)
        lastCopiedSecret = value

        clearJob?.cancel()
        if (clearAfterSeconds <= 0) {
            onCountdownTick(null)
            return
        }

        clearJob = scope.launch {
            for (remaining in clearAfterSeconds downTo 1) {
                onCountdownTick(remaining)
                delay(1_000L)
            }
            clearIfUnchanged(clipboard)
            onCountdownTick(null)
        }
    }

    fun clearNow(onCleared: () -> Unit) {
        clearJob?.cancel()
        clearJob = null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clearClip(clipboard)
        }
        lastCopiedSecret = null
        onCleared()
    }

    private fun clearIfUnchanged(clipboard: ClipboardManager) {
        val currentText = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
        if (currentText == null || currentText == lastCopiedSecret) {
            clearClip(clipboard)
        }
        lastCopiedSecret = null
    }

    private fun clearClip(clipboard: ClipboardManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
