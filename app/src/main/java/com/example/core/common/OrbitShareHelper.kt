package com.example.core.common

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.IntentCompat
import com.example.core.datastore.BrowserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives Android's [Intent.EXTRA_CHOSEN_COMPONENT] callback from [Intent.createChooser]
 * and persists the last chosen app component & label for 1-tap Quick Share.
 */
class OrbitShareTargetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chosen = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_CHOSEN_COMPONENT,
            ComponentName::class.java
        ) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val label = OrbitShareHelper.resolveComponentAppLabel(context, chosen)
                BrowserPreferencesDataStore(context.applicationContext)
                    .setLastShareTarget(chosen.flattenToString(), label)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object OrbitShareHelper {

    fun shareWithChooser(context: Context, title: String?, text: String) {
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val callbackIntent = Intent(context, OrbitShareTargetReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 4021, callbackIntent, flags)
        val chooser = Intent.createChooser(baseIntent, "Share link", pendingIntent.intentSender).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    fun quickShareToLastApp(
        context: Context,
        title: String?,
        text: String,
        lastComponentFlatten: String
    ): Boolean {
        val comp = ComponentName.unflattenFromString(lastComponentFlatten)
        if (comp != null && isComponentAvailable(context, comp)) {
            val directIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
                component = comp
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val launched = runCatching {
                context.startActivity(directIntent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        shareWithChooser(context, title, text)
        return false
    }

    fun isComponentAvailable(context: Context, component: ComponentName): Boolean {
        val probeIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            this.component = component
        }
        return context.packageManager.resolveActivity(probeIntent, 0) != null
    }

    fun resolveComponentAppLabel(context: Context, component: ComponentName): String {
        val pm = context.packageManager
        return runCatching {
            val activityInfo = pm.getActivityInfo(component, 0)
            val appLabel = pm.getApplicationLabel(activityInfo.applicationInfo).toString().trim()
            val actLabel = activityInfo.loadLabel(pm).toString().trim()
            appLabel.ifBlank { actLabel }.ifBlank { component.packageName }
        }.getOrElse { component.packageName.substringAfterLast('.') }
    }

    fun resolveComponentIcon(context: Context, componentFlatten: String): Drawable? {
        val comp = ComponentName.unflattenFromString(componentFlatten) ?: return null
        return runCatching {
            val pm = context.packageManager
            pm.getActivityIcon(comp)
        }.getOrNull()
    }
}
