package com.drynav.app.presentation.music

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin DI handle so any screen can reach the singleton [MusicManager] via
 * `hiltViewModel()`. Deliberately has no `onCleared` teardown: since screens
 * are inside a NavHost, `hiltViewModel()` there resolves to that screen's own
 * NavBackStackEntry store, not the Activity-wide one — an in-app navigation
 * that clears a backstack entry would otherwise tear down the *shared*
 * singleton [MusicManager] out from under every other screen. Actual
 * start/stop is owned solely by DryNavGraph's ON_START/ON_STOP.
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    val manager: MusicManager
) : ViewModel()
