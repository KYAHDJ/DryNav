package com.drynav.app.presentation.tutorial

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import com.drynav.app.data.prefs.UserPreferences
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.sound.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One stop on the full-app guided tour. */
data class TutorialStep(
    val route: String,
    val targetKey: String,
    val title: String,
    val description: String
)

/**
 * App-wide guided tour: darkens the screen, spotlights one real UI element
 * at a time, and drives the user through every main screen in order.
 *
 * Shown exactly ONCE per signed-in account, not once per app open — see
 * [UserPreferences.hasSeenTutorial]/[markTutorialSeen], which is real
 * on-device storage (DataStore), not an in-memory flag: it survives normal
 * app opens/closes, and only resets on a fresh install or clearing the
 * app's data, or the first time a genuinely different account signs in.
 */
@Singleton
class TutorialManager @Inject constructor(
    private val userPreferences: UserPreferences,
    private val soundManager: SoundManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Registered by each screen's `Modifier.tutorialTarget` calls as they compose. */
    val targets = mutableStateMapOf<String, Rect>()

    /** "Welcome to DryNav — want a quick tour?" prompt, before the tour itself starts. */
    var showWelcomePrompt by mutableStateOf(false)
        private set

    var isActive by mutableStateOf(false)
        private set
    var stepIndex by mutableIntStateOf(0)
        private set

    /** The post-tour celebration overlay. */
    var showCelebration by mutableStateOf(false)
        private set

    /** Remembered from [checkForUser] so later calls (declining/finishing) don't need it threaded back in. */
    private var currentUserId: String? = null
    private var checkedForUserId: String? = null

    val steps: List<TutorialStep> = buildTutorialSteps()

    val currentStep: TutorialStep? get() = steps.getOrNull(stepIndex)

    /**
     * Call from the Home screen's first composition each time it's shown.
     * A no-op after the first check for a given [userId] within this
     * process, so recomposition/re-navigation to Home doesn't re-query
     * DataStore or re-show a just-dismissed prompt.
     */
    fun checkForUser(userId: String) {
        currentUserId = userId
        if (userId.isBlank() || checkedForUserId == userId) return
        checkedForUserId = userId
        scope.launch {
            if (!userPreferences.hasSeenTutorial(userId)) {
                showWelcomePrompt = true
            }
        }
    }

    fun beginTour() {
        showWelcomePrompt = false
        isActive = true
        stepIndex = 0
    }

    /** Declining the welcome prompt entirely — counts as "seen", same as finishing it. */
    fun declineTour() {
        showWelcomePrompt = false
        markSeen()
    }

    fun next() {
        if (stepIndex < steps.lastIndex) stepIndex += 1 else finishTour()
    }

    fun back() {
        if (stepIndex > 0) stepIndex -= 1
    }

    /** Skipping mid-tour still ends on the same celebratory note as finishing normally. */
    fun skip() = finishTour()

    private fun finishTour() {
        isActive = false
        showCelebration = true
        // Fired from here (not a Compose LaunchedEffect on the celebration
        // overlay) so it plays deterministically the instant the tour ends,
        // regardless of which screen happens to be composed at that moment.
        soundManager.playVictory()
    }

    fun dismissCelebration() {
        showCelebration = false
        markSeen()
    }

    private fun markSeen() {
        val userId = currentUserId ?: return
        if (userId.isBlank()) return
        scope.launch { userPreferences.markTutorialSeen(userId) }
    }
}

private fun buildTutorialSteps(): List<TutorialStep> = listOf(
    // ---- Home ----
    TutorialStep(
        route = Routes.HOME,
        targetKey = "flood_card",
        title = "Live flood conditions",
        description = "See how many roads near you are flooded right now, updated in real time as other users report them."
    ),
    TutorialStep(
        route = Routes.HOME,
        targetKey = "navigate_button",
        title = "Navigate safely",
        description = "Get turn-by-turn directions that automatically avoid — and reroute around — flooded roads."
    ),
    TutorialStep(
        route = Routes.HOME,
        targetKey = "report_button",
        title = "Report a flood",
        description = "Spot a flooded road? Mark it here so it warns and reroutes everyone else too."
    ),
    // ---- Start Navigation preview ----
    TutorialStep(
        route = Routes.MAP_HOME,
        targetKey = "layers_button",
        title = "Map layers",
        description = "Switch between default, satellite, and terrain map views, and toggle live traffic."
    ),
    TutorialStep(
        route = Routes.MAP_HOME,
        targetKey = "start_navigation_button",
        title = "Start Navigation",
        description = "Opens the full map, where you can search for a destination and get directions."
    ),
    // ---- Full map / search ----
    TutorialStep(
        route = Routes.MAP,
        targetKey = "search_bar",
        title = "Search anywhere",
        description = "Type a destination, or tap directly on the map to drop a pin instead."
    ),
    TutorialStep(
        route = Routes.MAP,
        targetKey = "recenter_button",
        title = "Recenter",
        description = "Snap the map back to your current location any time you've panned away."
    ),
    TutorialStep(
        route = Routes.MAP,
        targetKey = "travel_mode_chips",
        title = "Pick your ride",
        description = "Pick Car, Motorcycle, or Walk any time — the route and ETA update automatically."
    ),
    TutorialStep(
        route = Routes.MAP,
        targetKey = "navigate_prompt",
        title = "Navigate here",
        description = "Tap any spot on the map to drop a pin, then tap \"Navigate here\" to get directions straight to it."
    ),
    // ---- Report a flood ----
    TutorialStep(
        route = Routes.REPORT,
        targetKey = "brush_tool",
        title = "Draw the flooded road",
        description = "Turn on the brush and drag along the map to paint exactly which stretch of road is flooded."
    ),
    TutorialStep(
        route = Routes.REPORT,
        targetKey = "history_buttons",
        title = "Undo, redo, clear",
        description = "Fix a mistake while drawing — undo or redo your last stroke, or clear everything and start over."
    ),
    TutorialStep(
        route = Routes.REPORT,
        targetKey = "photo_picker",
        title = "Add a photo",
        description = "Attach photos of the flooding to help other users — and admins reviewing the report — see it clearly."
    ),
    TutorialStep(
        route = Routes.REPORT,
        targetKey = "severity_toggle",
        title = "Passable or blocked",
        description = "Mark whether the road is still slowly passable, or completely blocked by the flood."
    ),
    TutorialStep(
        route = Routes.REPORT,
        targetKey = "submit_button",
        title = "Submit your report",
        description = "Send it in — once approved, it immediately warns and reroutes every other driver nearby."
    ),
    // ---- Profile ----
    TutorialStep(
        route = Routes.PROFILE,
        targetKey = "profile_details_row",
        title = "Your profile",
        description = "Update your display name and manage your account here — including password and deleting your account."
    ),
    TutorialStep(
        route = Routes.PROFILE,
        targetKey = "mood_row",
        title = "Your mood character",
        description = "Pick the character other users see representing you live on the map — this is public, everyone online can see it."
    )
)
