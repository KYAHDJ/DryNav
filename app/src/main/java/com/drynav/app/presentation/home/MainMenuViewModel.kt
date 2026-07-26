package com.drynav.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.data.location.LocationProvider
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.repository.FloodRepository
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainMenuUiState(
    val blockedCount: Int = 0,
    val slowCount: Int = 0,
    val loaded: Boolean = false,
    /** The signed-in user's real display name — never mock data. */
    val displayName: String = "",
    /** Drives the once-per-account guided-tour check — see TutorialManager. */
    val userId: String = ""
)

/** ~5 km — "nearby" for a flood-alert summary, not a citywide count. */
private const val NEARBY_RADIUS_METERS = 5000.0

/** Feeds the live "N flooded roads nearby" summary card on the main menu. */
@HiltViewModel
class MainMenuViewModel @Inject constructor(
    floodRepository: FloodRepository,
    authRepository: AuthRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainMenuUiState(
            displayName = authRepository.currentUser?.displayName.orEmpty(),
            userId = authRepository.currentUser?.uid.orEmpty()
        )
    )
    val uiState: StateFlow<MainMenuUiState> = _uiState.asStateFlow()

    // Null until the first GPS fix lands — floods aren't filtered by distance
    // until then (showing every live flood briefly is better than showing
    // none while waiting for a fix).
    private val userLocation = MutableStateFlow<Point?>(null)

    init {
        fetchLocation()
        viewModelScope.launch {
            combine(floodRepository.getLiveFloodReports(), userLocation) { reports, origin ->
                reports.filter { it.isApproved && it.isLiveHazard } to origin
            }
                .catch { /* keep showing the last known counts on a transient error */ }
                .collect { (approved, origin) ->
                    val nearby = origin?.let { here ->
                        approved.filter { it.distanceMetersFrom(here) <= NEARBY_RADIUS_METERS }
                    } ?: approved
                    _uiState.update {
                        it.copy(
                            blockedCount = nearby.count { r -> r.isActiveHazard },
                            slowCount = nearby.count { r -> !r.isActiveHazard },
                            loaded = true
                        )
                    }
                }
        }
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            locationProvider.getCurrentLocation()?.let {
                userLocation.value = Point.fromLngLat(it.longitude, it.latitude)
            }
        }
    }

    private fun FloodReport.distanceMetersFrom(origin: Point): Double =
        TurfMeasurement.distance(
            origin, Point.fromLngLat(longitude, latitude), TurfConstants.UNIT_METERS
        )
}
