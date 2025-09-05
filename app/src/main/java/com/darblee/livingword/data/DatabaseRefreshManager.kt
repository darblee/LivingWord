package com.darblee.livingword.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global manager to handle database refresh notifications across the app.
 * This allows ViewModels to refresh their data when the database is imported.
 */
object DatabaseRefreshManager {
    private val _refreshTrigger = MutableSharedFlow<Unit>()
    val refreshTrigger = _refreshTrigger.asSharedFlow()
    
    /**
     * Triggers a refresh event that ViewModels can observe.
     */
    suspend fun triggerRefresh() {
        _refreshTrigger.emit(Unit)
        android.util.Log.d("DatabaseRefreshManager", "Refresh triggered for all ViewModels")
    }
}