package com.drynav.app.domain.model

import com.drynav.app.R

/**
 * A selectable map-puck character, shown for the signed-in user (their own
 * puck) and broadcast via presence so other online users see it too — the
 * Waze-style "mood" concept.
 */
enum class Mood(val label: String, val iconRes: Int) {
    HAPPY("Happy", R.drawable.mood_happy),
    ANGRY("Angry", R.drawable.mood_angry),
    SAD("Sad", R.drawable.mood_sad),
    DINO("Dino", R.drawable.mood_dino);

    companion object {
        fun fromName(name: String?): Mood? = entries.firstOrNull { it.name == name }
    }
}
