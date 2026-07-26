package com.drynav.app.data.presence

import com.drynav.app.data.location.LocationProvider
import com.drynav.app.data.prefs.UserPreferences
import com.drynav.app.domain.model.Mood
import com.drynav.app.domain.model.Presence
import com.drynav.app.domain.repository.PresenceRepository
import com.mapbox.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide (not screen-scoped) live presence: broadcasts the signed-in
 * user's own position + [Mood] and streams every other online user's
 * presence, for as long as the app is actually in the foreground — not
 * tied to any one screen, so you show up (and see others) the moment you
 * open the app on any tab, not just once you drill into the map. [start]/
 * [stop] are called from [com.drynav.app.presentation.navigation.DryNavGraph]
 * on the Activity's ON_START/ON_STOP so this never runs while the app is
 * closed/backgrounded.
 */
@Singleton
class PresenceManager @Inject constructor(
    private val presenceRepository: PresenceRepository,
    private val locationProvider: LocationProvider,
    private val prefs: UserPreferences
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _otherPresences = MutableStateFlow<List<Presence>>(emptyList())
    val otherPresences: StateFlow<List<Presence>> = _otherPresences.asStateFlow()

    @Volatile private var currentLocation: Point? = null

    private var locationJob: Job? = null
    private var othersJob: Job? = null
    private var heartbeatJob: Job? = null

    fun start() {
        if (heartbeatJob?.isActive == true) return

        locationJob = scope.launch {
            locationProvider.getCurrentLocation()?.let {
                currentLocation = Point.fromLngLat(it.longitude, it.latitude)
            }
            locationProvider.locationUpdates().collect {
                currentLocation = Point.fromLngLat(it.longitude, it.latitude)
            }
        }
        othersJob = scope.launch {
            presenceRepository.observeOthers().collect { others ->
                _otherPresences.value = others
            }
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                val point = currentLocation
                if (point != null) {
                    val mood = Mood.fromName(prefs.mood.first()) ?: Mood.HAPPY
                    presenceRepository.publishPresence(mood.name, point)
                }
                delay(HEARTBEAT_MS)
            }
        }
    }

    fun stop() {
        locationJob?.cancel()
        othersJob?.cancel()
        heartbeatJob?.cancel()
        locationJob = null
        othersJob = null
        heartbeatJob = null
        currentLocation = null
        _otherPresences.value = emptyList()
        scope.launch { presenceRepository.clearPresence() }
    }

    private companion object {
        const val HEARTBEAT_MS = 10_000L
    }
}
