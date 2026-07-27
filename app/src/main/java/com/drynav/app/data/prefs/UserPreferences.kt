package com.drynav.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drynav.app.data.search.PlaceResult
import com.mapbox.geojson.Point
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "drynav_prefs")

/** Small on-device settings: onboarding flag, dark mode, notifications. */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val MOOD = stringPreferencesKey("mood")
        val TUTORIAL_SEEN_UIDS = stringPreferencesKey("tutorial_seen_uids")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
    }

    // ------------------------------------------------------------------
    // Guided tour — once per account, not once per app open. A fresh
    // install or a cleared-data DataStore file has none of these ids, so
    // the tour plays again; an account whose id is already in here never
    // sees it again on that install. Stored as a set of ids (not a single
    // flag) so signing into a DIFFERENT/new account on the same install
    // still gets the tour once, even if some other account already saw it.
    // ------------------------------------------------------------------

    suspend fun hasSeenTutorial(userId: String): Boolean =
        context.dataStore.data.first()[Keys.TUTORIAL_SEEN_UIDS]
            ?.split(',')
            ?.contains(userId)
            ?: false

    suspend fun markTutorialSeen(userId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TUTORIAL_SEEN_UIDS]
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current.add(userId)
            prefs[Keys.TUTORIAL_SEEN_UIDS] = current.joinToString(",")
        }
    }

    val onboardingSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_SEEN] ?: false }

    /** Chosen map-puck character (Mood enum name), null until the user picks one. */
    val mood: Flow<String?> =
        context.dataStore.data.map { it[Keys.MOOD] }

    suspend fun setMood(mood: String?) {
        context.dataStore.edit {
            if (mood == null) it.remove(Keys.MOOD) else it[Keys.MOOD] = mood
        }
    }

    val darkMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DARK_MODE] ?: false }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS] ?: true }

    // Background music: [musicVolume] is the user's chosen "normal" level
    // (default 75%); MusicManager scales it down while navigating, it isn't
    // a separate stored value.
    val musicEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MUSIC_ENABLED] ?: true }

    val musicVolume: Flow<Float> =
        context.dataStore.data.map { it[Keys.MUSIC_VOLUME] ?: 0.75f }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MUSIC_ENABLED] = enabled }
    }

    suspend fun setMusicVolume(volume: Float) {
        context.dataStore.edit { it[Keys.MUSIC_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = true }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    // ------------------------------------------------------------------
    // Recent destination searches (JSON — DataStore has no list type)
    // ------------------------------------------------------------------

    suspend fun getRecentSearches(): List<PlaceResult> {
        val json = context.dataStore.data.first()[Keys.RECENT_SEARCHES] ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        PlaceResult(
                            id = obj.optString("id"),
                            name = obj.getString("name"),
                            address = obj.optString("address"),
                            point = Point.fromLngLat(obj.getDouble("lng"), obj.getDouble("lat"))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setRecentSearches(places: List<PlaceResult>) {
        val array = JSONArray()
        places.forEach { place ->
            val point = place.point ?: return@forEach
            array.put(
                JSONObject()
                    .put("id", place.id)
                    .put("name", place.name)
                    .put("address", place.address)
                    .put("lng", point.longitude())
                    .put("lat", point.latitude())
            )
        }
        context.dataStore.edit { it[Keys.RECENT_SEARCHES] = array.toString() }
    }
}
