package com.drynav.app.data.presence

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin DI handle so [DryNavGraph][com.drynav.app.presentation.navigation.DryNavGraph]
 * (a plain Composable, not a nav destination) can reach the singleton
 * [PresenceManager] via `hiltViewModel()` and drive it from the Activity's
 * ON_START/ON_STOP lifecycle events.
 */
@HiltViewModel
class PresenceLifecycleViewModel @Inject constructor(
    val manager: PresenceManager
) : ViewModel() {
    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}
