package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.core.di.AppContainer
import com.example.core.network.NetworkClientFactory
import com.example.core.telemetry.OrbitCrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OrbitApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        OrbitCrashReporter.install(this)
        container = AppContainer(this)
        container.schedulePeriodicMaintenance()
        appScope.launch {
            container.preferencesDataStore.preferencesFlow.collectLatest { prefs ->
                NetworkClientFactory.setDohProvider(prefs.dohProvider)
            }
        }
        appScope.launch {
            container.shieldRepository.ensureInitializedAndCompile()
            container.shieldRepository.schedulePeriodicSync()
        }
    }
}
