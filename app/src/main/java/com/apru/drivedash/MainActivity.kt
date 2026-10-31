package com.apru.drivedash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.os.Handler
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlin.random.Random
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateMapOf
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.google.maps.android.compose.Polyline
import android.media.ToneGenerator
import android.media.AudioManager
import android.location.Location
import androidx.compose.foundation.lazy.items
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.rememberMarkerState
import org.json.JSONArray
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.encoder.Encoder
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.font.FontWeight
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode

import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.MapType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

//screens our app can show
enum class Screen {
    HOME, CREATE_ROOM, JOIN_ROOM, IN_ROOM, TRIP_SUMMARY, SETTINGS, MY_RIDES
}

// app theme modes
enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

// Units of measurement
enum class UnitSystem {
    METRIC, IMPERIAL
}

// Map Orientation
enum class MapOrientation {
    HEADING_UP, NORTH_UP
}

// Map Perspective
enum class MapPerspective {
    VIEW_3D, VIEW_2D
}

// Voice Transmission Mode
enum class VoiceTransmissionMode {
    OPEN_MIC, PUSH_TO_TALK
}

// the session type the user picks before creating/joining a room:
// - VOICE_ONLY: audio call only, no map rendered (saves battery/data)
// - MAP_ONLY: live location + route only, voice SDK never starts
// - MAP_VOICE: both, the full experience (default)
enum class RideMode {
    VOICE_ONLY, MAP_ONLY, MAP_VOICE
}

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance("https://drivedash-5b3be-default-rtdb.asia-southeast1.firebasedatabase.app")
    private var currentLat = mutableStateOf(0.0)
    private var currentLng = mutableStateOf(0.0)
    private var currentSpeed = mutableStateOf(0f)
    private var currentBearing = mutableStateOf(0f)
    //which screen is currently showing
    private var currentScreen = mutableStateOf(Screen.HOME)
    //the room code the user is currently in (empty if none)
    private var activeRoomCode = mutableStateOf("")
    //any error message to show the user (e.g. "Room expired")
    private var errorMessage = mutableStateOf("")
    private var roomHostId = mutableStateOf("")
    private var roomExpiresAt = mutableStateOf(0L)
    private var destinationInput = mutableStateOf("")
    private var destinationLatLng = mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null)
    private var routePoints = mutableStateOf<List<com.google.android.gms.maps.model.LatLng>>(emptyList())
    private var routeError = mutableStateOf("")
    private var destinationSuggestions = mutableStateOf<List<PlaceSuggestion>>(emptyList())
    private var isSearchingPlaces = mutableStateOf(false)
    private var suppressNextSearch = false // true right after tapping a suggestion, so it doesn't re-search itself

    data class PlaceSuggestion(val label: String, val lat: Double, val lng: Double)

    // voice call
    private var callManager: WebRtcCallManager? = null
    private var isMuted = mutableStateOf(false)
    private var micPermissionGranted = mutableStateOf(false)
    private var speakingUids = mutableStateMapOf<String, Boolean>()
    private var isBluetoothAudioActive = mutableStateOf(false)
    private var bluetoothAudioDeviceName = mutableStateOf("")
    private var isSpeakerOn = mutableStateOf(true)

    // display name shown to other riders
    private var myUsername = mutableStateOf("")

    // Location & GPS hardware status
    private var isLocationHardwareEnabled = mutableStateOf(true)
    private var isLocationPermissionGranted = mutableStateOf(true)

    // --- ADVANCED FEATURE 1 & 2: CONVOY SEPARATION & SOS ---
    private var isMySosActive = mutableStateOf(false)
    private var activeSosRiders = mutableStateMapOf<String, String>() // uid -> rider name
    private var riders = mutableStateMapOf<String, RiderLocation>()
    private var strayWarningText = mutableStateOf<String?>(null)
    private var lastStrayAlertTimestamp = 0L

    // Repeating SOS sound alert every 15 seconds until turned off
    private val sosAlarmHandler = Handler(Looper.getMainLooper())
    private val sosAlarmRunnable = object : Runnable {
        override fun run() {
            if ((isMySosActive.value || activeSosRiders.isNotEmpty()) && settingNotifySos.value) {
                playSosAlarmSound()
                sosAlarmHandler.postDelayed(this, 15000L)
            }
        }
    }

    private fun playToneSafely(streamType: Int, volume: Int, toneType: Int, durationMs: Int) {
        try {
            val tg = ToneGenerator(streamType, volume)
            tg.startTone(toneType, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, (durationMs + 200).toLong())
        } catch (_: Exception) {}
    }

    private fun playSosAlarmSound() {
        playToneSafely(AudioManager.STREAM_ALARM, 100, ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
    }

    private fun updateSosAlarmState() {
        sosAlarmHandler.removeCallbacks(sosAlarmRunnable)
        if ((isMySosActive.value || activeSosRiders.isNotEmpty()) && settingNotifySos.value) {
            playSosAlarmSound()
            sosAlarmHandler.postDelayed(sosAlarmRunnable, 15000L)
        }
    }

    // --- ADVANCED FEATURE 5: TRIP TELEMETRY & SUMMARY ---
    data class TripStats(
        val id: String = java.util.UUID.randomUUID().toString(),
        val roomCode: String,
        val totalDistanceKm: Double,
        val durationMillis: Long,
        val averageSpeedKmh: Double,
        val topSpeedKmh: Double,
        val timestamp: Long = System.currentTimeMillis(),
        val routePolyline: String = "",
        val isFavorite: Boolean = false,
        val ridersCount: Int = 0
    )
    private var tripStartTime = 0L
    private var tripTotalDistanceMeters = 0.0
    private var tripMaxSpeedMps = 0f
    private var lastRecordedLocation: Location? = null
    private val currentTripBreadcrumbs = mutableListOf<LatLng>()
    private var lastBreadcrumbLocation: Location? = null
    private var completedTripStats = mutableStateOf<TripStats?>(null)
    private var savedLastTripStats = mutableStateOf<TripStats?>(null)
    private var savedTripsHistory = mutableStateListOf<TripStats>()

    // Settings State
    private var settingVoiceOverlayEnabled = mutableStateOf(true)
    private var settingVoiceOverlayOnlySpeaking = mutableStateOf(false)
    private var settingSeparationDistanceMeters = mutableStateOf(500f)
    private var settingVoiceAutoJoin = mutableStateOf(true)
    private var appThemeMode = mutableStateOf(AppThemeMode.SYSTEM)

    // 1. Units of measurement
    private var settingUnitSystem = mutableStateOf(UnitSystem.METRIC)

    // 2. Map View & Orientation & Perspective
    private var settingMapOrientation = mutableStateOf(MapOrientation.HEADING_UP)
    private var settingMapPerspective = mutableStateOf(MapPerspective.VIEW_3D)

    // 3. Map Type / Layers
    private var settingMapType = mutableStateOf("NORMAL")
    private var settingTrafficEnabled = mutableStateOf(true)

    // 4. Auto-Recenter Camera
    private var settingAutoRecenter = mutableStateOf(true)

    // 5. Speed Limit & Speed Warnings
    private var settingSpeedWarningEnabled = mutableStateOf(false)
    private var settingSpeedLimitKmh = mutableStateOf(80f)
    private var settingSpeedChimeEnabled = mutableStateOf(true)
    private var lastSpeedAlertTimestamp = 0L

    // 6. Emergency Contacts (up to 3) & False-Alarm Countdown
    data class EmergencyContact(
        val name: String,
        val phone: String
    )
    private var emergencyContactsList = mutableStateListOf<EmergencyContact>()
    private var settingEmergencyContactName = mutableStateOf("")
    private var settingEmergencyContactPhone = mutableStateOf("")
    private var settingSosCountdownSeconds = mutableStateOf(5)
    private var sosCountdownRemaining = mutableStateOf(0)
    private var isSosCountdownActive = mutableStateOf(false)
    private var previousScreenBeforeSettings = mutableStateOf(Screen.HOME)

    private fun saveEmergencyContacts() {
        try {
            val jsonArray = JSONArray()
            emergencyContactsList.take(3).forEach { contact ->
                val obj = JSONObject()
                obj.put("name", contact.name)
                obj.put("phone", contact.phone)
                jsonArray.put(obj)
            }
            val sp = getSharedPreferences("drivedash_prefs", MODE_PRIVATE)
            sp.edit().putString("setting_emergency_contacts_json", jsonArray.toString()).apply()
            settingEmergencyContactName.value = emergencyContactsList.firstOrNull()?.name ?: ""
            settingEmergencyContactPhone.value = emergencyContactsList.firstOrNull()?.phone ?: ""
        } catch (_: Exception) {}
    }

    // 7. Mic Sensitivity / Push-to-Talk
    private var settingVoiceMode = mutableStateOf(VoiceTransmissionMode.OPEN_MIC)

    // 8. Push Notification Preferences
    private var settingNotifySeparation = mutableStateOf(true)
    private var settingNotifySos = mutableStateOf(true)
    private var settingNotifyRoomEvents = mutableStateOf(true)

    // 9. Profile & Bike/Car Info
    private var settingVehicleType = mutableStateOf("Motorcycle")
    private var settingVehicleModel = mutableStateOf("")

    private fun formatDisplaySpeed(speedKmh: Double): String {
        return if (settingUnitSystem.value == UnitSystem.IMPERIAL) {
            val mph = speedKmh * 0.621371
            "%.1f mph".format(mph)
        } else {
            "%.1f km/h".format(speedKmh)
        }
    }

    private fun formatDisplayDistance(distanceKm: Double): String {
        return if (settingUnitSystem.value == UnitSystem.IMPERIAL) {
            val miles = distanceKm * 0.621371
            if (miles >= 10.0) "%.1f mi".format(miles) else "%.2f mi".format(miles)
        } else {
            if (distanceKm >= 10.0) "%.1f km".format(distanceKm) else "%.2f km".format(distanceKm)
        }
    }

    private fun formatDisplayMeters(meters: Float): String {
        return if (settingUnitSystem.value == UnitSystem.IMPERIAL) {
            val feet = (meters * 3.28084f).toInt()
            "$feet ft"
        } else {
            "${meters.toInt()}m"
        }
    }

    private fun getVehicleIcon(type: String): String {
        return when (type.lowercase()) {
            "car" -> "🚗"
            "scooter" -> "🛵"
            "bicycle" -> "🚲"
            else -> "🏍️"
        }
    }
    // Each active room has its own unique cartoon character QR face automatically set by the app from the provided list
    private var currentRoomQrCharacterId = mutableStateOf("tom")
    private var lastUsedCharacterIndex = Random.nextInt(31)

    private fun pickNextRoomCharacterId(): String {
        if (cartoonCharactersList.isEmpty()) return "doraemon"
        // Cycle to a different character for every newly created room
        lastUsedCharacterIndex = (lastUsedCharacterIndex + 1) % cartoonCharactersList.size
        return cartoonCharactersList[lastUsedCharacterIndex].id
    }

    data class CartoonCharacter(
        val id: String,
        val name: String,
        val show: String
    )

    // Strictly the 31 characters across the 18 shows requested
    val cartoonCharactersList = listOf(
        // Tom and Jerry: Tom, Jerry
        CartoonCharacter("tom", "Tom", "Tom and Jerry"),
        CartoonCharacter("jerry", "Jerry", "Tom and Jerry"),
        // SpongeBob SquarePants: SpongeBob SquarePants
        CartoonCharacter("spongebob", "SpongeBob SquarePants", "SpongeBob SquarePants"),
        // The Simpsons: Homer, Marge, Bart, Lisa, Maggie
        CartoonCharacter("homer", "Homer", "The Simpsons"),
        CartoonCharacter("marge", "Marge", "The Simpsons"),
        CartoonCharacter("bart", "Bart", "The Simpsons"),
        CartoonCharacter("lisa", "Lisa", "The Simpsons"),
        CartoonCharacter("maggie", "Maggie", "The Simpsons"),
        // Looney Tunes: Bugs Bunny, Daffy Duck
        CartoonCharacter("bugs_bunny", "Bugs Bunny", "Looney Tunes"),
        CartoonCharacter("daffy_duck", "Daffy Duck", "Looney Tunes"),
        // Scooby-Doo, Where Are You!: Scooby-Doo, Shaggy Rogers
        CartoonCharacter("scoobydoo", "Scooby-Doo", "Scooby-Doo, Where Are You!"),
        CartoonCharacter("shaggy", "Shaggy Rogers", "Scooby-Doo, Where Are You!"),
        // Pokémon: Ash Ketchum, Pikachu
        CartoonCharacter("ash", "Ash Ketchum", "Pokémon"),
        CartoonCharacter("pikachu", "Pikachu", "Pokémon"),
        // Dragon Ball Z: Goku
        CartoonCharacter("goku", "Goku", "Dragon Ball Z"),
        // Doraemon: Doraemon, Nobita Nobi
        CartoonCharacter("doraemon", "Doraemon", "Doraemon"),
        CartoonCharacter("nobita", "Nobita Nobi", "Doraemon"),
        // Naruto: Naruto Uzumaki
        CartoonCharacter("naruto", "Naruto Uzumaki", "Naruto"),
        // Shinchan: Shinnosuke "Shinchan" Nohara
        CartoonCharacter("shinchan", "Shinnosuke \"Shinchan\" Nohara", "Shinchan"),
        // Ninja Hattori: Kanzo Hattori, Kenichi Mitsuba
        CartoonCharacter("hattori", "Kanzo Hattori", "Ninja Hattori"),
        CartoonCharacter("kenichi", "Kenichi Mitsuba", "Ninja Hattori"),
        // Shaun the Sheep: Shaun
        CartoonCharacter("shaun", "Shaun", "Shaun the Sheep"),
        // Oggy and the Cockroaches: Oggy
        CartoonCharacter("oggy", "Oggy", "Oggy and the Cockroaches"),
        // The Adventures of Tintin: Tintin, Snowy
        CartoonCharacter("tintin", "Tintin", "The Adventures of Tintin"),
        CartoonCharacter("snowy", "Snowy", "The Adventures of Tintin"),
        // Ben 10: Ben Tennyson
        CartoonCharacter("ben10", "Ben Tennyson", "Ben 10"),
        // Phineas and Ferb: Phineas Flynn, Ferb Fletcher
        CartoonCharacter("phineas", "Phineas Flynn", "Phineas and Ferb"),
        CartoonCharacter("ferb", "Ferb Fletcher", "Phineas and Ferb"),
        // Gravity Falls: Dipper Pines, Mabel Pines
        CartoonCharacter("dipper", "Dipper Pines", "Gravity Falls"),
        CartoonCharacter("mabel", "Mabel Pines", "Gravity Falls"),
        // Mr. Bean: The Animated Series: Mr. Bean
        CartoonCharacter("mr_bean", "Mr. Bean", "Mr. Bean: The Animated Series")
    )

    private fun encodePoints(points: List<LatLng>): String {
        return points.joinToString(";") { "${"%.5f".format(java.util.Locale.US, it.latitude)},${"%.5f".format(java.util.Locale.US, it.longitude)}" }
    }

    private fun decodePoints(str: String): List<LatLng> {
        if (str.isBlank()) return emptyList()
        return str.split(";").mapNotNull { part ->
            val sub = part.split(",")
            if (sub.size == 2) {
                val lat = sub[0].toDoubleOrNull()
                val lng = sub[1].toDoubleOrNull()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            } else null
        }
    }

    private fun tripToJson(stats: TripStats): JSONObject {
        val obj = JSONObject()
        obj.put("id", stats.id)
        obj.put("roomCode", stats.roomCode)
        obj.put("totalDistanceKm", stats.totalDistanceKm)
        obj.put("durationMillis", stats.durationMillis)
        obj.put("averageSpeedKmh", stats.averageSpeedKmh)
        obj.put("topSpeedKmh", stats.topSpeedKmh)
        obj.put("timestamp", stats.timestamp)
        obj.put("routePolyline", stats.routePolyline)
        obj.put("isFavorite", stats.isFavorite)
        obj.put("ridersCount", stats.ridersCount)
        return obj
    }

    private fun tripFromJson(obj: JSONObject): TripStats {
        return TripStats(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            roomCode = obj.optString("roomCode", ""),
            totalDistanceKm = obj.optDouble("totalDistanceKm", 0.0),
            durationMillis = obj.optLong("durationMillis", 0L),
            averageSpeedKmh = obj.optDouble("averageSpeedKmh", 0.0),
            topSpeedKmh = obj.optDouble("topSpeedKmh", 0.0),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            routePolyline = obj.optString("routePolyline", ""),
            isFavorite = obj.optBoolean("isFavorite", false),
            ridersCount = obj.optInt("ridersCount", 0)
        )
    }

    private fun persistTripsHistory() {
        try {
            val arr = JSONArray()
            for (trip in savedTripsHistory) {
                arr.put(tripToJson(trip))
            }
            getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
                .putString("saved_trips_history_json", arr.toString())
                .apply()
        } catch (_: Exception) {}
        savedLastTripStats.value = savedTripsHistory.firstOrNull()
    }

    private fun saveCompletedTrip(stats: TripStats) {
        savedTripsHistory.removeAll { it.id == stats.id }
        savedTripsHistory.add(0, stats)
        persistTripsHistory()
    }

    private fun toggleFavoriteTrip(tripId: String) {
        val idx = savedTripsHistory.indexOfFirst { it.id == tripId }
        if (idx != -1) {
            val current = savedTripsHistory[idx]
            savedTripsHistory[idx] = current.copy(isFavorite = !current.isFavorite)
            persistTripsHistory()
        }
    }

    private fun deleteTripFromHistory(tripId: String) {
        savedTripsHistory.removeAll { it.id == tripId }
        persistTripsHistory()
    }

    private fun clearSavedTrip() {
        savedTripsHistory.clear()
        savedLastTripStats.value = null
        getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
            .remove("saved_trips_history_json")
            .remove("saved_trip_room_code")
            .remove("saved_trip_distance_km")
            .remove("saved_trip_duration_ms")
            .remove("saved_trip_avg_speed")
            .remove("saved_trip_top_speed")
            .remove("saved_trip_timestamp")
            .apply()
    }

    private fun loadSavedTrip() {
        savedTripsHistory.clear()
        val prefs = getSharedPreferences("drivedash_prefs", MODE_PRIVATE)
        val historyJson = prefs.getString("saved_trips_history_json", null)
        if (!historyJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(historyJson)
                for (i in 0 until arr.length()) {
                    savedTripsHistory.add(tripFromJson(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {}
        }
        if (savedTripsHistory.isEmpty()) {
            val roomCode = prefs.getString("saved_trip_room_code", null)
            if (!roomCode.isNullOrBlank()) {
                val dist = prefs.getFloat("saved_trip_distance_km", 0f).toDouble()
                val duration = prefs.getLong("saved_trip_duration_ms", 0L)
                val avg = prefs.getFloat("saved_trip_avg_speed", 0f).toDouble()
                val top = prefs.getFloat("saved_trip_top_speed", 0f).toDouble()
                val ts = prefs.getLong("saved_trip_timestamp", 0L)
                val legacy = TripStats(
                    roomCode = roomCode,
                    totalDistanceKm = dist,
                    durationMillis = duration,
                    averageSpeedKmh = avg,
                    topSpeedKmh = top,
                    timestamp = if (ts > 0L) ts else System.currentTimeMillis()
                )
                savedTripsHistory.add(legacy)
                persistTripsHistory()
            }
        }
        savedLastTripStats.value = savedTripsHistory.firstOrNull()
    }

    private fun formatRideDuration(durationMillis: Long): String {
        val totalSec = (durationMillis / 1000).coerceAtLeast(0)
        val hrs = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return when {
            hrs > 0 -> "%d hr %d min %d sec".format(hrs, mins, secs)
            mins > 0 -> "%d min %02d sec".format(mins, secs)
            else -> "%d sec".format(secs)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("drivedash_prefs", MODE_PRIVATE)
        settingSeparationDistanceMeters.value = prefs.getFloat("setting_separation_dist", 500f)
        settingVoiceAutoJoin.value = prefs.getBoolean("setting_voice_auto_join", true)

        val unitStr = prefs.getString("setting_unit_system", UnitSystem.METRIC.name) ?: UnitSystem.METRIC.name
        settingUnitSystem.value = try { UnitSystem.valueOf(unitStr) } catch (_: Exception) { UnitSystem.METRIC }

        val orientStr = prefs.getString("setting_map_orientation", MapOrientation.HEADING_UP.name) ?: MapOrientation.HEADING_UP.name
        settingMapOrientation.value = try { MapOrientation.valueOf(orientStr) } catch (_: Exception) { MapOrientation.HEADING_UP }

        val perspStr = prefs.getString("setting_map_perspective", MapPerspective.VIEW_3D.name) ?: MapPerspective.VIEW_3D.name
        settingMapPerspective.value = try { MapPerspective.valueOf(perspStr) } catch (_: Exception) { MapPerspective.VIEW_3D }

        settingMapType.value = prefs.getString("setting_map_type", "NORMAL") ?: "NORMAL"
        settingTrafficEnabled.value = prefs.getBoolean("setting_traffic_enabled", true)
        settingAutoRecenter.value = prefs.getBoolean("setting_auto_recenter", true)

        settingSpeedWarningEnabled.value = prefs.getBoolean("setting_speed_warning_enabled", false)
        settingSpeedLimitKmh.value = prefs.getFloat("setting_speed_limit_kmh", 80f)
        settingSpeedChimeEnabled.value = prefs.getBoolean("setting_speed_chime_enabled", true)

        val contactsJson = prefs.getString("setting_emergency_contacts_json", null)
        emergencyContactsList.clear()
        if (!contactsJson.isNullOrBlank()) {
            try {
                val array = JSONArray(contactsJson)
                for (i in 0 until minOf(array.length(), 3)) {
                    val obj = array.getJSONObject(i)
                    val cName = obj.optString("name", "")
                    val cPhone = obj.optString("phone", "")
                    if (cName.isNotBlank() || cPhone.isNotBlank()) {
                        emergencyContactsList.add(EmergencyContact(cName, cPhone))
                    }
                }
            } catch (_: Exception) {}
        } else {
            val oldName = prefs.getString("setting_emergency_contact_name", "") ?: ""
            val oldPhone = prefs.getString("setting_emergency_contact_phone", "") ?: ""
            if (oldName.isNotBlank() || oldPhone.isNotBlank()) {
                emergencyContactsList.add(EmergencyContact(oldName, oldPhone))
            }
        }
        settingEmergencyContactName.value = emergencyContactsList.firstOrNull()?.name ?: ""
        settingEmergencyContactPhone.value = emergencyContactsList.firstOrNull()?.phone ?: ""
        settingSosCountdownSeconds.value = prefs.getInt("setting_sos_countdown_sec", 5)

        val voiceModeStr = prefs.getString("setting_voice_mode", VoiceTransmissionMode.OPEN_MIC.name) ?: VoiceTransmissionMode.OPEN_MIC.name
        settingVoiceMode.value = try { VoiceTransmissionMode.valueOf(voiceModeStr) } catch (_: Exception) { VoiceTransmissionMode.OPEN_MIC }

        settingNotifySeparation.value = prefs.getBoolean("setting_notify_separation", true)
        settingNotifySos.value = prefs.getBoolean("setting_notify_sos", true)
        settingNotifyRoomEvents.value = prefs.getBoolean("setting_notify_room_events", true)

        settingVehicleType.value = prefs.getString("setting_vehicle_type", "Motorcycle") ?: "Motorcycle"
        settingVehicleModel.value = prefs.getString("setting_vehicle_model", "") ?: ""

        val themeStr = prefs.getString("setting_theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        appThemeMode.value = try {
            AppThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }

        val modeStr = prefs.getString("setting_ride_mode", RideMode.MAP_VOICE.name) ?: RideMode.MAP_VOICE.name
        rideMode.value = try {
            RideMode.valueOf(modeStr)
        } catch (_: Exception) {
            RideMode.MAP_VOICE
        }

        settingVoiceOverlayEnabled.value = prefs.getBoolean("setting_voice_overlay_enabled", true)
        settingVoiceOverlayOnlySpeaking.value = prefs.getBoolean("setting_voice_overlay_only_speaking", false)

        // Restore last known GPS coordinates so the map doesn't start at (0,0) / Africa
        val savedLat = prefs.getString("last_known_lat", null)?.toDoubleOrNull()
        val savedLng = prefs.getString("last_known_lng", null)?.toDoubleOrNull()
        if (savedLat != null && savedLng != null && savedLat != 0.0 && savedLng != 0.0) {
            if (currentLat.value == 0.0 && currentLng.value == 0.0) {
                currentLat.value = savedLat
                currentLng.value = savedLng
            }
        }
    }

    private fun saveLastKnownLocation(lat: Double, lng: Double) {
        if (lat != 0.0 && lng != 0.0) {
            getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
                .putString("last_known_lat", lat.toString())
                .putString("last_known_lng", lng.toString())
                .apply()
        }
    }

    private fun saveSetting(key: String, value: Any) {
        val editor = getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
        }
        editor.apply()
    }

    private fun saveSettingTheme(mode: AppThemeMode) {
        appThemeMode.value = mode
        saveSetting("setting_theme_mode", mode.name)
    }

    private fun saveSettingSeparation(dist: Float) {
        settingSeparationDistanceMeters.value = dist
        saveSetting("setting_separation_dist", dist)
    }

    private fun saveSettingVoiceAutoJoin(enabled: Boolean) {
        settingVoiceAutoJoin.value = enabled
        saveSetting("setting_voice_auto_join", enabled)
    }

    // the ride mode picked on the Home screen, applied when the room is created/joined
    private var rideMode = mutableStateOf(RideMode.MAP_VOICE)

    // the last room the user manually typed a code for and then left — Home offers this as a
    // one-tap "Recent Room" rejoin option instead of making them re-type the code. Saved at
    // leave-time (see leaveRoom()), and cleared if it's later found to be expired/invalid/banned.
    private var lastActiveRoomCode = mutableStateOf<String?>(null)
    private var lastActiveRoomExpiresAt = mutableStateOf(0L)

    // true only while the user is in a room they got to by typing a code in (JoinRoomScreen,
    // including via the Recent Room button itself) — false for a room they created as host.
    // Read at leave-time to decide whether this session's code should become the new Recent Room.
    private var joinedViaManualEntry = mutableStateOf(false)

    private fun saveLastActiveRoom(code: String, expiresAt: Long) {
        lastActiveRoomCode.value = code
        lastActiveRoomExpiresAt.value = expiresAt
        getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
            .putString("last_room_code", code)
            .putLong("last_room_expires_at", expiresAt)
            .apply()
    }

    private fun clearLastActiveRoom() {
        lastActiveRoomCode.value = null
        lastActiveRoomExpiresAt.value = 0L
        getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
            .remove("last_room_code")
            .remove("last_room_expires_at")
            .apply()
    }

    // reads the same Maps API key already stored in AndroidManifest.xml, so we don't repeat it
    private val mapsApiKey: String by lazy {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentLat.value = location.latitude
            currentLng.value = location.longitude
            currentSpeed.value = location.speed
            if (location.latitude != 0.0 && location.longitude != 0.0) {
                saveLastKnownLocation(location.latitude, location.longitude)
            }
            // GPS bearing is noisy/unreliable when barely moving (it can swing wildly while
            // parked or crawling), so only trust it above a small speed threshold; otherwise
            // just keep showing the last known heading instead of jittering
            if (location.hasBearing() && location.speed >= 1.0f) {
                currentBearing.value = location.bearing
            }

            // if we're inside a room, track trip telemetry and push our location to Firebase
            val roomCode = activeRoomCode.value
            val userId = auth.currentUser?.uid
            if (roomCode.isNotEmpty() && userId != null) {
                // Trip metrics accumulation
                if (location.speed > tripMaxSpeedMps) {
                    tripMaxSpeedMps = location.speed
                }
                lastRecordedLocation?.let { prevLoc ->
                    val deltaMeters = prevLoc.distanceTo(location)
                    // Only accumulate if distance is reasonable (> 2m to ignore GPS noise, < 2000m to ignore sudden GPS jumps)
                    if (deltaMeters in 2.0f..2000.0f) {
                        tripTotalDistanceMeters += deltaMeters
                    }
                }
                lastRecordedLocation = location

                // Collect breadcrumb points for the route map snippet
                val lastLoc = lastBreadcrumbLocation
                if (lastLoc == null || lastLoc.distanceTo(location) >= 8.0f) {
                    currentTripBreadcrumbs.add(LatLng(location.latitude, location.longitude))
                    lastBreadcrumbLocation = location
                }

                val userLocationRef = database.getReference("rooms/$roomCode/users/$userId")
                userLocationRef.child("lat").setValue(location.latitude)
                userLocationRef.child("lng").setValue(location.longitude)
                userLocationRef.child("speed").setValue(location.speed)
                userLocationRef.child("heading").setValue(currentBearing.value)
                userLocationRef.child("lastUpdate").setValue(System.currentTimeMillis())
            }
        }
    }

    private fun checkLocationServices() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPermission = hasFine || hasCoarse
        isLocationPermissionGranted.value = hasFine

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsOn = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        isLocationHardwareEnabled.value = isGpsOn

        if (hasPermission && isGpsOn) {
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationServices()
        micPermissionGranted.value = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        isLocationPermissionGranted.value = fineGranted
        if (fineGranted || coarseGranted) {
            checkLocationServices()
        } else {
            Toast.makeText(this, "Location permission is needed for DriveDash to work", Toast.LENGTH_LONG).show()
        }
    }

    private val micPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val recordGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        micPermissionGranted.value = recordGranted
        if (!recordGranted) {
            Toast.makeText(this, "Mic permission is needed for the room voice call", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureMicPermission() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasBt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasMic) {
            micPermissionGranted.value = true
        }

        val needed = mutableListOf<String>()
        if (!hasMic) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBt) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotif) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            micPermissionRequest.launch(needed.toTypedArray())
        }
    }

    // --- QR CODE GENERATION & SHARING ---
    fun loadCharacterFaceBitmap(charId: String): Bitmap? {
        return try {
            assets.open("characters/$charId.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun generateHalftoneQrCodeBitmap(
        content: String,
        charId: String = currentRoomQrCharacterId.value,
        sizePx: Int = 600
    ): Bitmap? {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 0

            val qrCode = Encoder.encode(content, ErrorCorrectionLevel.H, hints)
            val rawMatrix = qrCode.matrix
            val n = rawMatrix.width
            val k = 3
            val coreSize = n * k

            val validCharId = if (cartoonCharactersList.any { it.id == charId }) charId else cartoonCharactersList.first().id
            val faceRaw = loadCharacterFaceBitmap(validCharId)
            val faceScaled = if (faceRaw != null) {
                Bitmap.createScaledBitmap(faceRaw, coreSize, coreSize, true)
            } else null

            fun isFinderPattern(r: Int, c: Int): Boolean {
                if (r < 9 && c < 9) return true
                if (r < 9 && c >= n - 9) return true
                if (r >= n - 9 && c < 9) return true
                return false
            }

            val core = Array(coreSize) { BooleanArray(coreSize) }
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val isDark = rawMatrix.get(c, r).toInt() == 1
                    val isFinder = isFinderPattern(r, c)

                    for (dr in 0 until 3) {
                        for (dc in 0 until 3) {
                            val pr = r * 3 + dr
                            val pc = c * 3 + dc

                            if (isFinder) {
                                core[pr][pc] = isDark
                            } else if (dr == 1 && dc == 1) {
                                // Strictly preserve QR data bit at center sub-pixel for reliable scanning
                                core[pr][pc] = isDark
                            } else {
                                if (faceScaled != null) {
                                    val pixel = faceScaled.getPixel(pc, pr)
                                    val red = android.graphics.Color.red(pixel)
                                    val green = android.graphics.Color.green(pixel)
                                    val blue = android.graphics.Color.blue(pixel)
                                    val lum = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()

                                    val threshold = if (isDark) 110 else 145
                                    core[pr][pc] = lum < threshold
                                } else {
                                    core[pr][pc] = isDark
                                }
                            }
                        }
                    }
                }
            }

            val quietModules = 2
            val margin = quietModules * 3
            val totalSize = coreSize + 2 * margin
            val pixels = IntArray(totalSize * totalSize) { android.graphics.Color.WHITE }

            for (r in 0 until coreSize) {
                for (c in 0 until coreSize) {
                    if (core[r][c]) {
                        val idx = (r + margin) * totalSize + (c + margin)
                        pixels[idx] = android.graphics.Color.BLACK
                    }
                }
            }

            val unscaled = Bitmap.createBitmap(pixels, totalSize, totalSize, Bitmap.Config.ARGB_8888)
            Bitmap.createScaledBitmap(unscaled, sizePx, sizePx, false)
        } catch (_: Exception) {
            generateQrCodeBitmap(content, sizePx)
        }
    }

    private fun generateQrCodeBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRoomCodeFromScannedContent(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length == 6 && trimmed.all { it.isDigit() }) {
            return trimmed
        }
        try {
            val uri = android.net.Uri.parse(trimmed)
            val roomParam = uri.getQueryParameter("room") ?: uri.getQueryParameter("code")
            if (!roomParam.isNullOrBlank()) {
                val digits = roomParam.filter { it.isDigit() }
                if (digits.length == 6) return digits
            }
            val last = uri.lastPathSegment
            if (last != null) {
                val digits = last.filter { it.isDigit() }
                if (digits.length == 6) return digits
            }
        } catch (_: Exception) {}
        val regex = Regex("""\b\d{6}\b""")
        return regex.find(trimmed)?.value
    }

    private fun launchQrScanner(onCodeScanned: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue ?: barcode.displayValue
                if (!rawValue.isNullOrBlank()) {
                    val code = extractRoomCodeFromScannedContent(rawValue)
                    if (code != null) {
                        Toast.makeText(this, "Scanned Room: $code", Toast.LENGTH_SHORT).show()
                        onCodeScanned(code)
                    } else {
                        Toast.makeText(this, "Scanned QR code did not contain a 6-digit room code", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnCanceledListener {
                // User dismissed scanner
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "QR Scanner: ${e.message ?: "Camera unavailable"}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun shareRoomLink(roomCode: String) {
        val shareText = "Join my DriveDash convoy ride!\nRoom Code: $roomCode\nTap to join directly: https://drivedash.app/join?room=$roomCode"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Convoy Room"))
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val roomCode = data.getQueryParameter("room")
            ?: data.getQueryParameter("code")
            ?: (if (data.pathSegments.isNotEmpty()) data.lastPathSegment else null)
        if (!roomCode.isNullOrBlank()) {
            val cleanCode = roomCode.trim().filter { it.isDigit() }
            if (cleanCode.length == 6) {
                if (auth.currentUser != null) {
                    joinRoom(cleanCode)
                } else {
                    auth.signInAnonymously().addOnSuccessListener {
                        joinRoom(cleanCode)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // must happen before anything touches BitmapDescriptorFactory (e.g. our custom marker
        // icons) — otherwise it throws "IBitmapDescriptorFactory is not initialized"
        com.google.android.gms.maps.MapsInitializer.initialize(applicationContext)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        myUsername.value = getSharedPreferences("drivedash_prefs", MODE_PRIVATE).getString("username", "") ?: ""
        android.util.Log.d("DriveDash", "Current UID on launch: ${auth.currentUser?.uid}")

        // load persistent settings and last saved trip
        loadSettings()
        loadSavedTrip()

        // restore any room this device was recently in, so Home can offer Quick Rejoin.
        // If it's obviously past its own expiry, don't even bother showing it.
        val prefs = getSharedPreferences("drivedash_prefs", MODE_PRIVATE)
        val savedCode = prefs.getString("last_room_code", null)
        val savedExpiresAt = prefs.getLong("last_room_expires_at", 0L)
        if (!savedCode.isNullOrBlank() && System.currentTimeMillis() < savedExpiresAt) {
            lastActiveRoomCode.value = savedCode
            lastActiveRoomExpiresAt.value = savedExpiresAt
        } else if (!savedCode.isNullOrBlank()) {
            clearLastActiveRoom()
        }

        // sign the user in anonymously so they get a unique ID without needing to build a login/signup screen
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    android.util.Log.d("DriveDash", "Anonymous sign-in SUCCESS: ${it.user?.uid}")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("DriveDash", "Anonymous sign-in FAILED", e)
                }
        } else {
            android.util.Log.d("DriveDash", "Already signed in: ${auth.currentUser?.uid}")
        }

        checkLocationServices()
        if (!isLocationPermissionGranted.value) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        handleIncomingDeepLink(intent)

        setContent {
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (appThemeMode.value) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                var showLeaveConfirmationDialog by remember { mutableStateOf(false) }

                // Handle hardware/gesture back button navigation cleanly
                BackHandler(enabled = currentScreen.value != Screen.HOME) {
                    when (currentScreen.value) {
                        Screen.CREATE_ROOM -> {
                            currentScreen.value = Screen.HOME
                        }
                        Screen.JOIN_ROOM -> {
                            errorMessage.value = ""
                            currentScreen.value = Screen.HOME
                        }
                        Screen.IN_ROOM -> {
                            // Show confirmation dialog so active ride is not abandoned accidentally
                            showLeaveConfirmationDialog = true
                        }
                        Screen.TRIP_SUMMARY -> {
                            tripStartTime = 0L
                            tripTotalDistanceMeters = 0.0
                            tripMaxSpeedMps = 0f
                            lastRecordedLocation = null
                            completedTripStats.value = null
                            currentScreen.value = Screen.HOME
                        }
                        Screen.SETTINGS -> {
                            currentScreen.value = previousScreenBeforeSettings.value
                        }
                        Screen.MY_RIDES -> {
                            currentScreen.value = Screen.HOME
                        }
                        Screen.HOME -> {
                            // Default system behavior (exit app)
                        }
                    }
                }

                if (showLeaveConfirmationDialog) {
                    val isHost = auth.currentUser?.uid == roomHostId.value
                    AlertDialog(
                        onDismissRequest = { showLeaveConfirmationDialog = false },
                        title = {
                            Text(if (isHost) "End Trip & Leave Room?" else "Leave Convoy Room?")
                        },
                        text = {
                            Text(
                                if (isHost)
                                    "You are the host. Do you want to end or leave this convoy ride?"
                                else
                                    "Are you sure you want to exit the convoy? You can rejoin later using the room code."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showLeaveConfirmationDialog = false
                                    endOrLeaveTrip()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(if (isHost) "End / Leave" else "Leave Room")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showLeaveConfirmationDialog = false }
                            ) {
                                Text("Stay")
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    when (currentScreen.value) {
                        Screen.HOME -> HomeScreen()
                        Screen.CREATE_ROOM -> CreateRoomScreen()
                        Screen.JOIN_ROOM -> JoinRoomScreen()
                        Screen.IN_ROOM -> InRoomScreen()
                        Screen.TRIP_SUMMARY -> TripSummaryScreen()
                        Screen.SETTINGS -> SettingsScreen()
                        Screen.MY_RIDES -> MyRidesScreen()
                    }
                }
            }
        }
    }

    // ---------- SCREEN1: HOME ----------
    @Composable
    fun HomeScreen() {
        var nameField by remember { mutableStateOf(myUsername.value) }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top Header: App Title + Settings button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DriveDash", style = MaterialTheme.typography.headlineLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                FilledTonalButton(
                    onClick = {
                        previousScreenBeforeSettings.value = Screen.HOME
                        currentScreen.value = Screen.SETTINGS
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("⚙️ Settings", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }

            // --- LOCATION / GPS DISABLED WARNING ON HOME ---
            if (!isLocationPermissionGranted.value) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📍 Location Permission Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "DriveDash cannot share your position with the convoy without location access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB71C1C)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant Permission", fontSize = 12.sp)
                        }
                    }
                }
            } else if (!isLocationHardwareEnabled.value) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCC80)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🛰️ Device GPS is Turned Off",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Turn on your phone's location / GPS to see your car and route on the map.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBF360C)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                try {
                                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Turn On GPS", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = nameField,
                onValueChange = {
                    nameField = it
                    if (it.isNotBlank()) {
                        myUsername.value = it.trim()
                        getSharedPreferences("drivedash_prefs", MODE_PRIVATE).edit()
                            .putString("username", it.trim()).apply()
                    }
                },
                label = { Text("Your name") },
                singleLine = true
            )
            Text(
                "Other riders will see this name in the room",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))
            Text("Ride Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            RideModeSelector(
                selected = rideMode.value,
                onSelect = {
                    rideMode.value = it
                    saveSetting("setting_ride_mode", it.name)
                }
            )

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = { currentScreen.value = Screen.CREATE_ROOM },
                enabled = nameField.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Room")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { currentScreen.value = Screen.JOIN_ROOM },
                enabled = nameField.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Join Room")
            }

            // --- SAVED LAST TRIP DETAILS / MY RIDES SECTION ---
            if (savedLastTripStats.value != null || savedTripsHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Last Saved Ride",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { currentScreen.value = Screen.MY_RIDES },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "My Rides (${savedTripsHistory.size}) →",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                savedLastTripStats.value?.let { savedTrip ->
                    RideHistoryCard(
                        stats = savedTrip,
                        onToggleFavorite = { toggleFavoriteTrip(it) },
                        onDelete = {
                            deleteTripFromHistory(it)
                            Toast.makeText(this@MainActivity, "Ride deleted", Toast.LENGTH_SHORT).show()
                        },
                        onViewSummary = {
                            completedTripStats.value = it
                            currentScreen.value = Screen.TRIP_SUMMARY
                        }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { currentScreen.value = Screen.MY_RIDES },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📍 My Rides")
                }
            }
        }
    }

    // ---------- SCREEN 7: MY RIDES & FAVORITES ----------
    @Composable
    fun MyRidesScreen() {
        var selectedTab by remember { mutableStateOf(0) } // 0: Rides, 1: Favorites
        var searchQuery by remember { mutableStateOf("") }

        val filteredList = remember(savedTripsHistory.size, selectedTab, searchQuery, savedTripsHistory.toList()) {
            savedTripsHistory.filter { trip ->
                val matchesTab = if (selectedTab == 1) trip.isFavorite else true
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    trip.roomCode.contains(searchQuery, ignoreCase = true) ||
                    java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(trip.timestamp)).contains(searchQuery, ignoreCase = true) ||
                    "%.1f".format(trip.totalDistanceKm).contains(searchQuery)
                }
                matchesTab && matchesSearch
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentScreen.value = Screen.HOME }) {
                    Text("←", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "My Rides",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            // Tabs: Rides / Favorites
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Rides (${savedTripsHistory.size})",
                            fontWeight = if (selectedTab == 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Favorites (${savedTripsHistory.count { it.isFavorite }})",
                            fontWeight = if (selectedTab == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search for a ride...") },
                leadingIcon = { Text("🔍", fontSize = 14.sp) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Text("✕", fontSize = 14.sp)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Rides list
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedTab == 1) "❤️" else "🏍️", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 1) "No favorite rides yet" else "No saved rides found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Rides you take will show here with route maps and stats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredList, key = { it.id }) { trip ->
                        RideHistoryCard(
                            stats = trip,
                            onToggleFavorite = { toggleFavoriteTrip(it) },
                            onDelete = {
                                deleteTripFromHistory(it)
                                Toast.makeText(this@MainActivity, "Ride deleted", Toast.LENGTH_SHORT).show()
                            },
                            onViewSummary = {
                                completedTripStats.value = it
                                currentScreen.value = Screen.TRIP_SUMMARY
                            }
                        )
                    }
                }
            }
        }
    }

    // ---------- COMPONENT: RIDE HISTORY CARD (REFERENCE DESIGN) ----------
    @Composable
    fun RideHistoryCard(
        stats: TripStats,
        onToggleFavorite: (String) -> Unit,
        onDelete: (String) -> Unit,
        onViewSummary: (TripStats) -> Unit
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        val points = remember(stats.routePolyline) { decodePoints(stats.routePolyline) }

        val titleTime = remember(stats.timestamp) {
            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(stats.timestamp)).uppercase()
        }
        val subtitle = remember(stats.timestamp) {
            val t = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(stats.timestamp)).lowercase()
            val d = java.text.SimpleDateFormat("EEE dd MMM", java.util.Locale.getDefault()).format(java.util.Date(stats.timestamp))
            "$t | $d"
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left vertical accent strip
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ride $titleTime",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Favorite Button
                    IconButton(
                        onClick = { onToggleFavorite(stats.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = if (stats.isFavorite) "❤️" else "🤍",
                            fontSize = 18.sp
                        )
                    }

                    // 3-dots Menu Button
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("⋮", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Full Summary") },
                                onClick = {
                                    menuExpanded = false
                                    onViewSummary(stats)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Ride", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete(stats.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Route Map snippet
                if (points.isNotEmpty()) {
                    val cameraPositionState = rememberCameraPositionState()
                    LaunchedEffect(points) {
                        if (points.size >= 2) {
                            try {
                                val builder = LatLngBounds.builder()
                                points.forEach { builder.include(it) }
                                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 40))
                            } catch (_: Exception) {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 14f)
                            }
                        } else {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 14f)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false,
                                tiltGesturesEnabled = false,
                                rotationGesturesEnabled = false,
                                mapToolbarEnabled = false,
                                myLocationButtonEnabled = false
                            ),
                            onMapLoaded = {
                                if (points.size >= 2) {
                                    try {
                                        val builder = LatLngBounds.builder()
                                        points.forEach { builder.include(it) }
                                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 40))
                                    } catch (_: Exception) {}
                                }
                            }
                        ) {
                            Polyline(
                                points = points,
                                color = Color(0xFF1E88E5),
                                width = 8f
                            )
                            MarkerComposable(
                                keys = arrayOf("start", stats.id),
                                state = rememberMarkerState(position = points.first())
                            ) {
                                Surface(
                                    color = Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(8.dp),
                                    shadowElevation = 3.dp,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text(
                                        "START",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (points.size > 1) {
                                MarkerComposable(
                                    keys = arrayOf("end", stats.id),
                                    state = rememberMarkerState(position = points.last())
                                ) {
                                    Surface(
                                        color = Color(0xFFC62828),
                                        shape = RoundedCornerShape(8.dp),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            "END",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛣️", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Route: ${if (stats.roomCode.isNotBlank()) "Room " + stats.roomCode else "Convoy Trip"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom 3-column telemetry bar matching reference image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Distance
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Distance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = formatDisplayDistance(stats.totalDistanceKm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider 1
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Column 2: Ride Duration
                    Column(
                        modifier = Modifier.weight(1.3f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏱️", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Ride Duration",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = formatRideDuration(stats.durationMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider 2
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Column 3: Riders Joined
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏍️", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Riders",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${stats.ridersCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // compact 3-way toggle: Voice Only / Map Only / Map + Voice
    @Composable
    fun RideModeSelector(selected: RideMode, onSelect: (RideMode) -> Unit) {
        val options = listOf(
            Triple(RideMode.VOICE_ONLY, "🎤 Voice Only", "Audio call, no map (battery saver)"),
            Triple(RideMode.MAP_ONLY, "🗺 Map Only", "Live location & route, no voice"),
            Triple(RideMode.MAP_VOICE, "🏍️ Map + Voice", "Full ride experience")
        )
        Column(modifier = Modifier.widthIn(max = 320.dp)) {
            options.forEach { (mode, label, description) ->
                val isSelected = mode == selected
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelect(mode) })
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // ---------- SCREEN2: CREATE ROOM ----------
    @Composable
    fun CreateRoomScreen() {
        var selectedHours by remember { mutableStateOf(1) }
        var selectedMinutes by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Set Room Duration", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "How long should this convoy room stay active?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Large Digital Timer Display
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%02d".format(selectedHours),
                            fontSize = 44.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("HOURS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Text(
                        text = ":",
                        fontSize = 40.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%02d".format(selectedMinutes),
                            fontSize = 44.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("MINUTES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Precise Increment / Decrement Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Hours Stepper
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hours", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { if (selectedHours > 0) selectedHours-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("-", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = { if (selectedHours < 23) selectedHours++ },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }

                // Minutes Stepper
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Minutes", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                if (selectedMinutes >= 5) selectedMinutes -= 5
                                else if (selectedHours > 0) {
                                    selectedHours--
                                    selectedMinutes = 55
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("-5", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = {
                                if (selectedMinutes <= 50) selectedMinutes += 5
                                else if (selectedHours < 23) {
                                    selectedHours++
                                    selectedMinutes = 0
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+5", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Preset Buttons
            Text("Quick Presets", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = { selectedHours = 0; selectedMinutes = 30 },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("30m")
                }
                OutlinedButton(
                    onClick = { selectedHours = 1; selectedMinutes = 0 },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("1h")
                }
                OutlinedButton(
                    onClick = { selectedHours = 2; selectedMinutes = 0 },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("2h")
                }
                OutlinedButton(
                    onClick = { selectedHours = 4; selectedMinutes = 0 },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("4h")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val totalMinutes = (selectedHours * 60) + selectedMinutes
                    if (totalMinutes > 0) {
                        createRoom(totalMinutes)
                    } else {
                        Toast.makeText(this@MainActivity, "Duration must be at least 1 minute", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Room", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { currentScreen.value = Screen.HOME }) {
                Text("Cancel")
            }
        }
    }

    //generates 6-digit code, saves it to Firebase with an expiry time
    private fun createRoom(durationMinutes: Int) {
        val hostId = auth.currentUser?.uid
        if (hostId == null) {
            Toast.makeText(this, "Still connecting... please wait a moment and try again", Toast.LENGTH_LONG).show()
            android.util.Log.d("DriveDash", "createRoom blocked - auth not ready yet")
            return
        }

        val roomCode = Random.nextInt(100000, 999999).toString()
        val expiresAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)

        // Pick a different cartoon character from the list for every newly created room
        val chosenCharId = pickNextRoomCharacterId()
        currentRoomQrCharacterId.value = chosenCharId

        val roomRef = database.getReference("rooms/$roomCode")
        val roomData = mapOf(
            "expiresAt" to expiresAt,
            "hostId" to hostId,
            "qrCharacter" to chosenCharId
        )
        roomRef.updateChildren(roomData)
            .addOnSuccessListener {
                saveLastActiveRoom(roomCode, expiresAt)
                activeRoomCode.value = roomCode
                currentScreen.value = Screen.IN_ROOM
                joinedViaManualEntry.value = true
                Toast.makeText(this, "Room created: $roomCode", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create room: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------- SCREEN3: JOIN ROOM ----------
    @Composable
    fun JoinRoomScreen() {
        var enteredCode by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Enter Room Code", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = { enteredCode = it },
                label = { Text("6-digit code") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    launchQrScanner { scannedCode ->
                        enteredCode = scannedCode
                        joinRoom(scannedCode)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📷 Scan QR Code to Join", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (errorMessage.value.isNotEmpty()) {
                Text(errorMessage.value, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = { joinRoom(enteredCode) },
                enabled = enteredCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Join Room")
            }

            // Prominent Last Joined Room card for accidental leaves
            if (lastActiveRoomCode.value != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚡ Last Joined Room",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Room ${lastActiveRoomCode.value}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Left by mistake? Rejoin immediately without entering code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { joinRoom(lastActiveRoomCode.value!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Rejoin ${lastActiveRoomCode.value}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = {
                errorMessage.value = ""
                currentScreen.value = Screen.HOME
            }) {
                Text("Back")
            }
        }
    }

    // ---------- SCREEN 5: TRIP SUMMARY & REPORT CARD ----------
    @Composable
    fun TripSummaryScreen() {
        val stats = completedTripStats.value
        val durationMins = if (stats != null) stats.durationMillis / 60000 else 0
        val durationSecs = if (stats != null) (stats.durationMillis / 1000) % 60 else 0
        val routePoints = remember(stats?.routePolyline) {
            if (stats?.routePolyline.isNullOrEmpty()) emptyList()
            else decodePoints(stats!!.routePolyline)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🏁 Trip Summary", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Convoy ride complete! Here is your ride report:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // Route Map
            if (routePoints.isNotEmpty()) {
                val cameraPositionState = rememberCameraPositionState()
                LaunchedEffect(routePoints) {
                    if (routePoints.size >= 2) {
                        try {
                            val builder = LatLngBounds.builder()
                            routePoints.forEach { builder.include(it) }
                            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 48))
                        } catch (_: Exception) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(routePoints.first(), 13f)
                        }
                    } else {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(routePoints.first(), 14f)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            mapToolbarEnabled = false,
                            myLocationButtonEnabled = false
                        ),
                        onMapLoaded = {
                            if (routePoints.size >= 2) {
                                try {
                                    val builder = LatLngBounds.builder()
                                    routePoints.forEach { builder.include(it) }
                                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 48))
                                } catch (_: Exception) {}
                            }
                        }
                    ) {
                        Polyline(points = routePoints, color = Color(0xFF1E88E5), width = 9f)
                        MarkerComposable(
                            keys = arrayOf("start"),
                            state = rememberMarkerState(position = routePoints.first())
                        ) {
                            Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(8.dp), shadowElevation = 3.dp, modifier = Modifier.padding(2.dp)) {
                                Text("START", color = Color.White, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        if (routePoints.size > 1) {
                            MarkerComposable(
                                keys = arrayOf("end"),
                                state = rememberMarkerState(position = routePoints.last())
                            ) {
                                Surface(color = Color(0xFFC62828), shape = RoundedCornerShape(8.dp), shadowElevation = 3.dp, modifier = Modifier.padding(2.dp)) {
                                    Text("END", color = Color.White, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Trip Details", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Room Code:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stats?.roomCode?.ifBlank { "N/A" } ?: "N/A", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Distance:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDisplayDistance(stats?.totalDistanceKm ?: 0.0), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Trip Duration:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%dm %02ds".format(durationMins, durationSecs), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Average Speed:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDisplaySpeed(stats?.averageSpeedKmh ?: 0.0), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Riders Joined:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${stats?.ridersCount ?: 0} rider${if ((stats?.ridersCount ?: 0) != 1) "s" else ""}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    tripStartTime = 0L
                    tripTotalDistanceMeters = 0.0
                    tripMaxSpeedMps = 0f
                    lastRecordedLocation = null
                    completedTripStats.value = null
                    currentScreen.value = Screen.HOME
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Home")
            }

        }
    }

    // ---------- SCREEN 6: SETTINGS ----------
    @Composable
    fun SettingsScreen() {
        var distanceSlider by remember { mutableStateOf(settingSeparationDistanceMeters.value) }
        var showAddContactDialog by remember { mutableStateOf(false) }
        var newContactName by remember { mutableStateOf("") }
        var newContactPhone by remember { mutableStateOf("") }
        var contactDialogError by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentScreen.value = previousScreenBeforeSettings.value }) {
                    Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚙️ Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ================= SECTION 1: RIDE MODE =================
            Text("🛵 Ride Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Switch mode anytime — active instantly during a ride without disconnecting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val modeOptions = listOf(
                        Triple(RideMode.MAP_VOICE, "🏍️ Map + Voice", "Full convoy experience with live GPS map and mesh voice intercom"),
                        Triple(RideMode.MAP_ONLY, "🗺️ Map Only", "Live location & navigation on map, voice audio turned off"),
                        Triple(RideMode.VOICE_ONLY, "🎤 Audio Only / Voice Only", "Hands-free voice intercom; map disabled for maximum battery saving")
                    )

                    modeOptions.forEach { (mode, label, desc) ->
                        val isSelected = rideMode.value == mode
                        Surface(
                            onClick = {
                                rideMode.value = mode
                                saveSetting("setting_ride_mode", mode.name)
                                if (mode != RideMode.MAP_ONLY && settingVoiceAutoJoin.value) {
                                    ensureMicPermission()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        rideMode.value = mode
                                        saveSetting("setting_ride_mode", mode.name)
                                        if (mode != RideMode.MAP_ONLY && settingVoiceAutoJoin.value) {
                                            ensureMicPermission()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION: DISCORD VOICE OVERLAY ON MAP =================
            Text("🎮 Discord In-Map Voice Overlay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Lightweight HUD directly on top of the live map with green speaking rings, mute status, and draggable placement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Master Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Enable Voice Overlay on Map", fontWeight = FontWeight.Medium)
                            Text("Show floating Discord-style HUD on the Google Map during convoy rides", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingVoiceOverlayEnabled.value,
                            onCheckedChange = {
                                settingVoiceOverlayEnabled.value = it
                                saveSetting("setting_voice_overlay_enabled", it)
                            }
                        )
                    }

                    if (settingVoiceOverlayEnabled.value) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Speaker Filter Mode", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayOptions = listOf(
                            Pair(false, "👥 Always Show All Riders in Call"),
                            Pair(true, "🗣️ Only Show While Speaking")
                        )

                        displayOptions.forEach { (onlySpeaking, label) ->
                            val isSelected = settingVoiceOverlayOnlySpeaking.value == onlySpeaking
                            Surface(
                                onClick = {
                                    settingVoiceOverlayOnlySpeaking.value = onlySpeaking
                                    saveSetting("setting_voice_overlay_only_speaking", onlySpeaking)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            settingVoiceOverlayOnlySpeaking.value = onlySpeaking
                                            saveSetting("setting_voice_overlay_only_speaking", onlySpeaking)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("📏 Units of Measurement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Speed & Distance Standard", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))

                    val unitOptions = listOf(
                        Pair(UnitSystem.METRIC, "🌍 Metric (km/h, kilometers, meters)"),
                        Pair(UnitSystem.IMPERIAL, "🇺🇸 Imperial (mph, miles, feet)")
                    )

                    unitOptions.forEach { (unit, label) ->
                        val isSelected = settingUnitSystem.value == unit
                        Surface(
                            onClick = {
                                settingUnitSystem.value = unit
                                saveSetting("setting_unit_system", unit.name)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        settingUnitSystem.value = unit
                                        saveSetting("setting_unit_system", unit.name)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 3: MAP VIEW, ORIENTATION & LAYERS =================
            Text("🗺️ Map View & Navigation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Map Type Layers
                    Text("Map Layer / Style", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val mapTypes = listOf(
                        Pair("NORMAL", "🗺️ Standard"),
                        Pair("SATELLITE", "🛰️ Satellite"),
                        Pair("TERRAIN", "⛰️ Terrain"),
                        Pair("HYBRID", "🌐 Hybrid")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mapTypes.forEach { (mType, label) ->
                            val isSel = settingMapType.value == mType
                            Surface(
                                onClick = {
                                    settingMapType.value = mType
                                    saveSetting("setting_map_type", mType)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🚦 Live Traffic Overlay", fontWeight = FontWeight.Medium)
                            Text("Real-time green, orange, and red traffic lines on roads", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingTrafficEnabled.value,
                            onCheckedChange = {
                                settingTrafficEnabled.value = it
                                saveSetting("setting_traffic_enabled", it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Map Orientation: Heading Up vs North Up
                    Text("Camera Orientation", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))

                    val orientations = listOf(
                        Pair(MapOrientation.HEADING_UP, "🧭 Heading-Up (Rotates with vehicle direction)"),
                        Pair(MapOrientation.NORTH_UP, "⬆️ North-Up (Fixed north standard view)")
                    )

                    orientations.forEach { (orient, label) ->
                        val isSelected = settingMapOrientation.value == orient
                        Surface(
                            onClick = {
                                settingMapOrientation.value = orient
                                saveSetting("setting_map_orientation", orient.name)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        settingMapOrientation.value = orient
                                        saveSetting("setting_map_orientation", orient.name)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Perspective: 3D Driving vs 2D Flat
                    Text("Map Perspective", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))

                    val perspectives = listOf(
                        Pair(MapPerspective.VIEW_3D, "🏎️ 3D Driving View (Tilted 45° angle)"),
                        Pair(MapPerspective.VIEW_2D, "🗺️ 2D Flat View (Top-down overhead)")
                    )

                    perspectives.forEach { (persp, label) ->
                        val isSelected = settingMapPerspective.value == persp
                        Surface(
                            onClick = {
                                settingMapPerspective.value = persp
                                saveSetting("setting_map_perspective", persp.name)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        settingMapPerspective.value = persp
                                        saveSetting("setting_map_perspective", persp.name)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Auto-Recenter Camera Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Recenter Camera", fontWeight = FontWeight.Medium)
                            Text(
                                "Automatically snap map back to your vehicle 5 seconds after panning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingAutoRecenter.value,
                            onCheckedChange = {
                                settingAutoRecenter.value = it
                                saveSetting("setting_auto_recenter", it)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 4: SPEED LIMIT & SPEED WARNINGS =================
            Text("⚠️ Speed Limit & Warnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Speed Limit Warning", fontWeight = FontWeight.Medium)
                            Text(
                                "Alerts you visually and audibly when driving above your set speed threshold",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingSpeedWarningEnabled.value,
                            onCheckedChange = {
                                settingSpeedWarningEnabled.value = it
                                saveSetting("setting_speed_warning_enabled", it)
                            }
                        )
                    }

                    if (settingSpeedWarningEnabled.value) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        val displayLimit = formatDisplaySpeed(settingSpeedLimitKmh.value.toDouble())
                        Text("Speed Warning Threshold: $displayLimit", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = settingSpeedLimitKmh.value,
                            onValueChange = { settingSpeedLimitKmh.value = it },
                            onValueChangeFinished = { saveSetting("setting_speed_limit_kmh", settingSpeedLimitKmh.value) },
                            valueRange = 30f..150f,
                            steps = 23
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDisplaySpeed(30.0), style = MaterialTheme.typography.labelSmall)
                            Text(formatDisplaySpeed(80.0), style = MaterialTheme.typography.labelSmall)
                            Text(formatDisplaySpeed(150.0), style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Audible Warning Chime", fontWeight = FontWeight.Medium)
                                Text("Play beep sound when overspeeding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settingSpeedChimeEnabled.value,
                                onCheckedChange = {
                                    settingSpeedChimeEnabled.value = it
                                    saveSetting("setting_speed_chime_enabled", it)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 5: EMERGENCY CONTACTS & SOS =================
            Text("🚨 Emergency Contacts & SOS Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Add up to 3 emergency contacts for instant SMS GPS dispatch and one-tap calling during road emergencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (emergencyContactsList.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No emergency contacts added yet. Tap Add Contact below to add up to 3 trusted contacts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        emergencyContactsList.forEachIndexed { index, contact ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(contact.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index in emergencyContactsList.indices) {
                                                emergencyContactsList.removeAt(index)
                                                saveEmergencyContacts()
                                            }
                                        }
                                    ) {
                                        Text("🗑️", fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (emergencyContactsList.size < 3) {
                        Button(
                            onClick = { showAddContactDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Add Emergency Contact (${emergencyContactsList.size}/3)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✅ Maximum 3 emergency contacts added. Remove one to add a new contact.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    Text("False-Alarm Countdown", fontWeight = FontWeight.Medium)
                    Text(
                        "Cancel window before broadcasting emergency siren across the convoy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val countdownOptions = listOf(
                        Pair(0, "⚡ Instant (0s)"),
                        Pair(5, "⏱️ 5 Seconds"),
                        Pair(10, "⏱️ 10 Seconds")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        countdownOptions.forEach { (sec, label) ->
                            val isSel = settingSosCountdownSeconds.value == sec
                            Surface(
                                onClick = {
                                    settingSosCountdownSeconds.value = sec
                                    saveSetting("setting_sos_countdown_sec", sec)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 6: VOICE & PUSH-TO-TALK =================
            Text("🎙️ Voice & Push-to-Talk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transmission Mode", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val voiceModes = listOf(
                        Pair(VoiceTransmissionMode.OPEN_MIC, "🗣️ Open Mic (Hands-Free: Toggle Mute / Unmute)"),
                        Pair(VoiceTransmissionMode.PUSH_TO_TALK, "🎙️ Push-to-Talk (Tap to speak — cuts wind & engine noise)")
                    )

                    voiceModes.forEach { (mode, label) ->
                        val isSelected = settingVoiceMode.value == mode
                        Surface(
                            onClick = {
                                settingVoiceMode.value = mode
                                saveSetting("setting_voice_mode", mode.name)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        settingVoiceMode.value = mode
                                        saveSetting("setting_voice_mode", mode.name)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Connect Voice", fontWeight = FontWeight.Medium)
                            Text("Automatically join convoy audio channel upon entering a room", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingVoiceAutoJoin.value,
                            onCheckedChange = { saveSettingVoiceAutoJoin(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 7: PUSH NOTIFICATION PREFERENCES =================
            Text("🔔 Notification Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Convoy Separation Alerts", fontWeight = FontWeight.Medium)
                            Text("Notify and beep when falling behind lead", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingNotifySeparation.value,
                            onCheckedChange = {
                                settingNotifySeparation.value = it
                                saveSetting("setting_notify_separation", it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Emergency SOS Sirens", fontWeight = FontWeight.Medium)
                            Text("Play repeating alarm tone on emergency SOS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingNotifySos.value,
                            onCheckedChange = {
                                settingNotifySos.value = it
                                saveSetting("setting_notify_sos", it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Room Expiry & Join Events", fontWeight = FontWeight.Medium)
                            Text("Alerts when room timeout approaches or riders join", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingNotifyRoomEvents.value,
                            onCheckedChange = {
                                settingNotifyRoomEvents.value = it
                                saveSetting("setting_notify_room_events", it)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 8: CONVOY SEPARATION DISTANCE =================
            Text("🛡️ Convoy Safety Threshold", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Separation Alert Distance: ${formatDisplayMeters(distanceSlider)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Alerts when distance to convoy leader exceeds this threshold",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = distanceSlider,
                        onValueChange = { distanceSlider = it },
                        onValueChangeFinished = { saveSettingSeparation(distanceSlider) },
                        valueRange = 100f..1500f,
                        steps = 13
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDisplayMeters(100f) + " (Strict)", style = MaterialTheme.typography.labelSmall)
                        Text(formatDisplayMeters(500f) + " (Default)", style = MaterialTheme.typography.labelSmall)
                        Text(formatDisplayMeters(1500f) + " (Highway)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 9: APP THEME =================
            Text("🎨 Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Appearance Mode", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))

                    val themeOptions = listOf(
                        Pair(AppThemeMode.SYSTEM, "📱 Follow System"),
                        Pair(AppThemeMode.LIGHT, "☀️ Light Mode"),
                        Pair(AppThemeMode.DARK, "🌙 Dark Mode")
                    )

                    themeOptions.forEach { (mode, label) ->
                        val isSelected = appThemeMode.value == mode
                        Surface(
                            onClick = { saveSettingTheme(mode) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { saveSettingTheme(mode) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 10: ABOUT APPLICATION =================
            Text("About DriveDash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DriveDash - Real-Time Group Ride & Convoy Companion",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Designed for group drivers and motorcycle convoys to stay connected effortlessly on the road.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            try {
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Device Location Settings")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version: 1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("GPS: High Accuracy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

        }

        if (showAddContactDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddContactDialog = false
                    newContactName = ""
                    newContactPhone = ""
                    contactDialogError = ""
                },
                title = {
                    Text("Add Emergency Contact (${emergencyContactsList.size + 1}/3)", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Enter contact details for one-tap SMS GPS dispatch and direct calling during an emergency.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newContactName,
                            onValueChange = { newContactName = it; contactDialogError = "" },
                            label = { Text("Contact Name (e.g. Mom, Partner)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newContactPhone,
                            onValueChange = { newContactPhone = it; contactDialogError = "" },
                            label = { Text("Phone Number (e.g. +1234567890)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (contactDialogError.isNotBlank()) {
                            Text(
                                text = contactDialogError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newContactName.trim().isBlank()) {
                                contactDialogError = "Please enter contact name"
                                return@Button
                            }
                            if (newContactPhone.trim().isBlank()) {
                                contactDialogError = "Please enter phone number"
                                return@Button
                            }
                            if (emergencyContactsList.size < 3) {
                                emergencyContactsList.add(EmergencyContact(newContactName.trim(), newContactPhone.trim()))
                                saveEmergencyContacts()
                            }
                            showAddContactDialog = false
                            newContactName = ""
                            newContactPhone = ""
                            contactDialogError = ""
                        }
                    ) {
                        Text("Save Contact")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddContactDialog = false
                            newContactName = ""
                            newContactPhone = ""
                            contactDialogError = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    //checks if the room exists, hasn't expired, and the user isn't banned
    private fun joinRoom(code: String) {
        android.util.Log.d("DriveDash", "joinRoom called with code: $code")
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            errorMessage.value = "Still connecting... please wait a moment and try again"
            android.util.Log.d("DriveDash", "myUid was null - auth not ready yet")
            return
        }
        android.util.Log.d("DriveDash", "myUid: $myUid")
        val roomRef = database.getReference("rooms/$code")
        roomRef.get()
            .addOnSuccessListener { snapshot ->
                android.util.Log.d("DriveDash", "joinRoom snapshot received")
                val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java)
                val isBanned = snapshot.child("banned").child(myUid).exists()
                when {
                    expiresAt == null -> {
                        errorMessage.value = "Room not found"
                        if (lastActiveRoomCode.value == code) clearLastActiveRoom()
                    }
                    isBanned -> {
                        errorMessage.value = "You have been removed from this room"
                        if (lastActiveRoomCode.value == code) clearLastActiveRoom()
                    }
                    System.currentTimeMillis() > expiresAt -> {
                        errorMessage.value = "This room has expired"
                        if (lastActiveRoomCode.value == code) clearLastActiveRoom()
                    }
                    else -> {
                        errorMessage.value = ""
                        val roomChar = snapshot.child("qrCharacter").getValue(String::class.java)
                        if (!roomChar.isNullOrBlank() && cartoonCharactersList.any { it.id == roomChar }) {
                            currentRoomQrCharacterId.value = roomChar
                        } else {
                            val digits = code.filter { it.isDigit() }.toIntOrNull() ?: 0
                            val idx = (digits % cartoonCharactersList.size + cartoonCharactersList.size) % cartoonCharactersList.size
                            currentRoomQrCharacterId.value = cartoonCharactersList[idx].id
                        }
                        saveLastActiveRoom(code, expiresAt)
                        activeRoomCode.value = code
                        currentScreen.value = Screen.IN_ROOM
                        joinedViaManualEntry.value = true
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DriveDash", "joinRoom FAILED", e)
                errorMessage.value = "Couldn't connect. Check internet."
            }
    }

    // ---------- SCREEN 4: IN ROOM (live map + rider list + host controls + route) ----------
    @Composable
    fun InRoomScreen() {
        val myUid = auth.currentUser?.uid ?: ""
        val coroutineScope = rememberCoroutineScope()

        // Map Only mode never touches the mic / voice SDK at all
        val voiceEnabledForMode = rideMode.value != RideMode.MAP_ONLY
        // Voice Only mode skips the map entirely — no need to keep burning GPS/network for it
        val mapEnabledForMode = rideMode.value != RideMode.VOICE_ONLY

        LaunchedEffect(voiceEnabledForMode) {
            if (voiceEnabledForMode && settingVoiceAutoJoin.value) ensureMicPermission()
        }

        LaunchedEffect(activeRoomCode.value) {
            if (activeRoomCode.value.isNotEmpty()) {
                DriveDashForegroundService.start(this@MainActivity, activeRoomCode.value)
                val userRef = database.getReference("rooms/${activeRoomCode.value}/users/$myUid")
                userRef.child("name").setValue(myUsername.value.ifBlank { "Rider ${myUid.take(5)}" })
                userRef.child("vehicleType").setValue(settingVehicleType.value)
                userRef.child("vehicleModel").setValue(settingVehicleModel.value)
                userRef.child("sos").setValue(isMySosActive.value)
                if (currentLat.value != 0.0 && currentLng.value != 0.0) {
                    userRef.child("lat").setValue(currentLat.value)
                    userRef.child("lng").setValue(currentLng.value)
                    userRef.child("speed").setValue(currentSpeed.value)
                    userRef.child("heading").setValue(currentBearing.value)
                    userRef.child("lastUpdate").setValue(System.currentTimeMillis())
                }
                // Initialize trip telemetry tracking
                if (tripStartTime == 0L) {
                    tripStartTime = System.currentTimeMillis()
                    tripTotalDistanceMeters = 0.0
                    tripMaxSpeedMps = 0f
                    lastRecordedLocation = null
                    currentTripBreadcrumbs.clear()
                    lastBreadcrumbLocation = null
                }
                // if this device disconnects unexpectedly (app killed, network drops, crash) —
                // not just a clean "Leave" tap — Firebase's server removes this rider's node on
                // its own, so their marker doesn't linger for everyone else
                userRef.onDisconnect().removeValue()
            } else {
                DriveDashForegroundService.stop(this@MainActivity)
            }
        }

        // battery saver for Voice Only: no map is shown, so there's no need for high-frequency
        // GPS updates on this device. Location resumes automatically the moment the mode changes
        // or the room is left, since onDispose restarts normal updates.
        DisposableEffect(mapEnabledForMode) {
            if (!mapEnabledForMode) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
            onDispose {
                if (!mapEnabledForMode &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) {
                    startLocationUpdates()
                }
            }
        }

        DisposableEffect(activeRoomCode.value, micPermissionGranted.value, voiceEnabledForMode) {
            val uid = auth.currentUser?.uid ?: myUid
            if (voiceEnabledForMode && micPermissionGranted.value && activeRoomCode.value.isNotBlank() && uid.isNotBlank() && callManager == null) {
                val isPtt = settingVoiceMode.value == VoiceTransmissionMode.PUSH_TO_TALK
                isMuted.value = isPtt
                callManager = WebRtcCallManager(this@MainActivity, activeRoomCode.value, uid).also {
                    it.onSpeakingChanged = { speaking ->
                        runOnUiThread {
                            speakingUids.clear()
                            speakingUids.putAll(speaking)
                        }
                    }
                    it.onAudioRouteChanged = { isBt, devName ->
                        runOnUiThread {
                            isBluetoothAudioActive.value = isBt
                            bluetoothAudioDeviceName.value = devName
                            if (isBt) {
                                Toast.makeText(this@MainActivity, "🎧 Audio routed to Bluetooth: $devName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    it.setSpeakerphone(isSpeakerOn.value)
                    it.start()
                    if (isPtt) {
                        it.setMuted(true)
                    }
                    if (riders.isNotEmpty()) {
                        it.syncPeers(riders.keys.toSet())
                    }
                }
            }
            onDispose {
                // If leaving the room entirely, tear down callManager
                if (activeRoomCode.value.isEmpty()) {
                    callManager?.stop()
                    callManager = null
                    speakingUids.clear()
                    isBluetoothAudioActive.value = false
                    bluetoothAudioDeviceName.value = ""
                }
            }
        }

        DisposableEffect(activeRoomCode.value) {
            val roomRef = database.getReference("rooms/${activeRoomCode.value}")
            val listener = object : com.google.firebase.database.ValueEventListener {
                // remembers who was in the room last update, so we can tell who just joined/left
                var previousNames: Map<String, String> = emptyMap()
                var isFirstUpdate = true

                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    roomHostId.value = snapshot.child("hostId").getValue(String::class.java) ?: ""
                    roomExpiresAt.value = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L

                    val qrChar = snapshot.child("qrCharacter").getValue(String::class.java)
                    if (!qrChar.isNullOrBlank() && cartoonCharactersList.any { it.id == qrChar }) {
                        currentRoomQrCharacterId.value = qrChar
                    } else if (activeRoomCode.value.isNotBlank()) {
                        val digits = activeRoomCode.value.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val idx = (digits % cartoonCharactersList.size + cartoonCharactersList.size) % cartoonCharactersList.size
                        currentRoomQrCharacterId.value = cartoonCharactersList[idx].id
                    }

                    if (snapshot.child("banned").child(myUid).exists()) {
                        database.getReference("rooms/${activeRoomCode.value}/users/$myUid").onDisconnect().cancel()
                        activeRoomCode.value = ""
                        currentScreen.value = Screen.HOME
                        joinedViaManualEntry.value = false
                        clearLastActiveRoom()
                        Toast.makeText(this@MainActivity, "You were removed from the room", Toast.LENGTH_LONG).show()
                        return
                    }

                    riders.clear()
                    val currentNames = mutableMapOf<String, String>()
                    val allUidsInRoom = mutableSetOf<String>()
                    for (userSnapshot in snapshot.child("users").children) {
                        val uid = userSnapshot.key ?: continue
                        allUidsInRoom.add(uid)
                        val name = userSnapshot.child("name").getValue(String::class.java)
                            ?: "Rider ${uid.take(5)}"
                        currentNames[uid] = name

                        val lat = userSnapshot.child("lat").getValue(Double::class.java) ?: 0.0
                        val lng = userSnapshot.child("lng").getValue(Double::class.java) ?: 0.0
                        val speed = userSnapshot.child("speed").getValue(Float::class.java) ?: 0f
                        val heading = userSnapshot.child("heading").getValue(Float::class.java) ?: 0f
                        val sos = userSnapshot.child("sos").getValue(Boolean::class.java) ?: false
                        val vType = userSnapshot.child("vehicleType").getValue(String::class.java) ?: "Motorcycle"
                        riders[uid] = RiderLocation(lat, lng, speed, name, heading, sos, vType)
                    }

                    // Track active SOS alerts across the convoy
                    val sosMap = riders.filter { it.value.sos }.mapValues { it.value.name }
                    val hadSos = activeSosRiders.isNotEmpty() || isMySosActive.value
                    activeSosRiders.clear()
                    activeSosRiders.putAll(sosMap)
                    val hasSosNow = activeSosRiders.isNotEmpty() || isMySosActive.value
                    if (hadSos != hasSosNow) {
                        updateSosAlarmState()
                    }

                    // Convoy stray / left-behind detection:
                    // Compare my distance with the leader/host or nearest rider
                    val myLocation = riders[myUid]
                    val hostLocation = riders[roomHostId.value]
                    val thresholdDist = settingSeparationDistanceMeters.value
                    if (myLocation != null && hostLocation != null && myUid != roomHostId.value) {
                        if (myLocation.lat != 0.0 && myLocation.lng != 0.0 && hostLocation.lat != 0.0 && hostLocation.lng != 0.0) {
                            val results = FloatArray(1)
                            Location.distanceBetween(myLocation.lat, myLocation.lng, hostLocation.lat, hostLocation.lng, results)
                            val distToHostMeters = results[0]
                            if (distToHostMeters > thresholdDist) {
                                val displayDist = formatDisplayMeters(distToHostMeters)
                                strayWarningText.value = "⚠️ Convoy alert: You are $displayDist away from convoy lead (${hostLocation.name})"
                                if (settingNotifySeparation.value && System.currentTimeMillis() - lastStrayAlertTimestamp > 15000L) {
                                    lastStrayAlertTimestamp = System.currentTimeMillis()
                                    playToneSafely(AudioManager.STREAM_NOTIFICATION, 80, ToneGenerator.TONE_PROP_BEEP, 300)
                                }
                            } else {
                                strayWarningText.value = null
                            }
                        } else {
                            strayWarningText.value = null
                        }
                    } else if (myLocation != null && myUid == roomHostId.value && riders.size > 1) {
                        // For the host: check if any participant is falling behind
                        var maxDistance = 0f
                        var fallenRiderName = ""
                        for ((otherUid, otherRider) in riders) {
                            if (otherUid != myUid && otherRider.lat != 0.0 && otherRider.lng != 0.0 && myLocation.lat != 0.0 && myLocation.lng != 0.0) {
                                val results = FloatArray(1)
                                Location.distanceBetween(myLocation.lat, myLocation.lng, otherRider.lat, otherRider.lng, results)
                                if (results[0] > thresholdDist && results[0] > maxDistance) {
                                    maxDistance = results[0]
                                    fallenRiderName = otherRider.name
                                }
                            }
                        }
                        if (maxDistance > thresholdDist) {
                            val displayDist = formatDisplayMeters(maxDistance)
                            strayWarningText.value = "⚠️ Convoy alert: $fallenRiderName is falling behind ($displayDist away)"
                            if (settingNotifySeparation.value && System.currentTimeMillis() - lastStrayAlertTimestamp > 15000L) {
                                lastStrayAlertTimestamp = System.currentTimeMillis()
                                playToneSafely(AudioManager.STREAM_NOTIFICATION, 80, ToneGenerator.TONE_PROP_BEEP, 300)
                            }
                        } else {
                            strayWarningText.value = null
                        }
                    } else {
                        strayWarningText.value = null
                    }

                    // anyone who wasn't here last update but is now just joined; anyone who WAS
                    // here but isn't anymore just left (or was kicked). Skip this entirely on the
                    // very first snapshot (right when I join) — otherwise everyone already in the
                    // room would incorrectly get announced as "just joined" the moment I arrive.
                    if (!isFirstUpdate) {
                        for ((uid, name) in currentNames) {
                            if (uid != myUid && !previousNames.containsKey(uid)) {
                                Toast.makeText(this@MainActivity, "$name joined the ride", Toast.LENGTH_SHORT).show()
                            }
                        }
                        for ((uid, name) in previousNames) {
                            if (uid != myUid && !currentNames.containsKey(uid)) {
                                Toast.makeText(this@MainActivity, "$name left the ride", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    previousNames = currentNames
                    isFirstUpdate = false

                    callManager?.syncPeers(allUidsInRoom)

                    // read the host's chosen destination/route so everyone sees it live —
                    // this also runs the instant you (re)join, so an active route is
                    // automatically restored on rejoin, not just on first entry
                    val destLat = snapshot.child("route/destLat").getValue(Double::class.java)
                    val destLng = snapshot.child("route/destLng").getValue(Double::class.java)
                    if (destLat != null && destLng != null) {
                        destinationLatLng.value = com.google.android.gms.maps.model.LatLng(destLat, destLng)
                    } else {
                        destinationLatLng.value = null
                    }
                    val polyline = snapshot.child("route/polyline").getValue(String::class.java)
                    routePoints.value = if (!polyline.isNullOrEmpty()) decodePolyline(polyline) else emptyList()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    android.util.Log.e("DriveDash", "Failed to read room", error.toException())
                }
            }
            roomRef.addValueEventListener(listener)
            onDispose { roomRef.removeEventListener(listener) }
        }

        val isHost = myUid == roomHostId.value

        // ticks every second so the countdown text stays live
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                now = System.currentTimeMillis()
            }
        }
        val remainingMillis = (roomExpiresAt.value - now).coerceAtLeast(0)
        val remainingMinutesTotal = remainingMillis / 1000 / 60
        val remainingHours = remainingMinutesTotal / 60
        val remainingMinutes = remainingMinutesTotal % 60
        val remainingSeconds = (remainingMillis / 1000) % 60
        val timeLeftText = if (remainingMillis <= 0) {
            "Room expired"
        } else if (remainingHours > 0) {
            "%dh %02dm left".format(remainingHours, remainingMinutes)
        } else {
            "%dm %02ds left".format(remainingMinutes, remainingSeconds)
        }
        val isExpired = remainingMillis <= 0 && roomExpiresAt.value > 0
        var dialogDismissed by remember(activeRoomCode.value) { mutableStateOf(false) }
        var showQrDialog by remember { mutableStateOf(false) }
        var isSliderOpen by remember { mutableStateOf(false) }

        BackHandler(enabled = isSliderOpen) {
            isSliderOpen = false
        }
        val currentChar = remember(currentRoomQrCharacterId.value) {
            cartoonCharactersList.find { it.id == currentRoomQrCharacterId.value }
                ?: cartoonCharactersList.first()
        }
        val qrBitmap = remember(activeRoomCode.value, currentRoomQrCharacterId.value) {
            generateHalftoneQrCodeBitmap(
                "https://drivedash.app/join?room=${activeRoomCode.value}",
                currentRoomQrCharacterId.value,
                600
            )
        }

        if (showQrDialog) {
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎨", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Room QR Code", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 6.dp,
                                modifier = Modifier.size(250.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "${currentChar.name} Cartoon Face QR Code",
                                    modifier = Modifier.fillMaxSize().padding(12.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.size(250.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showQrDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header: Top Left: Slider Menu Button, Top Center: Room Code, Top Right: 3 Buttons up to down (End Trip, SOS, Mute)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Top Left: Slider Menu Button
                        Surface(
                            onClick = { isSliderOpen = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "☰",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Top Center: Horizontal layout: 1. room code / timer  •  2. joined devices count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // 1. Room Code / Timer
                                Text(
                                    text = activeRoomCode.value,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = " / ",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )

                                Text(
                                    text = timeLeftText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.secondary
                                )

                                Text(
                                    text = "  •  ",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )

                                // 2. How much devices joined
                                Text(
                                    text = "${riders.size} joined",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { isSliderOpen = true }
                                )
                            }
                        }

                        // Top Right: 3 buttons left to right: 1. End Trip, 2. SOS, 3. Mute
                        InRoomControlBar()
                    }
                }

            // --- ADVANCED FEATURE 2: ACTIVE SOS EMERGENCY BANNER ---
            if (activeSosRiders.isNotEmpty()) {
                val sosNames = activeSosRiders.values.joinToString(", ")
                Surface(
                    color = Color(0xFFD32F2F),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚨", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EMERGENCY SOS: $sosNames reported an emergency / breakdown!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isMySosActive.value) {
                Surface(
                    color = Color(0xFFB71C1C),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shadowElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚨", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YOUR EMERGENCY SOS IS BROADCASTING!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val contactsToDispatch = if (emergencyContactsList.isNotEmpty()) {
                            emergencyContactsList.toList()
                        } else if (settingEmergencyContactPhone.value.isNotBlank()) {
                            listOf(EmergencyContact(settingEmergencyContactName.value.ifBlank { "Contact" }, settingEmergencyContactPhone.value))
                        } else {
                            emptyList()
                        }

                        if (contactsToDispatch.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                contactsToDispatch.forEach { contact ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val uri = Uri.parse("smsto:${contact.phone}")
                                                val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                                                    putExtra("sms_body", "EMERGENCY: I need help! My live convoy GPS location is https://maps.google.com/?q=${currentLat.value},${currentLng.value}")
                                                }
                                                try { startActivity(smsIntent) } catch (_: Exception) {}
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C)),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                        ) {
                                            Text("📲 SMS ${contact.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                        Button(
                                            onClick = {
                                                val uri = Uri.parse("tel:${contact.phone}")
                                                try { startActivity(Intent(Intent.ACTION_DIAL, uri)) } catch (_: Exception) {}
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C)),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                        ) {
                                            Text("📞 Call ${contact.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- ADVANCED FEATURE 1: CONVOY SEPARATION / STRAY WARNING ---
            strayWarningText.value?.let { warning ->
                Surface(
                    color = Color(0xFFFFF3CD),
                    contentColor = Color(0xFF856404),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEEBA))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isExpired && !dialogDismissed) {
                AlertDialog(
                    onDismissRequest = { dialogDismissed = true },
                    title = { Text("Time's up!") },
                    text = {
                        Text(
                            if (isHost) "This room's time has ended. Extend it, or leave."
                            else "This room's time has ended. Waiting for the host to extend it, or you can leave."
                        )
                    },
                    confirmButton = {
                        if (isHost) {
                            Row {
                                TextButton(onClick = { extendRoom(15); dialogDismissed = true }) { Text("+15m") }
                                TextButton(onClick = { extendRoom(30); dialogDismissed = true }) { Text("+30m") }
                                TextButton(onClick = { extendRoom(60); dialogDismissed = true }) { Text("+1hr") }
                            }
                        } else {
                            TextButton(onClick = { dialogDismissed = true }) { Text("OK") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { leaveRoom() }) {
                            Text("Leave Room")
                        }
                    }
                )
            }



            // destination search — HOST ONLY
            if (isHost) {
                LaunchedEffect(destinationInput.value) {
                    if (suppressNextSearch) {
                        suppressNextSearch = false
                        return@LaunchedEffect
                    }
                    val query = destinationInput.value
                    if (query.length < 3) {
                        destinationSuggestions.value = emptyList()
                        return@LaunchedEffect
                    }
                    kotlinx.coroutines.delay(400) // debounce so we don't fire a search on every keystroke
                    isSearchingPlaces.value = true
                    searchPlaces(query)
                    isSearchingPlaces.value = false
                }

            }

            // check if this device's Google Play Services can actually run Maps
            val context = androidx.compose.ui.platform.LocalContext.current
            val playServicesStatus = remember {
                com.google.android.gms.common.GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context)
            }

            // everything below shares the remaining vertical space. Mute/Leave now live in the
            // header row above, so this area is purely map/voice content.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (!mapEnabledForMode) {
                    // Voice Only: no map at all
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🎤 Voice Only Ride", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Map is off to save battery — switch to Map or Map+Voice anytime in Settings ⚙️ without leaving the room",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            riders.forEach { (uid, rider) ->
                                val label = if (uid == myUid) "${rider.name} (You)" else rider.name
                                val isSpeaking = speakingUids[uid] == true
                                Text(
                                    (if (isSpeaking) "🔊 " else "") + label,
                                    color = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else if (playServicesStatus != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "This device's Google Play Services needs to be updated to show the live map.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            val dialog = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                                .getErrorDialog(this@MainActivity, playServicesStatus, 1)
                            dialog?.show()
                        }) {
                            Text("Update Google Play Services")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Riders online: ${riders.size}", style = MaterialTheme.typography.bodyMedium)
                        riders.forEach { (uid, rider) ->
                            val label = if (uid == myUid) "${rider.name} (You)" else rider.name
                            val isSpeaking = speakingUids[uid] == true
                            Text((if (isSpeaking) "🔊 " else "") + label)
                        }
                    }
                } else {
                    if (currentLat.value == 0.0 && currentLng.value == 0.0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Acquiring exact GPS satellite fix…",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    val cameraPositionState = rememberCameraPositionState {
                        if (currentLat.value != 0.0 && currentLng.value != 0.0) {
                            val targetBearing = if (settingMapOrientation.value == MapOrientation.HEADING_UP) currentBearing.value else 0f
                            val targetTilt = if (settingMapPerspective.value == MapPerspective.VIEW_3D) 45f else 0f
                            position = CameraPosition.Builder()
                                .target(com.google.android.gms.maps.model.LatLng(currentLat.value, currentLng.value))
                                .zoom(16f)
                                .bearing(targetBearing)
                                .tilt(targetTilt)
                                .build()
                        }
                    }
                    val isCameraDetached = remember { mutableStateOf(false) }
                    val lastUserDragTime = remember { mutableStateOf(0L) }

                    // Track manual camera drags/gestures
                    LaunchedEffect(cameraPositionState.isMoving) {
                        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
                            isCameraDetached.value = true
                            lastUserDragTime.value = System.currentTimeMillis()
                        }
                    }

                    // Auto-recenter after 5 seconds if enabled
                    LaunchedEffect(isCameraDetached.value, lastUserDragTime.value, settingAutoRecenter.value) {
                        if (isCameraDetached.value && settingAutoRecenter.value) {
                            kotlinx.coroutines.delay(5000L)
                            isCameraDetached.value = false
                        }
                    }

                    // Auto-follow location with Orientation (Heading-up vs North-up) and Perspective (3D vs 2D)
                    LaunchedEffect(currentLat.value, currentLng.value, isCameraDetached.value, settingMapOrientation.value, settingMapPerspective.value) {
                        if (currentLat.value != 0.0 || currentLng.value != 0.0) {
                            if (!isCameraDetached.value) {
                                val targetBearing = if (settingMapOrientation.value == MapOrientation.HEADING_UP) currentBearing.value else 0f
                                val targetTilt = if (settingMapPerspective.value == MapPerspective.VIEW_3D) 45f else 0f
                                val targetZoom = if (cameraPositionState.position.zoom > 10f) cameraPositionState.position.zoom else 16f

                                val newCamera = CameraPosition.Builder()
                                    .target(com.google.android.gms.maps.model.LatLng(currentLat.value, currentLng.value))
                                    .zoom(targetZoom)
                                    .bearing(targetBearing)
                                    .tilt(targetTilt)
                                    .build()

                                cameraPositionState.animate(
                                    CameraUpdateFactory.newCameraPosition(newCamera),
                                    durationMs = 600
                                )
                            }
                        }
                    }

                    // Map Type Layer
                    val composeMapType = when (settingMapType.value.uppercase()) {
                        "SATELLITE" -> MapType.SATELLITE
                        "TERRAIN" -> MapType.TERRAIN
                        "HYBRID" -> MapType.HYBRID
                        else -> MapType.NORMAL
                    }

                    // Live Speed Limit Monitoring & Chime
                    val currentSpeedKmh = currentSpeed.value * 3.6
                    val isSpeeding = settingSpeedWarningEnabled.value && (currentSpeedKmh > settingSpeedLimitKmh.value)
                    LaunchedEffect(isSpeeding) {
                        if (isSpeeding && settingSpeedChimeEnabled.value) {
                            val now = System.currentTimeMillis()
                            if (now - lastSpeedAlertTimestamp > 10000L) {
                                lastSpeedAlertTimestamp = now
                                playToneSafely(AudioManager.STREAM_NOTIFICATION, 80, ToneGenerator.TONE_PROP_BEEP, 300)
                            }
                        }
                    }

                    val sosNavIcon = remember {
                        bitmapDescriptorFromVector(context, R.drawable.ic_nav_arrow, android.graphics.Color.parseColor("#D32F2F"))
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = remember(composeMapType, settingTrafficEnabled.value) {
                            MapProperties(
                                mapType = composeMapType,
                                isTrafficEnabled = settingTrafficEnabled.value,
                                isBuildingEnabled = true
                            )
                        },
                        uiSettings = remember { MapUiSettings(zoomControlsEnabled = false) }
                    ) {
                        // a marker for each rider currently in the room
                        riders.forEach { (uid, rider) ->
                            key(uid) {
                                val isMe = uid == myUid
                                val isSpeaking = speakingUids[uid] == true
                                val riderHex = remember(uid, isMe) { riderColorHexForUid(uid, isMe) }
                                val riderComposeColor = remember(riderHex) { Color(android.graphics.Color.parseColor(riderHex)) }
                                val riderNavIcon = remember(riderHex) {
                                    bitmapDescriptorFromVector(context, R.drawable.ic_nav_arrow, android.graphics.Color.parseColor(riderHex))
                                }

                                val liveSpeedKmh = if (isMe) (currentSpeed.value * 3.6).toDouble() else (rider.speed * 3.6).toDouble()
                                val displaySpeed = formatDisplaySpeed(liveSpeedKmh)
                                val riderVehicleType = if (isMe) settingVehicleType.value else rider.vehicleType
                                val vIcon = getVehicleIcon(riderVehicleType)
                                val markerLat = if (isMe && currentLat.value != 0.0) currentLat.value else rider.lat
                                val markerLng = if (isMe && currentLng.value != 0.0) currentLng.value else rider.lng
                                val markerPosition = com.google.android.gms.maps.model.LatLng(markerLat, markerLng)

                                // smoothly turn the arrow to the new heading instead of snapping to it
                                val targetHeading = if (isMe && currentBearing.value != 0f) currentBearing.value else rider.heading
                                val animatedHeading = remember { Animatable(targetHeading) }
                                LaunchedEffect(targetHeading) {
                                    val current = animatedHeading.value
                                    var delta = (targetHeading - current) % 360f
                                    if (delta > 180f) delta -= 360f
                                    if (delta < -180f) delta += 360f
                                    animatedHeading.animateTo(
                                        current + delta,
                                        animationSpec = tween(durationMillis = 400)
                                    )
                                }

                                if (markerLat != 0.0 && markerLng != 0.0) {
                                    Marker(
                                        state = com.google.maps.android.compose.MarkerState(position = markerPosition),
                                        title = (if (rider.sos) "🚨 SOS! " else if (isSpeaking) "🔊 " else "") + (if (isMe) "You" else rider.name),
                                        snippet = displaySpeed,
                                        icon = if (rider.sos) sosNavIcon else riderNavIcon,
                                        rotation = animatedHeading.value,
                                        flat = true,
                                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                                    )

                                    // Every user's speed is displayed directly on their arrow in the map
                                    MarkerComposable(
                                        keys = arrayOf<Any>(rider.name, isSpeaking, rider.sos, riderHex, riderVehicleType, displaySpeed, isMe),
                                        state = com.google.maps.android.compose.MarkerState(position = markerPosition),
                                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.8f), // float directly above the arrow
                                        flat = false, // always face the screen, never rotates with the arrow/map
                                        zIndex = 1f
                                    ) {
                                        Surface(
                                            color = if (rider.sos) Color(0xFFD32F2F) else riderComposeColor,
                                            contentColor = Color.White,
                                            shape = RoundedCornerShape(8.dp),
                                            shadowElevation = 4.dp,
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val statusPrefix = if (rider.sos) "🚨 " else if (isSpeaking) "🔊 " else ""
                                                val displayName = if (isMe) (if (rider.sos) "You (SOS)" else "You") else rider.name
                                                Text(
                                                    text = "$statusPrefix$vIcon $displayName",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (rider.sos) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = displaySpeed,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // draw the route line if the host has set one
                        if (routePoints.value.isNotEmpty()) {
                            Polyline(
                                points = routePoints.value,
                                color = MaterialTheme.colorScheme.primary,
                                width = 8f
                            )
                        }
                        // marker at the destination
                        destinationLatLng.value?.let { destPoint ->
                            Marker(
                                state = com.google.maps.android.compose.MarkerState(position = destPoint),
                                title = "Destination"
                            )
                        }
                    }

                    // Floating GPS Disabled Warning Overlay on Map
                    if (!isLocationHardwareEnabled.value) {
                        Surface(
                            color = Color(0xFFD32F2F),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️ GPS is turned off! Turn on location to navigate.", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        try {
                                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFD32F2F)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                ) {
                                    Text("Turn On", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Floating Speed Limit Warning Overlay
                    if (isSpeeding) {
                        Surface(
                            color = Color(0xFFD32F2F),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (!isLocationHardwareEnabled.value) 76.dp else 16.dp),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                "⚡ SPEED WARNING: ${formatDisplaySpeed(currentSpeedKmh)} (Limit: ${settingSpeedLimitKmh.value.toInt()} km/h)",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Floating Map Controls: 3D/2D toggle, Zoom In/Out, and Re-center
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 3D / 2D Perspective Toggle Button
                        Surface(
                            onClick = {
                                val is3D = settingMapPerspective.value == MapPerspective.VIEW_3D
                                val nextMode = if (is3D) MapPerspective.VIEW_2D else MapPerspective.VIEW_3D
                                settingMapPerspective.value = nextMode
                                saveSetting("setting_map_perspective", nextMode.name)
                                val targetTilt = if (nextMode == MapPerspective.VIEW_3D) 45f else 0f
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder(cameraPositionState.position)
                                                .tilt(targetTilt)
                                                .build()
                                        ),
                                        durationMs = 350
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 5.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (settingMapPerspective.value == MapPerspective.VIEW_3D) "3D" else "2D",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (settingMapPerspective.value == MapPerspective.VIEW_3D) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 2. Zoom In / Zoom Out Controls Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 5.dp,
                            modifier = Modifier.width(44.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Zoom In (+)
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            cameraPositionState.animate(CameraUpdateFactory.zoomIn(), durationMs = 250)
                                        }
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(modifier = Modifier.width(30.dp), thickness = 0.8.dp)
                                // Zoom Out (−)
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            cameraPositionState.animate(CameraUpdateFactory.zoomOut(), durationMs = 250)
                                        }
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // 3. Floating Re-Center Button (when camera is detached)
                        if (isCameraDetached.value) {
                            FloatingActionButton(
                                onClick = {
                                    isCameraDetached.value = false
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🎯", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-center", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // False-Alarm SOS Countdown Dialog
                    if (isSosCountdownActive.value) {
                        AlertDialog(
                            onDismissRequest = {
                                isSosCountdownActive.value = false
                            },
                            title = {
                                Text("🚨 Sending SOS in ${sosCountdownRemaining.value}s", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            },
                            text = {
                                Text("Emergency SOS will alert everyone in the convoy and sound alarms. Tap Cancel if this was an accident.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        isSosCountdownActive.value = false
                                        if (!isMySosActive.value) {
                                            toggleSos()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                ) {
                                    Text("Send Now", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = {
                                        isSosCountdownActive.value = false
                                    }
                                ) {
                                    Text("Cancel (False Alarm)")
                                }
                            }
                        )
                    }

                    LaunchedEffect(isSosCountdownActive.value) {
                        if (isSosCountdownActive.value) {
                            while (sosCountdownRemaining.value > 0 && isSosCountdownActive.value) {
                                kotlinx.coroutines.delay(1000L)
                                sosCountdownRemaining.value -= 1
                            }
                            if (isSosCountdownActive.value && sosCountdownRemaining.value <= 0) {
                                isSosCountdownActive.value = false
                                if (!isMySosActive.value) {
                                    toggleSos()
                                }
                            }
                        }
                    }

                    // Discord In-Map Voice Overlay HUD
                    if (settingVoiceOverlayEnabled.value && voiceEnabledForMode) {
                        DiscordVoiceMapOverlay(
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
            }
        }

            // SLIDER / SIDE DRAWER (TOP-LEFT SLIDE-IN)
            // 1. Scrim
            androidx.compose.animation.AnimatedVisibility(
                visible = isSliderOpen,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isSliderOpen = false }
                )
            }

            // 2. Sliding Drawer Panel
            androidx.compose.animation.AnimatedVisibility(
                visible = isSliderOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(250)),
                exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(250))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 280.dp, max = 340.dp)
                        .fillMaxWidth(0.85f),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Drawer Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Room Menu",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { isSliderOpen = false }) {
                                Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Room: ${activeRoomCode.value}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. DESTINATION SEARCH BAR (ON TOP)
                        Text(
                            text = "DESTINATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isHost) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = destinationInput.value,
                                    onValueChange = { destinationInput.value = it },
                                    label = { Text("Search place / address") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        destinationSuggestions.value = emptyList()
                                        findRoute(destinationInput.value)
                                        isSliderOpen = false
                                    }
                                ) {
                                    Text("Go")
                                }
                            }

                            if (isSearchingPlaces.value) {
                                Text(
                                    "Searching…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (destinationSuggestions.value.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    destinationSuggestions.value.forEach { suggestion ->
                                        Text(
                                            suggestion.label,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    suppressNextSearch = true
                                                    destinationInput.value = suggestion.label
                                                    destinationSuggestions.value = emptyList()
                                                    if (suggestion.lat != 0.0 || suggestion.lng != 0.0) {
                                                        routeToDestination(suggestion.lat, suggestion.lng)
                                                    } else {
                                                        findRoute(suggestion.label)
                                                    }
                                                    isSliderOpen = false
                                                }
                                                .padding(vertical = 10.dp)
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }

                            if (routeError.value.isNotEmpty()) {
                                Text(
                                    routeError.value,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else {
                            if (destinationLatLng.value != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Host has set a destination route",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            } else {
                                Text(
                                    "Host sets destination for the group",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. EVERY DEVICE / RIDERS WHO HAVE JOINED THE ROOM (2ND)
                        Text(
                            text = "JOINED RIDERS / DEVICES (${riders.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (riders.isEmpty()) {
                                    Text(
                                        "No other riders connected yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    riders.entries.forEachIndexed { index, (uid, rider) ->
                                        val isMe = uid == myUid
                                        val label = if (isMe) "${rider.name} (You)" else rider.name
                                        val isSpeaking = speakingUids[uid] == true
                                        val riderHex = riderColorHexForUid(uid, isMe)
                                        val riderColor = Color(android.graphics.Color.parseColor(riderHex))
                                        val isRiderHost = uid == roomHostId.value

                                        if (index > 0) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(
                                                            if (rider.sos) Color(0xFFD32F2F) else riderColor,
                                                            shape = CircleShape
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val vIcon = getVehicleIcon(rider.vehicleType)
                                                Text(
                                                    (if (rider.sos) "🚨 " else if (isSpeaking) "🔊 " else "") + "$vIcon $label",
                                                    color = if (rider.sos) Color(0xFFD32F2F) else if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (rider.sos || isRiderHost) FontWeight.Bold else FontWeight.Normal,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isRiderHost) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "HOST",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }

                                            if (isHost && !isMe) {
                                                TextButton(
                                                    onClick = { kickUser(uid) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Kick", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        // ROOM CONTROLS: 3. QR Code, 4. Settings
                        Text(
                            text = "ROOM CONTROLS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. QR Code Button
                        FilledTonalButton(
                            onClick = {
                                isSliderOpen = false
                                showQrDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎨", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("QR Code", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 4. Settings Button
                        FilledTonalButton(
                            onClick = {
                                isSliderOpen = false
                                previousScreenBeforeSettings.value = Screen.IN_ROOM
                                currentScreen.value = Screen.SETTINGS
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚙️", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Settings", fontWeight = FontWeight.SemiBold)
                            }
                        }

                    }
                }
            }
        }
    }

    // 3 buttons on top-right in a line left-to-right: 1. End Trip, 2. SOS, 3. Mute
    @Composable
    fun InRoomControlBar(modifier: Modifier = Modifier) {
        val isHost = auth.currentUser?.uid == roomHostId.value
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Line 1: End Trip (if host) / Leave (if rider)
            Button(
                onClick = { endOrLeaveTrip() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 28.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isHost) "End Trip" else "Leave", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            // Line 2: SOS Emergency Button
            Button(
                onClick = {
                    if (isMySosActive.value) {
                        toggleSos()
                    } else {
                        val countdownSecs = settingSosCountdownSeconds.value
                        if (countdownSecs <= 0) {
                            toggleSos()
                        } else {
                            sosCountdownRemaining.value = countdownSecs
                            isSosCountdownActive.value = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMySosActive.value) Color(0xFFD32F2F) else Color(0xFFE57373),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 28.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isMySosActive.value) "🚨 SOS ON" else "🚨 SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            // Line 3: Speakerphone Toggle Button (🔊 Speaker / 🔈 Earpiece)
            if (rideMode.value != RideMode.MAP_ONLY) {
                FilledTonalButton(
                    onClick = {
                        isSpeakerOn.value = !isSpeakerOn.value
                        callManager?.setSpeakerphone(isSpeakerOn.value)
                        Toast.makeText(
                            this@MainActivity,
                            if (isSpeakerOn.value) "🔊 Loudspeaker ON" else "🔈 Earpiece ON",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSpeakerOn.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSpeakerOn.value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 50.dp, minHeight = 28.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isSpeakerOn.value) "🔊 Speaker" else "🔈 Ear", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                // Line 4: Mute / Push-to-Talk / Talk Button
                if (micPermissionGranted.value) {
                    if (settingVoiceMode.value == VoiceTransmissionMode.PUSH_TO_TALK) {
                        var isPttPressed by remember { mutableStateOf(false) }
                        Button(
                            onClick = { /* handled by pointerInput */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPttPressed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 56.dp, minHeight = 28.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isPttPressed = true
                                            isMuted.value = false
                                            callManager?.setMuted(false)
                                            tryAwaitRelease()
                                            isPttPressed = false
                                            isMuted.value = true
                                            callManager?.setMuted(true)
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val btPrefix = if (isBluetoothAudioActive.value) "🎧 " else ""
                            Text(if (isPttPressed) "🎙️ Talking" else "${btPrefix}🎙️ Hold Talk", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = {
                                isMuted.value = !isMuted.value
                                callManager?.setMuted(isMuted.value)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMuted.value) Color(0xFF757575) else Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val btPrefix = if (isBluetoothAudioActive.value) "🎧 " else ""
                            Text(btPrefix + (if (isMuted.value) "🔇 Muted" else "🎙️ Talk ON"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                } else {
                    Button(
                        onClick = { ensureMicPermission() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 28.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🎙️ Enable Mic", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    // Discord In-Map Voice Overlay: Locked at top-left, pure name text only without any background
    @Composable
    fun DiscordVoiceMapOverlay(modifier: Modifier = Modifier) {
        val myUid = auth.currentUser?.uid ?: ""
        val myName = myUsername.value.ifBlank { "Rider ${myUid.take(5)}" }
        val isMySpeaking = speakingUids[myUid] == true

        data class OverlayParticipant(
            val uid: String,
            val name: String,
            val isMe: Boolean,
            val isSpeaking: Boolean,
            val isMuted: Boolean,
            val isSos: Boolean,
            val vehicleType: String
        )

        val allParticipants = remember(riders.toMap(), speakingUids.toMap(), isMuted.value, myName, isMySpeaking) {
            val list = mutableListOf<OverlayParticipant>()
            // Local user
            list.add(
                OverlayParticipant(
                    uid = myUid,
                    name = myName,
                    isMe = true,
                    isSpeaking = isMySpeaking,
                    isMuted = isMuted.value,
                    isSos = isMySosActive.value,
                    vehicleType = settingVehicleType.value
                )
            )
            // Remote riders
            riders.forEach { (uid, rider) ->
                if (uid != myUid) {
                    list.add(
                        OverlayParticipant(
                            uid = uid,
                            name = rider.name,
                            isMe = false,
                            isSpeaking = speakingUids[uid] == true,
                            isMuted = false,
                            isSos = rider.sos,
                            vehicleType = rider.vehicleType
                        )
                    )
                }
            }
            list
        }

        val displayList = if (settingVoiceOverlayOnlySpeaking.value) {
            allParticipants.filter { it.isSpeaking }
        } else {
            allParticipants
        }

        // Locked at top-left: pure name text only, NO background, with shadow for clarity over map
        Column(
            modifier = modifier
                .padding(top = 10.dp, start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            displayList.forEach { p ->
                val textAlpha by animateFloatAsState(
                    targetValue = if (p.isSpeaking) 0.75f else 0.40f,
                    animationSpec = tween(200),
                    label = "nameAlpha"
                )

                val prefix = when {
                    p.isSos -> "🚨 "
                    p.isSpeaking -> "🔊 "
                    p.isMe && p.isMuted -> "🔇 "
                    else -> ""
                }
                val displayName = if (p.isMe) "${p.name} (You)" else p.name

                Text(
                    text = "$prefix$displayName",
                    fontSize = 13.sp,
                    fontWeight = if (p.isSpeaking) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (p.isSpeaking) Color(0xFF57F287) else Color.White,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(1.5f, 1.5f),
                            blurRadius = 3f
                        )
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .alpha(textAlpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (p.isMe) {
                                isMuted.value = !isMuted.value
                                callManager?.setMuted(isMuted.value)
                            }
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }

    // host-only: removes a rider from the room and bans them from rejoining
    private fun kickUser(uid: String) {
        val roomCode = activeRoomCode.value
        database.getReference("rooms/$roomCode/users/$uid").removeValue()
        database.getReference("rooms/$roomCode/banned/$uid").setValue(true)
    }

    private fun toggleSos() {
        val roomCode = activeRoomCode.value
        val userId = auth.currentUser?.uid
        if (roomCode.isNotEmpty() && userId != null) {
            val nextState = !isMySosActive.value
            isMySosActive.value = nextState
            database.getReference("rooms/$roomCode/users/$userId/sos").setValue(nextState)
            updateSosAlarmState()
            if (nextState) {
                Toast.makeText(this, "🚨 EMERGENCY SOS ACTIVE - Repeating alarm every 15s until turned off!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Emergency SOS cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun endOrLeaveTrip() {
        val roomCode = activeRoomCode.value
        val durationMillis = (System.currentTimeMillis() - tripStartTime).coerceAtLeast(1000L)
        val distanceKm = tripTotalDistanceMeters / 1000.0
        val durationHours = durationMillis / 3600000.0
        val avgSpeedKmh = if (durationHours > 0) distanceKm / durationHours else 0.0
        val topSpeedKmh = tripMaxSpeedMps * 3.6
        val polylineStr = encodePoints(currentTripBreadcrumbs)

        val stats = TripStats(
            roomCode = roomCode,
            totalDistanceKm = distanceKm,
            durationMillis = durationMillis,
            averageSpeedKmh = avgSpeedKmh,
            topSpeedKmh = topSpeedKmh,
            timestamp = System.currentTimeMillis(),
            routePolyline = polylineStr,
            ridersCount = riders.size
        )
        completedTripStats.value = stats
        saveCompletedTrip(stats)

        val isHost = auth.currentUser?.uid == roomHostId.value
        leaveRoomInternal(preserveForSummary = true)
        currentScreen.value = Screen.TRIP_SUMMARY
    }

    // cleanly leaves the current room: removes this rider's own marker/data from Firebase
    // immediately (instead of leaving it to linger until the room expires), cancels the
    // onDisconnect safety-net since we're leaving on purpose, and resets local route state
    // so nothing stale flashes if this device joins another room next
    private fun leaveRoom() {
        leaveRoomInternal(preserveForSummary = false)
        currentScreen.value = Screen.HOME
    }

    private fun leaveRoomInternal(preserveForSummary: Boolean) {
        val roomCode = activeRoomCode.value
        val userId = auth.currentUser?.uid
        if (roomCode.isNotEmpty() && userId != null) {
            val userRef = database.getReference("rooms/$roomCode/users/$userId")
            userRef.onDisconnect().cancel()
            userRef.removeValue()
        }
        // If SOS was active, reset it
        if (isMySosActive.value && roomCode.isNotEmpty() && userId != null) {
            database.getReference("rooms/$roomCode/users/$userId/sos").removeValue()
            isMySosActive.value = false
        }
        activeSosRiders.clear()
        riders.clear()
        sosAlarmHandler.removeCallbacks(sosAlarmRunnable)
        // Recent Room is captured here, at leave-time — only for a room the user typed a code
        // into, not one they hosted. This becomes the one-tap "Recent Room" option on Home.
        if (roomCode.isNotEmpty() && joinedViaManualEntry.value) {
            saveLastActiveRoom(roomCode, roomExpiresAt.value)
        }
        joinedViaManualEntry.value = false
        activeRoomCode.value = ""
        callManager?.stop()
        callManager = null
        speakingUids.clear()
        isBluetoothAudioActive.value = false
        bluetoothAudioDeviceName.value = ""
        DriveDashForegroundService.stop(this)
        strayWarningText.value = null
        destinationLatLng.value = null
        routePoints.value = emptyList()
        destinationInput.value = ""
        destinationSuggestions.value = emptyList()
        routeError.value = ""
        if (!preserveForSummary) {
            // If the user participated in a ride (> 10s or moved > 10m), save it as last trip before resetting
            if (tripStartTime > 0L && (System.currentTimeMillis() - tripStartTime > 10000L || tripTotalDistanceMeters > 10.0)) {
                val durationMillis = (System.currentTimeMillis() - tripStartTime).coerceAtLeast(1000L)
                val distanceKm = tripTotalDistanceMeters / 1000.0
                val durationHours = durationMillis / 3600000.0
                val avgSpeedKmh = if (durationHours > 0) distanceKm / durationHours else 0.0
                val topSpeedKmh = tripMaxSpeedMps * 3.6
                val polylineStr = encodePoints(currentTripBreadcrumbs)
                saveCompletedTrip(
                    TripStats(
                        roomCode = roomCode,
                        totalDistanceKm = distanceKm,
                        durationMillis = durationMillis,
                        averageSpeedKmh = avgSpeedKmh,
                        topSpeedKmh = topSpeedKmh,
                        timestamp = System.currentTimeMillis(),
                        routePolyline = polylineStr
                    )
                )
            }
            tripStartTime = 0L
            tripTotalDistanceMeters = 0.0
            tripMaxSpeedMps = 0f
            lastRecordedLocation = null
            currentTripBreadcrumbs.clear()
            lastBreadcrumbLocation = null
        }
    }

    // host-only: pushes the room's expiry further into the future
    private fun extendRoom(additionalMinutes: Int) {
        val roomCode = activeRoomCode.value
        val newExpiresAt = System.currentTimeMillis() + (additionalMinutes * 60 * 1000L)
        database.getReference("rooms/$roomCode/expiresAt").setValue(newExpiresAt)
    }

    // host-only: live-search-as-you-type suggestions. Uses Nominatim's general free-text search,
    // which matches addresses AND named places (shops, landmarks, restaurants, offices, etc.) —
    // Google Maps Place Search & Suggestion Engine
    private suspend fun searchPlaces(query: String) {
        try {
            val suggestions = withContext(Dispatchers.IO) {
                val parsed = mutableListOf<PlaceSuggestion>()

                // 1. Primary: Use Android's native Google Geocoder (Google's official geocoding engine)
                try {
                    val geocoder = android.location.Geocoder(this@MainActivity, java.util.Locale.getDefault())
                    val addresses = if (currentLat.value != 0.0 || currentLng.value != 0.0) {
                        val lat = currentLat.value
                        val lng = currentLng.value
                        val delta = 1.0 // ~110km area around user's location
                        geocoder.getFromLocationName(
                            query,
                            8,
                            lat - delta,
                            lng - delta,
                            lat + delta,
                            lng + delta
                        )
                    } else {
                        geocoder.getFromLocationName(query, 8)
                    }

                    if (!addresses.isNullOrEmpty()) {
                        for (addr in addresses) {
                            val line = addr.getAddressLine(0)
                            val name = addr.featureName
                            val label = if (!name.isNullOrBlank() && line != null && !line.startsWith(name)) {
                                "$name, $line"
                            } else {
                                line ?: name ?: ""
                            }
                            if (label.isNotBlank()) {
                                parsed.add(PlaceSuggestion(label, addr.latitude, addr.longitude))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DriveDash", "Google Native Geocoder search error", e)
                }

                // 2. Google Autocomplete Query Suggestions (Exact Google Maps query completion engine)
                if (parsed.size < 5) {
                    try {
                        val encodedQuery = URLEncoder.encode(query, "UTF-8")
                        val googleSuggestUrl = "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
                        val jsonStr = fetchUrl(googleSuggestUrl, userAgent = "Mozilla/5.0")
                        val rootArray = org.json.JSONArray(jsonStr)
                        if (rootArray.length() >= 2) {
                            val suggestionsArray = rootArray.getJSONArray(1)
                            val geocoder = android.location.Geocoder(this@MainActivity, java.util.Locale.getDefault())
                            for (i in 0 until minOf(suggestionsArray.length(), 6)) {
                                val text = suggestionsArray.getString(i)
                                if (parsed.none { it.label.contains(text, ignoreCase = true) }) {
                                    try {
                                        val geo = geocoder.getFromLocationName(text, 1)
                                        if (!geo.isNullOrEmpty()) {
                                            parsed.add(PlaceSuggestion(text, geo[0].latitude, geo[0].longitude))
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("DriveDash", "Google Suggest queries error", e)
                    }
                }

                // 3. Fallback to Nominatim if both returned empty
                if (parsed.isEmpty()) {
                    try {
                        val encodedQuery = URLEncoder.encode(query, "UTF-8")
                        val biasParams = if (currentLat.value != 0.0 || currentLng.value != 0.0) {
                            val lat = currentLat.value
                            val lng = currentLng.value
                            val delta = 0.9
                            "&viewbox=${lng - delta},${lat + delta},${lng + delta},${lat - delta}&bounded=0"
                        } else ""
                        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=6&addressdetails=1&dedupe=1$biasParams"
                        val json = fetchUrl(url, userAgent = "DriveDash-CollegeProject/1.0")
                        val results = org.json.JSONArray(json)
                        for (i in 0 until results.length()) {
                            val obj = results.getJSONObject(i)
                            val label = obj.optString("display_name", "")
                            val lat = obj.optString("lat", "").toDoubleOrNull()
                            val lng = obj.optString("lon", "").toDoubleOrNull()
                            if (label.isNotEmpty() && lat != null && lng != null) {
                                parsed.add(PlaceSuggestion(label, lat, lng))
                            }
                        }
                    } catch (_: Exception) {}
                }

                parsed
            }
            destinationSuggestions.value = suggestions
        } catch (e: Exception) {
            android.util.Log.e("DriveDash", "Place search error", e)
            destinationSuggestions.value = emptyList()
        }
    }

    // host-only: builds a road route from the host's current location to a chosen lat/lng
    // and SAVES it to Firebase so every rider in the room sees the same route live
    private fun routeToDestination(destLat: Double, destLng: Double) {
        if (currentLat.value == 0.0 && currentLng.value == 0.0) {
            routeError.value = "Waiting for your GPS location first"
            return
        }
        routeError.value = ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // OSRM expects coordinates as lng,lat
                val originStr = "${currentLng.value},${currentLat.value}"
                val destStr = "$destLng,$destLat"
                val osrmUrl = "https://router.project-osrm.org/route/v1/driving/$originStr;$destStr?overview=full&geometries=polyline"
                val osrmJson = fetchUrl(osrmUrl, userAgent = "DriveDash-CollegeProject/1.0")
                val osrmResponse = JSONObject(osrmJson)
                val osrmStatus = osrmResponse.getString("code")
                if (osrmStatus != "Ok") {
                    withContext(Dispatchers.Main) { routeError.value = "No route found" }
                    return@launch
                }
                val routes = osrmResponse.getJSONArray("routes")
                val geometry = routes.getJSONObject(0).getString("geometry")

                val roomCode = activeRoomCode.value
                val routeData = mapOf(
                    "destLat" to destLat,
                    "destLng" to destLng,
                    "polyline" to geometry
                )
                database.getReference("rooms/$roomCode/route").setValue(routeData)
            } catch (e: Exception) {
                android.util.Log.e("DriveDash", "Route error", e)
                withContext(Dispatchers.Main) { routeError.value = "Failed to get route: ${e.message}" }
            }
        }
    }

    // Geocodes the destination address using Google's native Geocoder engine, then plots route
    private fun findRoute(destinationAddress: String) {
        if (currentLat.value == 0.0 && currentLng.value == 0.0) {
            routeError.value = "Waiting for your GPS location first"
            return
        }
        routeError.value = ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var destLat: Double? = null
                var destLng: Double? = null

                // 1. Google Native Geocoder
                try {
                    val geocoder = android.location.Geocoder(this@MainActivity, java.util.Locale.getDefault())
                    val list = geocoder.getFromLocationName(destinationAddress, 1)
                    if (!list.isNullOrEmpty()) {
                        destLat = list[0].latitude
                        destLng = list[0].longitude
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DriveDash", "Google geocode error", e)
                }

                // 2. Fallback
                if (destLat == null || destLng == null) {
                    val encodedAddress = URLEncoder.encode(destinationAddress, "UTF-8")
                    val geocodeUrl = "https://nominatim.openstreetmap.org/search?q=$encodedAddress&format=json&limit=1"
                    val geocodeJson = fetchUrl(geocodeUrl, userAgent = "DriveDash-CollegeProject/1.0")
                    val geocodeResults = org.json.JSONArray(geocodeJson)
                    if (geocodeResults.length() > 0) {
                        destLat = geocodeResults.getJSONObject(0).getString("lat").toDouble()
                        destLng = geocodeResults.getJSONObject(0).getString("lon").toDouble()
                    }
                }

                if (destLat == null || destLng == null) {
                    withContext(Dispatchers.Main) { routeError.value = "Place not found" }
                    return@launch
                }

                withContext(Dispatchers.Main) { routeToDestination(destLat, destLng) }
            } catch (e: Exception) {
                android.util.Log.e("DriveDash", "Geocode error", e)
                withContext(Dispatchers.Main) { routeError.value = "Failed to find that place: ${e.message}" }
            }
        }
    }


    // renders a vector drawable to a Bitmap with a given tint, for use as a Marker icon.
    // (Marker icons must be Bitmaps, not vector drawables, so this converts once and we reuse it.)
    // Falls back to the default marker (returns null) instead of crashing if the Maps SDK
    // somehow hasn't finished initializing yet.
    private fun bitmapDescriptorFromVector(
        context: android.content.Context,
        vectorResId: Int,
        tintColor: Int
    ): com.google.android.gms.maps.model.BitmapDescriptor? {
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId) ?: return null
            val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(drawable).mutate()
            androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, tintColor)
            wrapped.setBounds(0, 0, wrapped.intrinsicWidth.coerceAtLeast(1), wrapped.intrinsicHeight.coerceAtLeast(1))
            val bitmap = android.graphics.Bitmap.createBitmap(
                wrapped.bounds.width(),
                wrapped.bounds.height(),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            wrapped.draw(canvas)
            com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            android.util.Log.e("DriveDash", "Marker icon build failed, using default pin instead", e)
            null
        }
    }

    private fun fetchUrl(urlString: String, userAgent: String? = null): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (userAgent != null) {
            connection.setRequestProperty("User-Agent", userAgent)
        }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    // Google/OSRM return routes as a compressed "encoded polyline" string; this decodes it
    // into a list of actual lat/lng points we can draw on the map
    private fun decodePolyline(encoded: String): List<com.google.android.gms.maps.model.LatLng> {
        val poly = ArrayList<com.google.android.gms.maps.model.LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(com.google.android.gms.maps.model.LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    // simple holder for one rider's live data
    data class RiderLocation(
        val lat: Double,
        val lng: Double,
        val speed: Float,
        val name: String,
        val heading: Float,
        val sos: Boolean = false,
        val vehicleType: String = "Motorcycle"
    )

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        try {
            // 1. Immediately read last known cached location (takes 0ms)
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    if (currentLat.value == 0.0 && currentLng.value == 0.0) {
                        currentLat.value = loc.latitude
                        currentLng.value = loc.longitude
                        currentSpeed.value = loc.speed
                        if (loc.hasBearing()) currentBearing.value = loc.bearing
                        saveLastKnownLocation(loc.latitude, loc.longitude)
                    }
                    val code = activeRoomCode.value
                    val uid = auth.currentUser?.uid
                    if (code.isNotBlank() && !uid.isNullOrBlank()) {
                        val uRef = database.getReference("rooms/$code/users/$uid")
                        uRef.child("lat").setValue(loc.latitude)
                        uRef.child("lng").setValue(loc.longitude)
                        uRef.child("speed").setValue(loc.speed)
                        if (loc.hasBearing()) uRef.child("heading").setValue(loc.bearing)
                        uRef.child("lastUpdate").setValue(System.currentTimeMillis())
                    }
                }
            }

            // 2. Request immediate one-shot high-accuracy fix without waiting for periodic timer
            val cancelToken = com.google.android.gms.tasks.CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelToken.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLat.value = loc.latitude
                        currentLng.value = loc.longitude
                        currentSpeed.value = loc.speed
                        if (loc.hasBearing()) currentBearing.value = loc.bearing
                        saveLastKnownLocation(loc.latitude, loc.longitude)
                        val code = activeRoomCode.value
                        val uid = auth.currentUser?.uid
                        if (code.isNotBlank() && !uid.isNullOrBlank()) {
                            val uRef = database.getReference("rooms/$code/users/$uid")
                            uRef.child("lat").setValue(loc.latitude)
                            uRef.child("lng").setValue(loc.longitude)
                            uRef.child("speed").setValue(loc.speed)
                            if (loc.hasBearing()) uRef.child("heading").setValue(loc.bearing)
                            uRef.child("lastUpdate").setValue(System.currentTimeMillis())
                        }
                    }
                }

            // 3. Fast high-accuracy continuous updates: 1-second interval, 500ms min interval
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            )
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            android.util.Log.e("DriveDash", "Location permission missing", e)
        }
    }

    // Deterministic palette so every rider consistently gets a unique, vibrant color across all phones
    private val convoyPalette = listOf(
        "#1A73E8", // Vibrant Blue (Reserved for current user)
        "#E65100", // Deep Orange
        "#00897B", // Teal
        "#8E24AA", // Purple
        "#D81B60", // Pink
        "#43A047", // Green
        "#FDD835", // Yellow / Amber
        "#3949AB", // Indigo
        "#00ACC1", // Cyan
        "#FB8C00", // Amber Orange
        "#6D4C41", // Brown
        "#546E7A"  // Blue Grey
    )

    private fun riderColorHexForUid(uid: String, isMe: Boolean): String {
        if (isMe) return convoyPalette[0] // User always sees themselves in Primary Blue
        // Deterministically map other riders' UIDs to distinct colors from the palette
        val index = ((uid.hashCode() and 0x7FFFFFFF) % (convoyPalette.size - 1)) + 1
        return convoyPalette[index]
    }

    override fun onDestroy() {
        super.onDestroy()
        callManager?.stop()
        callManager = null
        sosAlarmHandler.removeCallbacks(sosAlarmRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        DriveDashForegroundService.stop(this)
    }
}
