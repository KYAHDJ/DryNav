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
   `com.drynav.app`, enable **Firestore** and **Anonymous Auth**, then drop the
   generated `google-services.json` into `app/`.

4. Build: open in Android Studio, or from the command line with a local
   Gradle install (this repo doesn't ship a `gradlew` wrapper):
   `gradle :app:assembleDebug`.

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
