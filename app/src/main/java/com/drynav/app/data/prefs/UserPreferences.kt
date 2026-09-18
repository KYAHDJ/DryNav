package com.drynav.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

/** Lightweight on-device preferences. Dark mode is retained because the app already supports it. */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val TUTORIAL_SEEN_UIDS = stringPreferencesKey("tutorial_seen_uids")
    }

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


    val darkMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DARK_MODE] ?: false }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS] ?: true }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = true }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

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
