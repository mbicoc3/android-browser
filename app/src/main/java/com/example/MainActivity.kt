package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.app.OrbitNavHost
import com.example.core.ui.theme.OrbitBrowserTheme
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitWebViewPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val webViewPool by lazy {
        val container = (application as OrbitApplication).container
        OrbitWebViewPool(
            contentBlocker = container.filterEngine,
            passwordCoordinator = container.browserPasswordCoordinator
        )
    }
    private lateinit var browserViewModel: BrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as OrbitApplication).container
        browserViewModel = ViewModelProvider(
            this,
            appContainer.browserViewModelFactory()
        )[BrowserViewModel::class.java]

        handleIncomingIntent(intent)

        setContent {
            val navController = rememberNavController()
            val uiState by browserViewModel.uiState.collectAsStateWithLifecycle()
            val isIncognito = uiState.activeTab?.isIncognito == true

            LaunchedEffect(uiState.preferences.tabPreviewEnabled) {
                if (!uiState.preferences.tabPreviewEnabled) {
                    webViewPool.clearPreviews()
                }
            }

            OrbitBrowserTheme(
                themeMode = uiState.preferences.themeMode,
                isIncognito = isIncognito
            ) {
                OrbitNavHost(
                    navController = navController,
                    container = appContainer,
                    browserViewModel = browserViewModel,
                    tabsStateProvider = { uiState.tabs to uiState.activeTabId },
                    webViewPool = webViewPool
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val dataUrl = intent.dataString
            if (!dataUrl.isNullOrBlank()) {
                browserViewModel.openUrlInNewTab(dataUrl)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            (application as OrbitApplication).container.localVaultRepository.onAppBackgrounded()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            val appContainer = (application as OrbitApplication).container
            CoroutineScope(Dispatchers.IO).launch {
                val prefs = appContainer.preferencesDataStore.preferencesFlow.first()
                if (prefs.clearHistoryOnExit) {
                    appContainer.historyRepository.clearAll()
                }
            }
        }
        webViewPool.destroyAll()
        super.onDestroy()
    }
}
