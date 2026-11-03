package com.example.fithub.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocationManager(
    private val context: Context,
    private val onLocationChanged: (Location) -> Unit
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    /** Returns false if permission is missing so the UI can explain instead of crashing. */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(): Boolean {
        if (!hasPermission(context)) return false

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    onLocationChanged(location)
                }
            }
        }
        locationCallback = callback

        return try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    companion object {
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /** Last known position, or null if unavailable. Used for the weather card. */
        @SuppressLint("MissingPermission")
        suspend fun lastKnownLocation(context: Context): Location? {
            if (!hasPermission(context)) return null
            return try {
                withTimeoutOrNull(5000L) {
                    LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
