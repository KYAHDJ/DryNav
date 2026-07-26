package com.drynav.app.presentation.components

import androidx.lifecycle.ViewModel
import com.drynav.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Tiny helper so [DryNavBottomBar] can show the Admin tab without prop-drilling. */
@HiltViewModel
class BottomBarViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    val isAdmin: Boolean get() = authRepository.isAdmin
}
