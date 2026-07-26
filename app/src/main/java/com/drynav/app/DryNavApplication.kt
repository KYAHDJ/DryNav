package com.drynav.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DryNavApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Sign the device in anonymously so Firestore security rules can
        // require request.auth != null for report submissions & upvotes.
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }
}
