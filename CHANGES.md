# DryNav Flood Reporting Refactor

Implemented the requested frontend/UX refactor without adding Teachable Machine or TensorFlow image recognition.

## Profile
- Added dedicated Edit Profile route and screen.
- Added profile picture selection and display-name editing.
- Profile picture uploads to Firebase Storage and updates Firebase Auth profile photo URL.
- Removed Mood from profile state/preferences and live presence Firestore payload.
- Preserved the existing Dark Mode implementation.
- Removed background music, UI sound effects, sound hooks, managers, and audio assets.

## Flood reporting
- Flood status is now exactly Passable / Not Passable radio buttons.
- Not Passable is displayed in red.
- A live camera photo is mandatory before submission.
- Gallery selection is not available from the flood report screen.
- Camera capture uses FileProvider and the Android camera activity.
- Capture timestamp and current GPS coordinates are attached to every captured photo.
- Report Firestore payload stores `photoMetadata` alongside uploaded `photoUrls`.
- Any mandatory photo upload failure blocks report submission.

## Validation
- Searched the source tree for removed Mood/music/sound references: none remain.
- Checked for the previous duplicate `MAX_POINTS_PER_STROKE` declaration: only one remains.
- Confirmed the new Edit Profile route, camera permission/provider, and photo metadata references exist.
- The environment does not provide an Android SDK/Gradle distribution, so a full `:app:assembleDebug` build cannot be executed here. The source was checked for Kotlin parsing errors with the installed Kotlin compiler; only expected unresolved Android/dependency symbols were produced because the Android/Gradle classpath is unavailable.


# Latest requested cleanup
- Profile now opens a dedicated Edit Profile screen from a visible edit icon and the Profile Details row.
- Removed Mood UI, preference state, model, tutorial step, and mood image assets.
- Dark Mode retained because it was already implemented.
- Background music/sound assets are absent; no music feature was added.
- Flood report already used exactly two Passable / Not Passable radio options, with Not Passable in red and mandatory live camera photo.
- Added `ml/` training pipeline for a real two-class flooded/not_flooded TFLite model. No fake model is bundled; a trained model requires real labeled training images.
## Navigation/report fix
- Navigation camera now uses a lower driver/rider focal position, 45° following pitch, and heading-following bearing so the road ahead stays visible.
- Firebase report submission now waits for an authenticated Firebase user before Storage/Firestore writes, preventing the startup `PERMISSION_DENIED` race.


## v1.3 — navigation camera + manual report pin + Firebase rules

- Navigation now uses a driver-focused bird's-eye following camera: 58° pitch, lower-third focal point, tighter navigation zoom, and heading-following camera behavior.
- Navigation is explicitly primed with the current enhanced location/bearing when a trip starts so it cannot begin in the old flat north-up view.
- Flood report location is now **manual-only**. GPS is never automatically assigned as the flood pin.
- Painting a flood-area stroke no longer silently creates a report pin. The user must tap **Pin Flood** and tap the map.
- Added `FIREBASE_STORAGE_RULES.example` and tightened `FIREBASE_REPORT_RULES.example` to match authenticated reporter IDs.


## v1.5 — navigation camera restored + precise flood-aware rerouting

- Restored the v1.3 navigation camera configuration unchanged: 58° bird's-eye follow pitch, lower-third focal point, tight navigation zoom, and heading-following behavior.
- Kept the v1.3 visual/navigation camera behavior instead of introducing the v1.4 rerouting changes into the camera layer.
- Flood-aware routing first calculates a normal Mapbox baseline, then excludes only approved active flood reports that actually intersect that baseline route.
- Automatic rerouting ignores flood hazards that are already behind the map-matched vehicle position.
- Reintroduced periodic rechecks while a route still has an active flood ahead, so a better alternate can be found after the vehicle moves through a junction.
- Added an 8-second debounce to prevent Firestore updates/off-route callbacks from repeatedly replacing the route in rapid succession.
- Flood intersection checks use the full report geometry and route-relative distance, reducing false reroutes from nearby/behind flood points.
- No automatic flood pinning or Firebase report behavior was changed.

## Image recognition integration
- Added the exported Teachable Machine TensorFlow Lite model and labels.
- Added on-device analysis immediately after a report camera photo is captured.
- Added a per-photo AI confidence score (1–100) and explanatory message in the Report tab.
- Added FLOOD / NOT_FLOOD / BLURRY_UNUSABLE / UNCERTAIN handling.
- Kept manual flood-pin placement mandatory.
- Report submission is blocked until every attached photo passes the flood image check.
- Initial conservative FLOOD submission threshold is 80%.
