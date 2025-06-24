package com.example.yandexmapapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import android.widget.Button
import android.widget.Toast
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.request.DrivingOptions
import com.yandex.mapkit.directions.request.Request
import com.yandex.mapkit.directions.DirectionsListener
import com.yandex.mapkit.directions.response.DrivingRoute
import com.yandex.mapkit.directions.response.Route
import com.yandex.runtime.Error
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.PolylineMapObject
import java.util.ArrayList

class MainActivity : AppCompatActivity(), UserLocationObjectListener, InputListener, DirectionsListener {

    private lateinit var mapView: MapView
    private lateinit var userLocationLayer: UserLocationLayer
    private val LOCATION_PERMISSION_REQUEST_CODE = 123
    private var isLocationEnabled = false
    private var points = mutableListOf<Point>()
    private lateinit var mapObjects: MapObjectCollection
    private var route: PolylineMapObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapKitFactory.initialize(this)

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapview)
        mapObjects = mapView.map.mapObjects.addCollection()

        val zoomToKemerovoButton: Button = findViewById(R.id.zoom_to_kemerovo_button)
        zoomToKemerovoButton.setOnClickListener {
            val kemerovoPoint = Point(55.354993, 86.085805)
            mapView.map.move(
                CameraPosition(kemerovoPoint, 15.0f, 0.0f, 0.0f),
                Animation(Animation.Type.SMOOTH, 1f),
                null
            )
        }

        val zoomToLocationButton: Button = findViewById(R.id.zoom_to_location_button)
        zoomToLocationButton.setOnClickListener {
            if (isLocationEnabled) {
                zoomToUserLocation()
            } else {
                Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            }
        }

        val clearRouteButton: Button = findViewById(R.id.clear_route_button)
        clearRouteButton.setOnClickListener {
            clearRoute()
        }

        checkLocationPermission()

        mapView.map.addInputListener(this)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableUserLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableUserLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun enableUserLocation() {
        val mapKit = MapKitFactory.getInstance()
        userLocationLayer = mapKit.createUserLocationLayer(mapView.mapWindow)
        userLocationLayer.isVisible = true
        userLocationLayer.isHeadingEnabled = true
        userLocationLayer.setObjectListener(this)
        isLocationEnabled = true
    }

    private fun zoomToUserLocation() {
        if (userLocationLayer.cameraPosition() != null) {
            mapView.map.move(
                userLocationLayer.cameraPosition()!!,
                Animation(Animation.Type.SMOOTH, 1f),
                null
            )
        } else {
            Toast.makeText(this, "Location not yet available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        userLocationView.arrow.setIcon(ImageProvider.fromResource(this, R.drawable.ic_arrow))
        userLocationView.pin.setIcon(ImageProvider.fromResource(this, R.drawable.ic_pin))
    }

    override fun onObjectRemoved(view: UserLocationView) {}

    override fun onObjectUpdated(userLocationView: UserLocationView, event: com.yandex.mapkit.user_location.ObjectEvent) {}

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.destroy()
    }

    override fun onMapTap(map: Map, point: Point) {

    }

    override fun onMapLongTap(map: Map, point: Point) {
        addPoint(point)
    }

    private fun addPoint(point: Point) {
        points.add(point)
        val placemarkMapObject = mapObjects.addPlacemark(point)
        placemarkMapObject.setIcon(ImageProvider.fromResource(this, R.drawable.ic_placemark))
        placemarkMapObject.setIconStyle(IconStyle().setScale(0.5f))

        if (points.size == 2) {
            buildRoute()
        } else if (points.size > 2) {
            clearRoute()
            points.clear()
            mapObjects.clear()
            points.add(point)
            val newPlacemarkMapObject = mapObjects.addPlacemark(point)
            newPlacemarkMapObject.setIcon(ImageProvider.fromResource(this, R.drawable.ic_placemark))
            newPlacemarkMapObject.setIconStyle(IconStyle().setScale(0.5f))
        }
    }

    private fun buildRoute() {
        val drivingOptions = DrivingOptions().apply {
            avoidTolls = true
        }

        val requestPoints = ArrayList<com.yandex.mapkit.directions.request.RequestPoint>()
        for (point in points) {
            requestPoints.add(
                com.yandex.mapkit.directions.request.RequestPoint(
                    point,
                    com.yandex.mapkit.directions.request.RequestPointType.WAYPOINT
                )
            )
        }

        val drivingRouter = DirectionsFactory.getInstance().createDrivingRouter()
        val drivingRequest = drivingRouter.requestDrivingRoute(requestPoints, drivingOptions, this)
        drivingRequest.submit()
    }

    override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
        if (routes.isNotEmpty()) {
            val polyline = routes[0].geometry
            route = mapObjects.addPolyline(polyline)
        } else {
            Toast.makeText(this, "No routes found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDrivingRoutesError(error: Error) {
        val errorMessage = when (error.remoteError?.code) {
            "errors.invalid_parameter" -> "Invalid parameters"
            "errors.too_many_waypoints" -> "Too many waypoints"
            else -> "Error occurred: ${error.message}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun clearRoute() {
        mapObjects.clear()
        points.clear()
        route = null
    }
}