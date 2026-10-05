package com.uc0079.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Current weather for device location via Open-Meteo (no API key).
 */
object WeatherRepository {

    data class Snapshot(
        val tempC: Int,
        val labelJa: String,
        val symbol: String,
        val place: String?,
    )

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers.mapNotNull { name ->
            runCatching {
                if (lm.isProviderEnabled(name)) lm.getLastKnownLocation(name) else null
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    suspend fun fetch(context: Context): Snapshot? = withContext(Dispatchers.IO) {
        val loc = lastKnownLocation(context)
            ?: withTimeoutOrNull(8_000) { awaitFreshLocation(context) }
            ?: return@withContext null
        val weather = fetchWeather(loc.latitude, loc.longitude) ?: return@withContext null
        val place = reverseGeocode(context, loc.latitude, loc.longitude)
        weather.copy(place = place)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFreshLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            runCatching {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun fetchWeather(lat: Double, lon: Double): Snapshot? {
        val url =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&timezone=auto"
        val body = httpGet(url) ?: return null
        return runCatching {
            val current = JSONObject(body).getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val code = current.getInt("weather_code")
            val (symbol, label) = describe(code)
            Snapshot(tempC = temp, labelJa = label, symbol = symbol, place = null)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val list = Geocoder(context, Locale.JAPAN).getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull() ?: return null
            addr.locality
                ?: addr.subAdminArea
                ?: addr.adminArea
                ?: addr.featureName
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Z-Gundam-OS-Launcher")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.readText()?.takeIf { code in 200..299 }
        } finally {
            conn.disconnect()
        }
    }

    /** WMO weather interpretation codes → HUD symbol + short Japanese. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀" to "晴れ"
        1 -> "🌤" to "ほぼ晴れ"
        2 -> "⛅" to "くもり"
        3 -> "☁" to "くもり"
        45, 48 -> "☁" to "霧"
        51, 53, 55 -> "🌦" to "霧雨"
        56, 57 -> "🌧" to "着氷性の霧雨"
        61, 63, 65 -> "🌧" to "雨"
        66, 67 -> "🌧" to "着氷性の雨"
        71, 73, 75, 77 -> "❄" to "雪"
        80, 81, 82 -> "🌧" to "にわか雨"
        85, 86 -> "❄" to "にわか雪"
        95 -> "⛈" to "雷雨"
        96, 99 -> "⛈" to "雹を伴う雷雨"
        else -> "☁" to "天気"
    }
}
