package com.faiza.ai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.ui.orb.AvatarFace
import com.faiza.ai.ui.orb.AvatarStore
import com.faiza.ai.ui.orb.AvatarView
import com.faiza.ai.ui.orb.OrbMode
import com.faiza.ai.ui.orb.OrbTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.sin

/** Avatar: pick a picture, tap her eyes and mouth once, and she comes alive in place of the orb. */
class AvatarSetupActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var tapView: TapView
    private lateinit var hint: TextView
    private lateinit var preview: AvatarView
    private lateinit var saveBtn: MaterialButton
    private lateinit var useSwitch: SwitchMaterial

    private val pick = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { usePicture(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(col)
        setContentView(root)

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back)
                background = null
                imageTintList = getColorStateList(R.color.text_primary)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(TextView(context).apply {
                text = "Avatar"
                textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp(8), 0, 0, 0)
            })
        })
        col.addView(caption(
            "Choose a picture of her. Tap her left eye, right eye and mouth once — then she blinks, breathes, sways " +
                    "and moves her lips while she talks, in place of the orb. Use art you're allowed to use, of an adult."
        ))

        // Use avatar switch
        useSwitch = SwitchMaterial(this).apply {
            text = "Show the avatar instead of the orb"
            setTextColor(getColor(R.color.text_primary))
            textSize = 15f
            isChecked = prefs.isAvatarMode
            setOnCheckedChangeListener { _, on ->
                if (on && !AvatarStore.isReady(this@AvatarSetupActivity)) {
                    isChecked = false
                    Toast.makeText(this@AvatarSetupActivity, "First choose a picture and tap the eyes and mouth", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                prefs.isAvatarMode = on
                FloatingOrbService.instance?.onAvatarChanged()
            }
            setPadding(dp(4), dp(14), dp(4), dp(6))
        }
        col.addView(useSwitch)

        col.addView(MaterialButton(this).apply {
            text = "Choose a picture"
            setOnClickListener { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        })

        hint = caption("")
        hint.setTextColor(getColor(R.color.primary_light))
        hint.textSize = 15f
        col.addView(hint)

        tapView = TapView(this) { step -> onStep(step) }
        col.addView(tapView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)).apply { topMargin = dp(6) })

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Tap again"
                setOnClickListener { tapView.resetPoints() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            })
            saveBtn = MaterialButton(context).apply {
                text = "Save"
                isEnabled = false
                setOnClickListener { save() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(saveBtn)
            setPadding(0, dp(10), 0, 0)
        })

        col.addView(caption("Preview — she talks here so you can see the lips and blinks:"))
        preview = AvatarView(this).apply {
            theme = OrbTheme.from(prefs.orbTheme)
            mode = OrbMode.SPEAKING
            // A pretend voice: syllables rising and falling.
            val start = System.nanoTime()
            voiceLevel = {
                val t = (System.nanoTime() - start) / 1e9f
                ((sin(t * 9f) * 0.5f + 0.5f) * (sin(t * 1.7f) * 0.4f + 0.6f)).coerceIn(0f, 1f)
            }
        }
        col.addView(preview, LinearLayout.LayoutParams(dp(180), dp(180)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        // Existing avatar: show it ready.
        if (AvatarStore.file(this).exists()) {
            AvatarStore.loadBitmap(this)?.let { tapView.setPicture(it, AvatarStore.face()) }
        }
        refreshHint()
    }

    private fun usePicture(uri: Uri) {
        val ok = runCatching {
            contentResolver.openInputStream(uri)!!.use { input ->
                AvatarStore.file(this).outputStream().use { input.copyTo(it) }
            }
            true
        }.getOrDefault(false)
        if (!ok) {
            Toast.makeText(this, "Couldn't open that picture", Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = AvatarStore.loadBitmap(this) ?: return
        prefs.avatarFace = null
        tapView.setPicture(bmp, null)
        preview.reload()
        refreshHint()
    }

    private fun onStep(@Suppress("UNUSED_PARAMETER") step: Int) {
        refreshHint()
    }

    private fun refreshHint() {
        val n = tapView.points.size
        hint.text = when {
            !tapView.hasPicture -> "1. Choose a picture"
            n == 0 -> "2. Tap her LEFT eye (on your left)"
            n == 1 -> "3. Tap her RIGHT eye"
            n == 2 -> "4. Tap the middle of her MOUTH"
            else -> "Done — tap Save"
        }
        saveBtn.isEnabled = tapView.hasPicture && n == 3
    }

    private fun save() {
        val p = tapView.points
        if (p.size != 3) return
        AvatarStore.save(AvatarFace(p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second))
        prefs.isAvatarMode = true
        useSwitch.isChecked = true
        preview.reload()
        FloatingOrbService.instance?.onAvatarChanged()
        Toast.makeText(this, "Saved — she's your avatar now", Toast.LENGTH_SHORT).show()
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.25f)
        setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Shows the picture; collects up to three taps as 0..1 picture coordinates. */
    class TapView(context: Context, private val onChange: (Int) -> Unit) : View(context) {
        private var bmp: Bitmap? = null
        val points = mutableListOf<Pair<Float, Float>>()
        val hasPicture get() = bmp != null
        private val dst = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * resources.displayMetrics.density
            typeface = Typeface.DEFAULT_BOLD
        }

        fun setPicture(b: Bitmap, face: AvatarFace?) {
            bmp = b
            points.clear()
            face?.let {
                points += it.leftEyeX to it.leftEyeY
                points += it.rightEyeX to it.rightEyeY
                points += it.mouthX to it.mouthY
            }
            invalidate()
            onChange(points.size)
        }

        fun resetPoints() {
            points.clear()
            invalidate()
            onChange(0)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.rgb(17, 17, 24))
            val b = bmp ?: return
            val s = minOf(width / b.width.toFloat(), height / b.height.toFloat())
            val w = b.width * s
            val h = b.height * s
            dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
            canvas.drawBitmap(b, null, dst, paint)
            val names = listOf("L", "R", "M")
            points.forEachIndexed { i, (x, y) ->
                val px = dst.left + x * dst.width()
                val py = dst.top + y * dst.height()
                dot.color = Color.argb(220, 139, 92, 246)
                canvas.drawCircle(px, py, 11f * resources.displayMetrics.density, dot)
                canvas.drawText(names[i], px - label.textSize * 0.32f, py + label.textSize * 0.36f, label)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP || bmp == null || points.size >= 3) return true
            if (!dst.contains(event.x, event.y)) return true
            points += ((event.x - dst.left) / dst.width()) to ((event.y - dst.top) / dst.height())
            invalidate()
            onChange(points.size)
            return true
        }
    }
}
