package com.faiza.ai.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.databinding.ActivityMainBinding
import com.faiza.ai.license.LicenseResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash / Entry screen.
 * Checks license status and routes to the correct activity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { FaizaApp.instance.prefs }
    private val licenseManager by lazy { FaizaApp.instance.licenseManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pulse animation for logo
        binding.tvLogo.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(800)
            .withEndAction {
                binding.tvLogo.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(800)
                    .start()
            }.start()

        checkAccessAndRoute()
    }

    private fun checkAccessAndRoute() {
        lifecycleScope.launch {
            binding.tvLoading.text = "Checking license…"

            // Small delay for splash feel
            delay(1200)

            val savedKey = prefs.licenseKey

            if (savedKey != null) {
                // Has a saved license - verify it
                binding.tvLoading.text = "Verifying license…"
                val result = licenseManager.verifyLicense(savedKey)

                when (result) {
                    is LicenseResult.Success,
                    is LicenseResult.CachedValid -> {
                        binding.tvLoading.text = "Welcome back"
                        delay(500)
                        goToDashboard()
                    }
                    else -> {
                        // License invalid or expired
                        binding.tvLoading.text = "License needs renewal"
                        delay(800)
                        goToLicense()
                    }
                }
            } else if (prefs.hasValidAccess()) {
                // Trial is still active
                binding.tvLoading.text = "Free trial"
                delay(500)
                goToDashboard()
            } else {
                // No license, no trial - go to activation
                delay(300)
                goToLicense()
            }
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun goToLicense() {
        startActivity(Intent(this, LicenseActivity::class.java))
        finish()
    }
}
