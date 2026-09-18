package com.drynav.app.presentation.tutorial

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin DI handle so any screen (a plain Composable) can reach the singleton
 * [TutorialManager] via `hiltViewModel()`. Every screen gets its own
 * [TutorialViewModel] instance (scoped to that nav destination, same as any
 * other screen ViewModel) but they all wrap the SAME underlying [manager],
 * so tour state stays correctly shared across screens.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    val manager: TutorialManager
) : ViewModel()
