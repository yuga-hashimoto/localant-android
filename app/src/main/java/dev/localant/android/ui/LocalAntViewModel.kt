package dev.localant.android.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.localant.android.accessibility.AccessibilityServiceStatus
import dev.localant.android.accessibility.LocalAntAccessibilityService
import dev.localant.android.approval.PendingApproval
import dev.localant.android.runtime.LocalAntAppServices
import dev.localant.android.service.HostStateStore
import dev.localant.android.service.LocalAntHostService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalAntViewModel(application: Application) : AndroidViewModel(application) {
    private val services = LocalAntAppServices.get(application)
    val hostState = HostStateStore.shared.state

    private val mutableApprovals = MutableStateFlow<List<PendingApproval>>(emptyList())
    val approvals: StateFlow<List<PendingApproval>> = mutableApprovals.asStateFlow()

    private val mutableAccessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = mutableAccessibilityConnected.asStateFlow()

    private val mutableOverlayPermissionGranted = MutableStateFlow(false)
    val overlayPermissionGranted: StateFlow<Boolean> = mutableOverlayPermissionGranted.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshNow()
                delay(APPROVAL_POLL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        mutableAccessibilityConnected.value =
            LocalAntAccessibilityService.current() != null ||
                AccessibilityServiceStatus.isEnabled(getApplication())
        mutableOverlayPermissionGranted.value = Settings.canDrawOverlays(getApplication())
        mutableApprovals.value = withContext(Dispatchers.IO) {
            services.approvals.expireStale(Long.MAX_VALUE)
            emptyList()
        }
    }

    fun startHosting() {
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), LocalAntHostService::class.java)
                .setAction(LocalAntHostService.ACTION_START),
        )
    }

    fun stopHosting() {
        getApplication<Application>().startService(
            Intent(getApplication(), LocalAntHostService::class.java)
                .setAction(LocalAntHostService.ACTION_STOP),
        )
    }

    fun approve(id: String, forSession: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                services.approvals.approve(id, sessionGrant = forSession)
            }
            refresh()
        }
    }

    fun deny(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { services.approvals.deny(id) }
            refresh()
        }
    }

    fun rotateToken() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { services.tokenStore.rotate() }
            stopHosting()
        }
    }

    private companion object {
        const val APPROVAL_POLL_MS = 1_000L
    }
}
