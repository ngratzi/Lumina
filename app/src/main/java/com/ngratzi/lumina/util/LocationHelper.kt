package com.ngratzi.lumina.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.ngratzi.lumina.data.model.GeocodedResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun getLastLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            fusedClient.lastLocation.await()
        } catch (e: Exception) { null }
    }

    suspend fun getCurrentLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
        } catch (e: Exception) {
            getLastLocation()
        }
    }

    fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull() ?: return null
            addr.locality ?: addr.subAdminArea ?: addr.adminArea
        } catch (e: Exception) { null }
    }

    suspend fun forwardGeocode(query: String): List<GeocodedResult> {
        if (query.isBlank()) return emptyList()
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 6) { cont.resume(it) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 6) ?: emptyList()
            }
            addresses.mapNotNull { addr ->
                val name = buildString {
                    addr.locality?.let { append(it) }
                    addr.adminArea?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    addr.countryCode?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }.ifBlank { addr.getAddressLine(0) ?: return@mapNotNull null }
                GeocodedResult(name, addr.latitude, addr.longitude)
            }
        } catch (e: Exception) { emptyList() }
    }
}
