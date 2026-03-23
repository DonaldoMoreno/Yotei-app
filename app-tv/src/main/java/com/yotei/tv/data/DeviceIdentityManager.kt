package com.yotei.tv.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Manages device identity and secrets securely.
 *
 * Responsibilities:
 * - Generate device_id on first launch (UUID v4)
 * - Store device_id persistently
 * - Store device_secret encrypted
 * - Store barbershop_id after successful pairing
 * - Track first launch state
 */
class DeviceIdentityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "device_identity",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_MODEL = "device_model"
        private const val KEY_BARBERSHOP_ID = "barbershop_id"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_FIRST_PAIRING_AT = "first_pairing_at"
    }

    /**
     * Initialize device on first launch.
     * Returns true if this is first launch, false otherwise.
     */
    fun initializeDevice(deviceName: String, deviceModel: String): Boolean {
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            val deviceId = UUID.randomUUID().toString()
            prefs.edit().apply {
                putString(KEY_DEVICE_ID, deviceId)
                putString(KEY_DEVICE_NAME, deviceName)
                putString(KEY_DEVICE_MODEL, deviceModel)
                putBoolean(KEY_FIRST_LAUNCH, false)
                apply()
            }
            return true
        }
        return false
    }

    /**
     * Get device unique identifier.
     * Returns null if not yet initialized.
     */
    fun getDeviceId(): String? {
        return prefs.getString(KEY_DEVICE_ID, null)
    }

    /**
     * Get stored device secret (encrypted).
     * Returns null if not yet paired.
     */
    fun getDeviceSecret(): String? {
        return prefs.getString(KEY_DEVICE_SECRET, null)
    }

    /**
     * Store device secret after successful pairing.
     * Called after redeemPairingCode returns binding with device_secret.
     */
    fun setDeviceSecret(secret: String) {
        prefs.edit().putString(KEY_DEVICE_SECRET, secret).apply()
    }

    /**
     * Get friendly device name.
     */
    fun getDeviceName(): String? {
        return prefs.getString(KEY_DEVICE_NAME, null)
    }

    /**
     * Get device model string.
     */
    fun getDeviceModel(): String? {
        return prefs.getString(KEY_DEVICE_MODEL, null)
    }

    /**
     * Get barbershop this device is paired to.
     * Returns null if not yet paired.
     */
    fun getBarbershopId(): String? {
        return prefs.getString(KEY_BARBERSHOP_ID, null)
    }

    /**
     * Store barbershop after successful pairing.
     */
    fun setBarbershopId(barbershopId: String) {
        prefs.edit().putString(KEY_BARBERSHOP_ID, barbershopId).apply()
    }

    /**
     * Check if device is paired to a display.
     * (Used to determine which screen to show.)
     */
    fun isPaired(): Boolean {
        return !getDeviceSecret().isNullOrEmpty() &&
               !getBarbershopId().isNullOrEmpty()
    }

    /**
     * Check if this is first launch.
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Get timestamp of first successful pairing.
     */
    fun getFirstPairingAt(): String? {
        return prefs.getString(KEY_FIRST_PAIRING_AT, null)
    }

    /**
     * Store first pairing timestamp.
     */
    fun setFirstPairingAt(timestamp: String) {
        prefs.edit().putString(KEY_FIRST_PAIRING_AT, timestamp).apply()
    }

    /**
     * Clear all device data (for debug/reset).
     * In production, use only after user confirms deletion.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
