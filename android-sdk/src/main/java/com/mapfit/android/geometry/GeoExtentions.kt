package com.mapfit.android.geometry

import android.util.Log

/**
 * Extension functions for geo related classes.
 *
 * Created by dogangulcan on 2/20/18.
 */

internal fun LatLng.isValid(): Boolean {

    val latRegex = """^[-+]?([1-8]?\d(\.\d+)?|90(\.0+)?)$""".toRegex()
    val lonRegex = """^\s*[-+]?(180(\.0+)?|((1[0-7]\d)|([1-9]?\d))(\.\d+)?)${'$'}""".toRegex()

    val isLatValid = latRegex.containsMatchIn(lat.toString())
    val isLonValid = lonRegex.containsMatchIn(lng.toString())

    if (!isLatValid) {
        Log.e("Mapfit", "Invalid latitude: $lat. Should be in the range [-90, 90].")
    }

    if (!isLatValid) {
        Log.e("Mapfit", "Invalid longitude: $lng. Should be in the range [-180, 180).")
    }

    return isLatValid && isLonValid
}

internal fun LatLng.isEmpty() = lat == 0.0 && lng == 0.0

