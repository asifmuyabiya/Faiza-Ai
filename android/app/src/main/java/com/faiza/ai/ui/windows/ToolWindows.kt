package com.faiza.ai.ui.windows

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.faiza.ai.R
import com.faiza.ai.ai.skills.Calculator
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream

/** 📷 A floating camera: front/back preview; Faiza can look through it; take a photo. */
class CameraWindow(ctx: Context) : FloatWindow(ctx, WindowKind.CAMERA), LifecycleOwner {

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    override val smallSize = 220 to 320
    override val largeSize = 320 to 480

    private lateinit var preview: PreviewView
    private var front = true
    var status: String = ""
        private set

    override fun buildContent(container: FrameLayout) {
        preview = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        container.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, px(4), 0, px(8))
        }
        fun btn(t: String, a: () -> Unit) = TextView(ctx).apply {
            text = t; textSize = 20f; gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_pill)
            layoutParams = LinearLayout.LayoutParams(px(46), px(46)).apply { marginStart = px(6); marginEnd = px(6) }
            setOnClickListener { a() }
        }
        bar.addView(btn("🔄") { flip() })
        bar.addView(btn("👁") { com.faiza.ai.service.FloatingOrbService.instance?.lookThroughCamera() })
        container.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        registry.currentState = Lifecycle.State.RESUMED
        bind()
    }

    fun useFront(f: Boolean) { if (f != front) { front = f; bind() } }
    fun flip() { front = !front; bind() }

    private fun bind() {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            status = runCatching {
                val provider = future.get()
                val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(this, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, p)
                "ok"
            }.getOrElse { "error: ${it.message}" }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** The current picture as a small JPEG (for Faiza to look at), or null if the camera isn't showing anything. */
    fun frameJpeg(): ByteArray? {
        val bmp: Bitmap = preview.bitmap ?: return null
        val scale = 768f / maxOf(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    override fun onClosed() {
        runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** 🧮 A pocket calculator; Faiza's spoken sums show up here too. */
class CalculatorWindow(ctx: Context) : FloatWindow(ctx, WindowKind.CALCULATOR) {

    override val smallSize = 250 to 360
    override val largeSize = 300 to 440
    private lateinit var exprView: TextView
    private lateinit var resultView: TextView
    private var expr = ""

    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(10), px(8), px(10), px(8)) }
        exprView = TextView(ctx).apply {
            textSize = 15f; gravity = Gravity.END; maxLines = 3
            setTextColor(ctx.getColor(R.color.text_secondary))
        }
        resultView = TextView(ctx).apply {
            textSize = 28f; gravity = Gravity.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ctx.getColor(R.color.text_primary)); text = "0"
        }
        col.addView(exprView); col.addView(resultView)
        val grid = GridLayout(ctx).apply { columnCount = 4; setPadding(0, px(6), 0, 0) }
        listOf("C", "(", ")", "⌫", "7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ".", "%", "+", "=")
            .forEach { k ->
                grid.addView(TextView(ctx).apply {
                    text = k; textSize = 17f; gravity = Gravity.CENTER
                    setTextColor(ctx.getColor(if (k in setOf("=", "+", "−", "×", "÷")) R.color.primary_light else R.color.text_primary))
                    setBackgroundResource(R.drawable.bg_input)
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0; height = px(40)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, if (k == "=") 4 else 1, 1f)
                        setMargins(px(3), px(3), px(3), px(3))
                    }
                    setOnClickListener { press(k) }
                })
            }
        col.addView(grid)
        container.addView(col)
    }

    private fun press(k: String) {
        when (k) {
            "C" -> { expr = ""; resultView.text = "0" }
            "⌫" -> expr = expr.dropLast(1)
            "=" -> { compute(); return }
            "×" -> expr += "*"
            "÷" -> expr += "/"
            "−" -> expr += "-"
            else -> expr += k
        }
        exprView.text = expr.replace("*", "×").replace("/", "÷")
        runCatching { resultView.text = Calculator.pretty(Calculator.eval(expr)) }
    }

    private fun compute() {
        resultView.text = runCatching { Calculator.pretty(Calculator.eval(expr)) }.getOrElse { "Error" }
    }

    /** Faiza calculated something: show the sum and answer. */
    fun showSum(expression: String, result: String) {
        expr = Calculator.normalize(expression)
        exprView.text = expr.replace("*", "×").replace("/", "÷")
        resultView.text = result
    }
}

/** 🌐 Faiza's own little browser for research: Google and pages inside a floating window. */
class BrowserWindow(ctx: Context) : FloatWindow(ctx, WindowKind.BROWSER) {

    override val smallSize = 300 to 420
    override val largeSize = 360 to 600
    override val wantsKeyboard = true
    private lateinit var web: WebView
    private lateinit var address: EditText

    @SuppressLint("SetJavaScriptEnabled")
    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(6), px(4), px(6), px(4)) }
        top.addView(TextView(ctx).apply {
            text = "‹"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(px(32), px(36))
            setOnClickListener { if (web.canGoBack()) web.goBack() }
        })
        address = EditText(ctx).apply {
            hint = "Search or address"; textSize = 13f; maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(ctx.getColor(R.color.text_primary)); setHintTextColor(ctx.getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input); setPadding(px(10), px(6), px(10), px(6))
            layoutParams = LinearLayout.LayoutParams(0, px(36), 1f)
            setSingleLine()
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        top.addView(address)
        col.addView(top)
        web = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { address.setText(url.orEmpty()) }
            }
        }
        col.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(col)
    }

    /** A web address, or words to search on Google. */
    fun go(input: String) {
        val t = input.trim()
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$").matches(t) -> "https://$t"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        web.loadUrl(url)
    }

    /** The page's title, text and links (for Faiza to read). */
    suspend fun read(): String {
        val wait = CompletableDeferred<String>()
        web.evaluateJavascript(
            "(function(){var l=[];var a=document.querySelectorAll('a[href]');for(var i=0;i<a.length&&l.length<15;i++){" +
                    "var t=(a[i].innerText||'').trim();if(t.length>3&&a[i].href.indexOf('http')==0)l.push((l.length+1)+'. '+t.substring(0,90)+' — '+a[i].href);}" +
                    "return document.title+'\\n'+location.href+'\\n\\n'+(document.body?document.body.innerText:'').substring(0,7000)+'\\n\\nLINKS:\\n'+l.join('\\n');})()"
        ) { wait.complete(it) }
        val raw = wait.await()
        // evaluateJavascript returns a JSON string literal.
        return runCatching { com.google.gson.JsonParser.parseString(raw).asString }.getOrDefault(raw)
    }

    override fun onClosed() { runCatching { web.destroy() } }
}
