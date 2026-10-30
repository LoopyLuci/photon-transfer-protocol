package com.prism.ot.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prism.ot.data.model.TransferMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prismDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prism_preferences"
)

/**
 * Persistent user and session preferences. All setters are suspend to avoid
 * accidentally doing disk I/O on the main thread.
 */
@Singleton
class PrismPreferences @Inject constructor(
    private val context: Context,
) {

    // ---- User-facing settings ----
    val preferredTransferMode: Flow<TransferMode> = context.prismDataStore.data.map {
        val raw = it[KEY_PREFERRED_MODE] ?: TransferMode.KINETIC.name
        runCatching { TransferMode.valueOf(raw) }.getOrDefault(TransferMode.KINETIC)
    }

    val autoDetectMode: Flow<Boolean> = context.prismDataStore.data.map {
        it[KEY_AUTO_DETECT_MODE] ?: true
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.prismDataStore.data.map {
        it[KEY_HAPTIC] ?: true
    }

    val soundFeedbackEnabled: Flow<Boolean> = context.prismDataStore.data.map {
        it[KEY_SOUND] ?: true
    }

    val keepScreenOn: Flow<Boolean> = context.prismDataStore.data.map {
        it[KEY_KEEP_SCREEN_ON] ?: true
    }

    val debugOverlayEnabled: Flow<Boolean> = context.prismDataStore.data.map {
        it[KEY_DEBUG_OVERLAY] ?: false
    }

    // ---- Tuning ----
    val preferredFaceBytes: Flow<Int> = context.prismDataStore.data.map {
        it[KEY_FACE_BYTES] ?: com.prism.ot.core.PrismConstants.KINETIC_DEFAULT_FACE_BYTES
    }

    val preferredFacesPerMass: Flow<Int> = context.prismDataStore.data.map {
        it[KEY_FACES] ?: com.prism.ot.core.PrismConstants.KINETIC_DEFAULT_FACES
    }

    val preferredStaticDpi: Flow<Int> = context.prismDataStore.data.map {
        it[KEY_STATIC_DPI] ?: com.prism.ot.core.PrismConstants.STATIC_DEFAULT_DPI
    }

    val preferredRotationHz: Flow<Float> = context.prismDataStore.data.map {
        it[KEY_ROTATION_HZ] ?: com.prism.ot.core.PrismConstants.KINETIC_DEFAULT_ROTATION_HZ
    }

    // ---- Derived ----
    val lastKnownCameraFps: Flow<Float> = context.prismDataStore.data.map {
        it[KEY_LAST_CAMERA_FPS] ?: 0f
    }

    val totalBytesTransferred: Flow<Long> = context.prismDataStore.data.map {
        it[KEY_TOTAL_BYTES] ?: 0L
    }

    val totalSessionsCompleted: Flow<Int> = context.prismDataStore.data.map {
        it[KEY_TOTAL_SESSIONS] ?: 0
    }

    val lastUsedMotorMac: Flow<String?> = context.prismDataStore.data.map {
        it[KEY_MOTOR_MAC]
    }

    // ---- Setters ----
    suspend fun setPreferredTransferMode(mode: TransferMode) = edit {
        it[KEY_PREFERRED_MODE] = mode.name
    }

    suspend fun setAutoDetectMode(enabled: Boolean) = edit { it[KEY_AUTO_DETECT_MODE] = enabled }
    suspend fun setHapticFeedbackEnabled(enabled: Boolean) = edit { it[KEY_HAPTIC] = enabled }
    suspend fun setSoundFeedbackEnabled(enabled: Boolean) = edit { it[KEY_SOUND] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    suspend fun setDebugOverlayEnabled(enabled: Boolean) = edit { it[KEY_DEBUG_OVERLAY] = enabled }

    suspend fun setPreferredFaceBytes(bytes: Int) = edit {
        it[KEY_FACE_BYTES] = bytes.coerceIn(
            com.prism.ot.core.PrismConstants.KINETIC_MIN_FACE_BYTES,
            com.prism.ot.core.PrismConstants.KINETIC_MAX_FACE_BYTES
        )
    }

    suspend fun setPreferredFacesPerMass(faces: Int) = edit {
        it[KEY_FACES] = faces.coerceIn(
            com.prism.ot.core.PrismConstants.KINETIC_MIN_FACES,
            com.prism.ot.core.PrismConstants.KINETIC_MAX_FACES
        )
    }

    suspend fun setPreferredStaticDpi(dpi: Int) = edit {
        it[KEY_STATIC_DPI] = dpi.coerceIn(300, com.prism.ot.core.PrismConstants.STATIC_MAX_DPI)
    }

    suspend fun setPreferredRotationHz(hz: Float) = edit {
        it[KEY_ROTATION_HZ] = hz.coerceIn(
            com.prism.ot.core.PrismConstants.KINETIC_DEFAULT_ROTATION_HZ,
            com.prism.ot.core.PrismConstants.KINETIC_MAX_ROTATION_HZ
        )
    }

    suspend fun setLastKnownCameraFps(fps: Float) = edit { it[KEY_LAST_CAMERA_FPS] = fps }
    suspend fun setLastUsedMotorMac(mac: String?) = edit { it[KEY_MOTOR_MAC] = mac }

    suspend fun recordSessionCompletion(bytesTransferred: Long) = edit {
        val sessions = (it[KEY_TOTAL_SESSIONS] ?: 0) + 1
        val total = (it[KEY_TOTAL_BYTES] ?: 0L) + bytesTransferred
        it[KEY_TOTAL_SESSIONS] = sessions
        it[KEY_TOTAL_BYTES] = total
    }

    suspend fun clearAll() = edit { it.clear() }

    suspend fun snapshot(): Map<String, Any?> = context.prismDataStore.data.first().let { prefs ->
        mapOf(
            "preferredTransferMode" to (prefs[KEY_PREFERRED_MODE] ?: "KINETIC"),
            "autoDetectMode" to (prefs[KEY_AUTO_DETECT_MODE] ?: true),
            "faceBytes" to (prefs[KEY_FACE_BYTES] ?: 7_500),
            "faces" to (prefs[KEY_FACES] ?: 16),
            "lastCameraFps" to (prefs[KEY_LAST_CAMERA_FPS] ?: 0f),
        )
    }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.prismDataStore.edit { prefs -> block(prefs) }
    }

    private companion object {
        val KEY_PREFERRED_MODE = stringPreferencesKey("pref_mode")
        val KEY_AUTO_DETECT_MODE = booleanPreferencesKey("auto_detect_mode")
        val KEY_HAPTIC = booleanPreferencesKey("haptic")
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_DEBUG_OVERLAY = booleanPreferencesKey("debug_overlay")
        val KEY_FACE_BYTES = intPreferencesKey("face_bytes")
        val KEY_FACES = intPreferencesKey("faces")
        val KEY_STATIC_DPI = intPreferencesKey("static_dpi")
        val KEY_ROTATION_HZ = floatPreferencesKey("rotation_hz")
        val KEY_LAST_CAMERA_FPS = floatPreferencesKey("last_camera_fps")
        val KEY_TOTAL_BYTES = longPreferencesKey("total_bytes")
        val KEY_TOTAL_SESSIONS = intPreferencesKey("total_sessions")
        val KEY_MOTOR_MAC = stringPreferencesKey("motor_mac")
    }
}
