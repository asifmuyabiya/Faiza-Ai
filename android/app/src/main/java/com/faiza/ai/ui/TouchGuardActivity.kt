package com.faiza.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.guardian.TouchGuard
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

/** MAYA "Touch Guard": arm the phone while you're away. */
class TouchGuardActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(col)
        setContentView(root)

        // Top bar
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(10))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back)
                background = null
                imageTintList = getColorStateList(R.color.text_primary)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(TextView(context).apply {
                text = "Touch Guard"
                textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp(8), 0, 0, 0)
            })
        })

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        card.addView(box)
        col.addView(card)

        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply { text = "✋"; textSize = 24f; setPadding(0, 0, dp(12), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "Touch Guard"
                    textSize = 18f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(getColor(R.color.text_primary))
                })
                addView(TextView(context).apply {
                    text = "Watch the phone while you are away from it"
                    textSize = 13f
                    setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, dp(2), 0, 0)
                })
            })
        })

        box.addView(TextView(this).apply {
            text = "When armed, anyone who wakes the screen, picks the phone up or pulls the charger out gets photographed with the front camera. Faiza then tells you out loud that someone touched your phone, warns whoever is holding it, and sounds a siren."
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(16), 0, dp(16))
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val switch = SwitchMaterial(this).apply {
            isChecked = prefs.isTouchGuard
            setOnCheckedChangeListener { _, on ->
                prefs.isTouchGuard = on
                if (on && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), 61)
                }
                TouchGuard.refresh(applicationContext)
                if (on) Toast.makeText(this@TouchGuardActivity, "Armed. Step away — she's watching.", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "Enable Touch Guard"
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
            })
            addView(TextView(context).apply {
                text = "Off by default. Nothing below does anything until this is on."
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(2), 0, 0)
            })
        })
        row.addView(switch)
        box.addView(row)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
