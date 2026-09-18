# DryNav — Flood-Aware Navigation

A Waze-style navigation app that routes drivers *around* flooded roads in real time.

## Stack
- MVVM + Clean Architecture (data / domain / presentation)
- Jetpack Compose + Material 3
- Hilt DI
- Mapbox Maps + Navigation SDK v2
- Firebase Firestore (realtime flood reports) + Firebase Auth (anonymous)
- Kotlin Coroutines & Flow

## Setup (required before building)

Both tokens below are machine-local secrets — never commit real values for
either of them (that's why they live outside any tracked file).

1. **Mapbox download token** (secret, `DOWNLOADS:READ` scope): add it to your
   **global** `~/.gradle/gradle.properties` (create the file if it doesn't
   exist) as `MAPBOX_DOWNLOADS_TOKEN=sk.xxxx`. This file lives outside any
   git repo on your machine, so it's never committed.

2. **Mapbox public token**: add it to this project's `local.properties`
   (already gitignored) as `MAPBOX_ACCESS_TOKEN=pk.xxxx`. `app/build.gradle.kts`
   reads it from there and generates the `mapbox_access_token` string
   resource at build time — nothing to edit in `strings.xml`.

3. **Firebase**: create a Firebase project, add an Android app with package
   `com.drynav.app`, enable **Firestore**, **Anonymous Auth**, and **Storage**,
   then drop the generated `google-services.json` into `app/`.

   Deploy/review the included example rules before testing flood reports:
   - `FIREBASE_REPORT_RULES.example` for `flood_reports`
   - `FIREBASE_STORAGE_RULES.example` for `flood_photos/*`

   The Android app authenticates before uploading the photo or writing the
   report. If Firebase Console rules still deny authenticated users, Firebase
   will correctly return `PERMISSION_DENIED`; changing Android code alone
   cannot override server-side Firebase Security Rules.

4. Build: open in Android Studio, or from the command line with a local
   Gradle install (this repo doesn't ship a `gradlew` wrapper):
   `gradle :app:assembleDebug`.

## Flood report pin behavior

The flood location is **never auto-pinned from GPS**. GPS is retained only as
the reporter's physical location. The user must press **Pin Flood** and tap the
map to choose the flood location. Painting a road/stroke does not create a pin
implicitly, and the Report button stays disabled until a manual pin exists.

## Firestore data model

Collection `flood_reports`:

| field       | type      |
|-------------|-----------|
| latitude    | double    |
| longitude   | double    |
| severity    | string (`PASSABLE` \| `IMPASSABLE`) |
| timestamp   | number (epoch ms) |
| upvotes     | number    |
| isCleared   | boolean   |

Suggested security rules: authenticated users may create reports and increment
`upvotes`; only cloud functions / moderators may set `isCleared = true`.

## How flood avoidance works

1. `FloodRepositoryImpl` attaches a Firestore `addSnapshotListener` and emits
   `Flow<List<FloodReport>>` via `callbackFlow`.
2. `FloodAwareRouter` converts every `IMPASSABLE` report into a
   `point(lng lat)` entry for the Mapbox Directions `exclude` parameter
   (`RouteOptions.builder().excludeList(...)`), so the Directions engine is
   *guaranteed* not to route through those coordinates.
3. While navigating, the router keeps collecting the live flood Flow. If a new
   impassable report lands within ~40 m of the active route geometry
   (Turf `pointToLineDistance`), it automatically re-requests routes with the
   updated exclusion list and swaps them in.


### v1.5 routing behavior
The navigation camera remains on the v1.3 driver-focused configuration. Flood-aware rerouting now starts from the current enhanced position, considers only route-intersecting approved live hazards, ignores hazards behind the vehicle, and periodically rechecks a blocked route from the vehicle's current position.


## Flood report anti-spam

DryNav prevents the same authenticated user from creating another flood report within 500 meters of a previous report during the 15-minute cooldown window. Existing reports still remain visible and other users can independently report/confirm the same flooded area.

## v1.13 changes

The map flood-report image viewer now displays a loading spinner while remote images load and an error message if a photo cannot be fetched. Flood report tap selection is restricted to a small center target rather than the entire affected circle, making nearby roads easier to select.
