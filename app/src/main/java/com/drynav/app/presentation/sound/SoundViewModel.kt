package com.drynav.app.presentation.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin DI handle so any screen can reach the singleton [SoundManager] via `hiltViewModel()`. */
@HiltViewModel
class SoundViewModel @Inject constructor(
    val manager: SoundManager
) : ViewModel()

/** A ready-to-use `() -> Unit` that plays a randomized click sound — drop into any `onClick`. */
@Composable
fun rememberClickSound(): () -> Unit {
    val manager = hiltViewModel<SoundViewModel>().manager
    return remember(manager) { { manager.playClick() } }
}
