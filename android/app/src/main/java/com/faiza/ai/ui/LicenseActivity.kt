package com.faiza.ai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.BuildConfig
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.databinding.ActivityLicenseBinding
import com.faiza.ai.license.LicenseResult
import com.faiza.ai.license.TrialResult
import kotlinx.coroutines.launch

class LicenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLicenseBinding
    private val licenseManager by lazy { FaizaApp.instance.licenseManager }
    private val prefs by lazy { FaizaApp.instance.prefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLicenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkExistingAccess()
    }

    private fun setupUI() {
        // Activate button
        binding.btnActivate.setOnClickListener {
            val key = binding.etLicenseKey.text.toString().trim().uppercase()
            if (key.isEmpty()) {
                showStatus("Please enter a license key", isError = true)
                return@setOnClickListener
            }
            activateLicense(key)
        }

        // Free trial button
        binding.btnFreeTrial.setOnClickListener {
            startFreeTrial()
        }

        // Test builds only: unlock Pro without the license server (which only runs on the developer PC).
        if (BuildConfig.DEBUG) {
            binding.btnDevTest.visibility = View.VISIBLE
            binding.btnDevTest.setOnClickListener {
                prefs.licenseKey = "FAIZA-DEV-TEST"
                prefs.licensePlan = "pro"
                prefs.licenseExpiry = null
                prefs.isLicenseValid = true
                Toast.makeText(this, "Developer test mode: Pro unlocked", Toast.LENGTH_SHORT).show()
                goToDashboard()
            }
        }

        // Buy license link
        binding.tvBuyLicense.setOnClickListener {
            // Open website to buy license
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://getfaiza.ai/pricing"))
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Visit getfaiza.ai to purchase", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkExistingAccess() {
        // Check if already has valid license
        val savedKey = prefs.licenseKey
        if (savedKey != null && prefs.isLicenseValid) {
            // Re-verify with server
            lifecycleScope.launch {
                val result = licenseManager.verifyLicense(savedKey)
                if (result is LicenseResult.Success || result is LicenseResult.CachedValid) {
                    goToDashboard()
                }
            }
        }

        // Update trial info
        lifecycleScope.launch {
            val trial = licenseManager.checkTrial()
            when (trial) {
                is TrialResult.Active -> {
                    binding.tvTrialInfo.text =
                        "⏱️ ${String.format("%.1f", trial.minutesRemaining)} minutes remaining in trial"
                }
                is TrialResult.Expired -> {
                    binding.btnFreeTrial.isEnabled = false
                    binding.btnFreeTrial.text = "Trial ended"
                    binding.tvTrialInfo.text = "Purchase a license to continue using Faiza AI"
                }
                is TrialResult.Error -> {
                    // Offline - check local
                    if (!prefs.isTrialExpired) {
                        val remaining = 10f - prefs.trialMinutesUsed
                        binding.tvTrialInfo.text =
                            "⏱️ ~${String.format("%.1f", remaining)} minutes remaining (offline)"
                    }
                }
            }
        }
    }

    private fun activateLicense(key: String) {
        binding.btnActivate.isEnabled = false
        binding.btnActivate.text = "Verifying..."
        showStatus("Connecting to server...", isError = false)

        lifecycleScope.launch {
            val result = licenseManager.verifyLicense(key)

            binding.btnActivate.isEnabled = true
            binding.btnActivate.text = "Activate License"

            when (result) {
                is LicenseResult.Success -> {
                    showStatus(
                        "License activated!\nPlan: ${result.plan}\nExpires: ${result.expiresAt?.take(10) ?: "Never"}",
                        isError = false
                    )
                    // Short delay then go to dashboard
                    binding.root.postDelayed({ goToDashboard() }, 1500)
                }
                is LicenseResult.CachedValid -> {
                    showStatus("${result.message}", isError = false)
                    binding.root.postDelayed({ goToDashboard() }, 1000)
                }
                is LicenseResult.Invalid -> {
                    showStatus("${result.error}", isError = true)
                }
                is LicenseResult.DeviceBound -> {
                    showStatus("🔒 ${result.error}", isError = true)
                }
                is LicenseResult.Error -> {
                    showStatus("${result.error}", isError = true)
                }
            }
        }
    }

    private fun startFreeTrial() {
        binding.btnFreeTrial.isEnabled = false
        binding.btnFreeTrial.text = "Starting trial..."

        lifecycleScope.launch {
            val trial = licenseManager.checkTrial()

            when (trial) {
                is TrialResult.Active -> {
                    Toast.makeText(
                        this@LicenseActivity,
                        "Trial active · ${String.format("%.0f", trial.minutesRemaining)} min left",
                        Toast.LENGTH_SHORT
                    ).show()
                    goToDashboard()
                }
                is TrialResult.Expired -> {
                    binding.btnFreeTrial.text = "Trial ended"
                    showStatus("Trial has expired. Please purchase a license.", isError = true)
                }
                is TrialResult.Error -> {
                    // Allow offline trial
                    if (!prefs.isTrialExpired) {
                        goToDashboard()
                    } else {
                        binding.btnFreeTrial.isEnabled = true
                        binding.btnFreeTrial.text = "Try free for 10 minutes"
                        showStatus("Can't verify the trial. Check your internet connection.", isError = true)
                    }
                }
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.tvStatus.apply {
            visibility = View.VISIBLE
            text = message
            setTextColor(
                getColor(if (isError) R.color.danger else R.color.success)
            )
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
