package dev.tvsopen.transport

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.tvsopen.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-accuracy background GPS tracker using Android's native LocationManager.
 * Zero cloud dependency, fully offline.
 */
class LocationTracker(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private var isTracking = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _lastLocation.value = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) return
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "cannot start location tracking: permissions not granted")
            return
        }

        isTracking = true
        runCatching {
            val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = lastGps ?: lastNet
            if (best != null) _lastLocation.value = best

            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    2f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    5f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            Log.i(TAG, "location tracking started")
        }.onFailure { Log.e(TAG, "failed to start location updates: ${it.message}") }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        runCatching { locationManager?.removeUpdates(listener) }
        Log.i(TAG, "location tracking stopped")
    }

    companion object {
        private const val TAG = "LocationTracker"
    }
}
