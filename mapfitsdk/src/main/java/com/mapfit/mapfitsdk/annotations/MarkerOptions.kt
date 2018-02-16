package com.mapfit.mapfitsdk.annotations

import com.mapfit.mapfitsdk.MapController
import java.util.HashMap

/**
 * Defines options for [Marker].
 *
 * Created by dogangulcan on 1/3/18.
 */
class MarkerOptions internal constructor(
    private var marker: Marker,
    internal val mapController: MutableList<MapController>
) {


    val properties by lazy {
        val props = HashMap<String, String>()
//        props["type"] = "lines"
        props["type"] = "point"
        props["color"] = "yellow"
        getStringMapAsArray(props)
    }

    private fun getStringMapAsArray(properties: Map<String, String>): Array<String?> {
        val out = arrayOfNulls<String>(properties.size * 2)
        var i = 0
        for ((key, value) in properties) {
            out[i++] = key
            out[i++] = value
        }
        return out
    }


    private val markerDotSide by lazy {
        11
    }

    /**
     * Height of the marker icon in pixels.
     */
    var height = 59
        set(value) {
            if (!marker.usingDefaultIcon) {
                field = value
                updateStyle()
            }
        }

    var width = 55
        set(value) {
            if (!marker.usingDefaultIcon) {
                field = value
                updateStyle()
            }
        }

    var drawOrder = 2000
        set(value) {
            field = value
            updateStyle()
        }

    var color = "white"
        set(value) {
            field = value
            updateStyle()
        }

    internal fun setDefaultMarkerSize() {
        marker.usingDefaultIcon = false
        height = 59
        width = 55
        marker.usingDefaultIcon = true
    }

    private val placeInfoMarkerStyle =
        "{ style: 'icons', anchor: top,color: $color, size: [${markerDotSide}px, ${markerDotSide}px], order: $drawOrder, interactive: true, collide: false }"

//    internal var style =
//        "{ style: 'icons', anchor: top, color: $color, size: [${width}px, ${height}px], order: $drawOrder, interactive: true, collide: false }"
//        set(value) {
//            field = value
//            updateStyle()
//        }

    init {
        updateStyle()
    }

    private fun getStyleString() =
        "{ style: 'icons', anchor: top, color: $color, size: [${width}px, ${height}px], order: $drawOrder, interactive: true, collide: false }"

    internal fun updateStyle() {
        marker.mapBindings.forEach {
            it.key.setMarkerStylingFromString(it.value, getStyleString())
        }
    }

    internal fun placeInfoShown(
        isShown: Boolean,
        markerId: Long,
        mapController: MapController
    ) {
        if (isShown) {
            mapController.setMarkerStylingFromString(markerId, placeInfoMarkerStyle)

        } else {
            mapController.setMarkerStylingFromString(markerId, getStyleString())
        }
    }

}