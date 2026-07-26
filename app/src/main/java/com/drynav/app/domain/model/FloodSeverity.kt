package com.drynav.app.domain.model

/**
 * How severe a reported flood is.
 *
 * PASSABLE   – water on the road, drive with caution (shown as a warning,
 *              NOT excluded from routing).
 * IMPASSABLE – road is not drivable; routing must detour around it.
 */
enum class FloodSeverity {
    PASSABLE,
    IMPASSABLE;

    companion object {
        fun fromString(value: String?): FloodSeverity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PASSABLE
    }
}
