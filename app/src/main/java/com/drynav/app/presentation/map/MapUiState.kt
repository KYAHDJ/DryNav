package com.drynav.app.presentation.map

import com.drynav.app.data.search.PlaceResult
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.Mood
import com.drynav.app.domain.model.Presence
import com.drynav.app.domain.model.SavedPlace
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.navigation.base.route.NavigationRoute

/** Which base map the user picked in the layers sheet. */
enum class MapType {
    DEFAULT, SATELLITE, TERRAIN;

    fun styleUri(trafficEnabled: Boolean): String = when (this) {
        DEFAULT -> if (trafficEnabled) Style.TRAFFIC_NIGHT else Style.DARK
        SATELLITE -> Style.SATELLITE_STREETS
        TERRAIN -> Style.OUTDOORS
    }
}

/** How the user is travelling — drives the Directions profile and the ETA. */
enum class TravelMode(val label: String, val profile: String) {
    CAR("Car", DirectionsCriteria.PROFILE_DRIVING_TRAFFIC),
    MOTOR("Motor", DirectionsCriteria.PROFILE_DRIVING),
    WALK("Walk", DirectionsCriteria.PROFILE_WALKING)
}

data class MapUiState(
    val userLocation: Point? = null,
    val floodReports: List<FloodReport> = emptyList(),
    /** False until the first live flood snapshot arrives — gates the loading overlay. */
    val floodsLoaded: Boolean = false,
    val routes: List<NavigationRoute> = emptyList(),
    val destination: Point? = null,
    val destinationName: String? = null,
    val isNavigating: Boolean = false,
    val isCalculatingRoute: Boolean = false,
    val isSubmittingReport: Boolean = false,
    val showReportSheet: Boolean = false,
    val currentInstruction: String? = null,
    val maneuverModifier: String? = null,
    val remainingDistanceMeters: Double = 0.0,
    val remainingDurationSeconds: Double = 0.0,
    val routeDistanceMeters: Double = 0.0,
    val isRerouting: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    val mapType: MapType = MapType.DEFAULT,
    val trafficEnabled: Boolean = false,
    val floodOverlayEnabled: Boolean = true,
    val travelMode: TravelMode = TravelMode.CAR,
    val pendingPin: Point? = null,
    val recentSearches: List<PlaceResult> = emptyList(),
    val isAdmin: Boolean = false,
    val snackbarMessage: String? = null,
    /** True when the computed route crosses a blocked flood — pauses before navigating to ask. */
    val showFloodCrossingModal: Boolean = false,
    /** True while the on-demand denser search (triggered by "Find Alternative") is running. */
    val isFindingAlternative: Boolean = false,
    /** True when the denser search also failed to find a clean route. */
    val noAlternativeFound: Boolean = false,
    /** False when the user has manually panned away from the follow camera. */
    val isFollowingCamera: Boolean = true,
    val isVoiceMuted: Boolean = false,
    /** The signed-in user's chosen map-puck character, picked in Profile. Null = default arrow puck. */
    val mood: Mood? = null,
    /** Other online users' live position + mood, for the Waze-style "other drivers" layer. */
    val otherPresences: List<Presence> = emptyList(),
    /** Non-null while the brief "N people nearby" toast is showing; null hides it. */
    val nearbyPeopleCount: Int? = null,
    val savedPlaces: List<SavedPlace> = emptyList(),
    /** True while the user is in "tap the map to save a place" mode. */
    val isPinningSavedPlace: Boolean = false,
    /** The point tapped while [isPinningSavedPlace] — shows the "name this place" modal. */
    val pendingSavedPlacePoint: Point? = null
) {
    val impassableFloods: List<FloodReport>
        get() = floodReports.filter { it.isActiveHazard }

    val passableFloods: List<FloodReport>
        get() = floodReports.filter { it.isLiveHazard && !it.isActiveHazard }
}
