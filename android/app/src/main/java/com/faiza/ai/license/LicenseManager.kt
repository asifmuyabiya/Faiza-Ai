package com.faiza.ai.license

import android.content.Context
import com.faiza.ai.FaizaApp
import com.faiza.ai.util.PrefsManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Manages license verification with the backend server.
 * Supports: License key activation, trial checking, device binding.
 */
class LicenseManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val prefs = FaizaApp.instance.prefs
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ====== Data Classes ======

    data class LicenseVerifyRequest(
        @SerializedName("license_key") val licenseKey: String,
        @SerializedName("device_id") val deviceId: String
    )

    data class LicenseVerifyResponse(
        val valid: Boolean,
        val plan: String? = null,
        @SerializedName("plan_name") val planName: String? = null,
        val features: List<String>? = null,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("license_key") val licenseKey: String? = null,
        val error: String? = null,
        @SerializedName("bound_device") val boundDevice: Boolean = false
    )

    data class TrialCheckRequest(
        @SerializedName("device_id") val deviceId: String
    )

    data class TrialCheckResponse(
        @SerializedName("has_trial") val hasTrial: Boolean = false,
        @SerializedName("minutes_remaining") val minutesRemaining: Float = 0f,
        @SerializedName("minutes_used") val minutesUsed: Float = 0f,
        @SerializedName("trial_expired") val trialExpired: Boolean = false,
        val message: String? = null,
        val error: String? = null
    )

    data class TrialUpdateRequest(
        @SerializedName("device_id") val deviceId: String,
        val minutes: Float
    )

    // ====== License Operations ======

    /**
     * Verify a license key with the server
     */
    suspend fun verifyLicense(licenseKey: String): LicenseResult {
        if (FaizaApp.serverMissing) return LicenseResult.Invalid("License server isn't set up in this build yet. Please contact the seller.")
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(
                    LicenseVerifyRequest(
                        licenseKey = licenseKey.trim().uppercase(),
                        deviceId = prefs.getDeviceId()
                    )
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("${FaizaApp.SERVER_URL}/api/license/verify")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val result = gson.fromJson(body, LicenseVerifyResponse::class.java)

                if (result.valid) {
                    // Save license locally
                    prefs.licenseKey = result.licenseKey ?: licenseKey
                    prefs.licensePlan = result.plan
                    prefs.isLicenseValid = true
                    prefs.licenseExpiry = result.expiresAt

                    LicenseResult.Success(
                        plan = result.planName ?: "Unknown",
                        features = result.features ?: emptyList(),
                        expiresAt = result.expiresAt
                    )
                } else {
                    prefs.isLicenseValid = false
                    if (result.boundDevice) {
                        LicenseResult.DeviceBound(result.error ?: "License bound to another device")
                    } else {
                        LicenseResult.Invalid(result.error ?: "Invalid license key")
                    }
                }
            } catch (e: Exception) {
                // If offline, check local cache
                if (prefs.isLicenseValid && prefs.licenseKey != null) {
                    LicenseResult.CachedValid(
                        plan = prefs.licensePlan ?: "unknown",
                        message = "Offline mode - using cached license"
                    )
                } else {
                    LicenseResult.Error("Cannot connect to server: ${e.message}")
                }
            }
        }
    }

    /**
     * Check free trial status
     */
    suspend fun checkTrial(): TrialResult {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(
                    TrialCheckRequest(deviceId = prefs.getDeviceId())
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("${FaizaApp.SERVER_URL}/api/trial/check")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val result = gson.fromJson(body, TrialCheckResponse::class.java)

                if (result.hasTrial) {
                    prefs.trialMinutesUsed = result.minutesUsed
                    prefs.isTrialExpired = false
                    TrialResult.Active(
                        minutesRemaining = result.minutesRemaining,
                        minutesUsed = result.minutesUsed
                    )
                } else {
                    prefs.isTrialExpired = true
                    TrialResult.Expired(result.message ?: "Trial expired")
                }
            } catch (e: Exception) {
                // Offline fallback
                if (!prefs.isTrialExpired && prefs.trialMinutesUsed < 10f) {
                    TrialResult.Active(
                        minutesRemaining = 10f - prefs.trialMinutesUsed,
                        minutesUsed = prefs.trialMinutesUsed
                    )
                } else {
                    TrialResult.Error("Cannot connect: ${e.message}")
                }
            }
        }
    }

    /**
     * Update trial usage on server
     */
    suspend fun updateTrialUsage(minutesToAdd: Float) {
        withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(
                    TrialUpdateRequest(
                        deviceId = prefs.getDeviceId(),
                        minutes = minutesToAdd
                    )
                ).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("${FaizaApp.SERVER_URL}/api/trial/update")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute()
            } catch (_: Exception) {
                // Update locally even if server is unreachable
            }
            prefs.trialMinutesUsed += minutesToAdd
            if (prefs.trialMinutesUsed >= 10f) {
                prefs.isTrialExpired = true
            }
        }
    }

    /**
     * Quick check if user has valid access (license or trial)
     */
    fun hasValidAccess(): Boolean {
        return prefs.hasValidAccess()
    }

    /**
     * Check if current plan is Pro or Lifetime
     */
    fun isPro(): Boolean {
        if (FaizaApp.UNLOCK_ALL_FOR_TESTING) return true
        val plan = prefs.licensePlan?.lowercase()
        return plan == "pro" || plan == "lifetime"
    }
}

// ====== Result Types ======

sealed class LicenseResult {
    data class Success(
        val plan: String,
        val features: List<String>,
        val expiresAt: String?
    ) : LicenseResult()

    data class CachedValid(
        val plan: String,
        val message: String
    ) : LicenseResult()

    data class Invalid(val error: String) : LicenseResult()
    data class DeviceBound(val error: String) : LicenseResult()
    data class Error(val error: String) : LicenseResult()
}

sealed class TrialResult {
    data class Active(
        val minutesRemaining: Float,
        val minutesUsed: Float
    ) : TrialResult()

    data class Expired(val message: String) : TrialResult()
    data class Error(val error: String) : TrialResult()
}
