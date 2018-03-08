package com.mapfit.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.mapfit.android.MapTheme
import com.mapfit.android.MapView
import com.mapfit.android.MapfitMap
import com.mapfit.android.OnMapReadyCallback
import com.mapfit.android.geocoder.Geocoder
import com.mapfit.android.geocoder.GeocoderCallback
import com.mapfit.android.geocoder.model.Address
import com.mapfit.android.geometry.LatLng
import com.mapfit.mapfitdemo.R

class GeocoderActivity : AppCompatActivity() {

    lateinit var mapView: MapView
    lateinit var mapfitMap: MapfitMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        Mapfit.getInstance(this, getString(R.string.mapfit_debug_api_key))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)

        mapView.getMapAsync(MapTheme.MAPFIT_DAY, object : OnMapReadyCallback {
            override fun onMapReady(mapfitMap: MapfitMap) {
                setupMap(mapfitMap)
            }
        })
    }

    private fun setupMap(mapfitMap: MapfitMap) {
        this.mapfitMap = mapfitMap

        mapfitMap.setCenter(LatLng(40.74405, -73.99324))
        mapfitMap.setZoom(14f)

        reverseGeocodeAddress()

        // enable ui controls
        mapfitMap.getMapOptions().recenterButtonEnabled = true
        mapfitMap.getMapOptions().zoomControlsEnabled = true
        mapfitMap.getMapOptions().compassButtonEnabled = true
    }

    private fun reverseGeocodeAddress() {
        Geocoder().reverseGeocode(
            LatLng(40.74405, -73.99324),
            true,
            object : GeocoderCallback {
                override fun onError(message: String, e: Exception) {
                    e.printStackTrace()
                }

                override fun onSuccess(addressList: List<Address>) {

                    var latLng = LatLng()
                    addressList.forEach { address ->
                        latLng =
                                LatLng(address.entrances.first().lat, address.entrances.first().lng)
                    }
                    val marker = mapfitMap.addMarker(latLng)
                    val polygon = mapfitMap.addPolygon(addressList[0].building.polygon)

                }
            })
    }


}