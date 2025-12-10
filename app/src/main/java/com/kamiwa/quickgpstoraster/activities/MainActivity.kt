package com.kamiwa.quickgpstoraster.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kamiwa.quickgpstoraster.R
import com.kamiwa.quickgpstoraster.adapters.PointAdapter
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

class MainActivity : AppCompatActivity() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationTextView: TextView
    private lateinit var mapView: MapView

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var altitude: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)

        // USTAWIENIA MAPY
        val inflater = LayoutInflater.from(this)
        val rootView = inflater.inflate(R.layout.activity_main, null)
        setContentView(rootView)

        // OBSŁUGA LOKALIZACJI
        locationTextView = findViewById<TextView>(R.id.mainActivity_coordinates)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        requestLocationPermission()
        setupLocationUpdates()

        mapView = rootView.findViewById<MapView>(R.id.mainActivity_mapView)
        mapView.getMapAsync { map ->
            map.setStyle("https://tiles.openfreemap.org/styles/liberty")
            map.cameraPosition = CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(1.0).build()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val pointList = mutableListOf<Triple<Double, Double, Double>>()
        val pointListView = findViewById<RecyclerView>(R.id.mainActivity_pointListView)
        val pointAddButton = findViewById<Button>(R.id.mainActivity_addPointButton)
        val pointResetButton = findViewById<Button>(R.id.mainActivity_clearPointListButton)
        val adapter = PointAdapter(pointList)

        pointListView.layoutManager = LinearLayoutManager(this)
        pointListView.adapter = adapter

        pointAddButton.setOnClickListener {
            val pointValues = Triple(latitude, longitude, altitude)
            pointList.add(pointValues)
            adapter.notifyItemInserted(pointList.size - 1)
        }
        pointResetButton.setOnClickListener {
            val pointListSize = pointList.size
            pointList.clear()
            adapter.notifyItemRangeRemoved(0, pointListSize)
        }

    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                latitude = location.latitude
                longitude = location.longitude
                altitude = location.altitude
                locationTextView.text =
                    "Lat: ${location.latitude} Lon: ${location.longitude} Alt: ${location.altitude}"
                mapView.getMapAsync { map ->
                    val position = CameraPosition.Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(15.0)
                        .build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }
}