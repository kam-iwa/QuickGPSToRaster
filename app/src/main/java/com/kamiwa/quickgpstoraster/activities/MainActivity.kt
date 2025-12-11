package com.kamiwa.quickgpstoraster.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kamiwa.quickgpstoraster.R
import com.kamiwa.quickgpstoraster.adapters.PointAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import kotlin.math.abs

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

        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py = Python.getInstance()
        val module = py.getModule("create_raster")

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
            map.uiSettings.setAllGesturesEnabled(false)
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
        val createRasterButton = findViewById<Button>(R.id.mainActivity_createRasterFromPoints)
        val progressBar = findViewById<ProgressBar>(R.id.mainActivity_progressBar)
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
        createRasterButton.setOnClickListener {
            if (pointList.size < 3){
                Toast.makeText(this@MainActivity, getString(R.string.toast_invalid_data), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            createRasterButton.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) { // wstawiłem to, by skrypt Pythona nie przeszkadzał kółku postępu się kręcić :3
                try {
                    val pyList = py.builtins.callAttr("list")
                    for (triple in pointList) {
                        val pyTuple = py.builtins.callAttr(
                            "tuple",
                            arrayOf(triple.first, triple.second, triple.third)
                        )
                        pyList.callAttr("append", pyTuple)
                    }

                    val currentTimeMillis = System.currentTimeMillis()
                    val outputPath =
                        getExternalFilesDir(null)?.absolutePath + "/raster_${currentTimeMillis}.tiff"
                    val outputControlPointsPath =
                        getExternalFilesDir(null)?.absolutePath + "/raster_${currentTimeMillis}.points"
                    val rasterCreatorInstance = module.callAttr("RasterCreator", pyList, outputPath, outputControlPointsPath)
                    rasterCreatorInstance.callAttr("create_raster")

                    withContext(Dispatchers.Main) { // jak wyżej - dla kółka postępu :3
                        progressBar.visibility = View.GONE
                        createRasterButton.isEnabled = true
                        Toast.makeText(this@MainActivity, "${getString(R.string.toast_success)}: $outputPath", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { // jak wyżej - dla kółka postępu :3
                        progressBar.visibility = View.GONE
                        createRasterButton.isEnabled = true
                        Log.d("PYTHON_DEBUG", "${e.message}")
                        Toast.makeText(this@MainActivity, "${getString(R.string.toast_error)}: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
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

                val latFormatted = formatCoordinate(location.latitude, true)
                val lonFormatted = formatCoordinate(location.longitude, false)

                locationTextView.text = "$latFormatted $lonFormatted\n(${location.altitude} m n.p.m.)"
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

    private fun formatCoordinate(coordinate: Double, isLatitude: Boolean): String {
        val direction = if (isLatitude) {
            if (coordinate >= 0) "N" else "S"
        } else {
            if (coordinate >= 0) "E" else "W"
        }
        return "${abs(coordinate)} $direction"
    }
}