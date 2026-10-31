package com.faiza.ai.ai.code

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * The "runner" for websites: opens the page in a real (hidden) browser at phone size, collects JavaScript errors
 * and broken files, runs the project's faiza.test.js, and takes a screenshot so the reviewer can SEE the design.
 */
object WebChecker {

    data class Report(
        val loaded: Boolean,
        val errors: List<String>,
        val testsPassed: Int,
        val testsFailed: List<String>,
        val testsFound: Boolean,
        val screenshotB64: String?,
        val title: String
    ) {
        val clean get() = loaded && errors.isEmpty() && testsFailed.isEmpty()
        fun text(): String = buildString {
            appendLine(if (loaded) "Page loaded: \"$title\"" else "PAGE DID NOT LOAD")
            if (errors.isEmpty()) appendLine("Console/network errors: none")
            else { appendLine("Console/network errors (${errors.size}):"); errors.take(25).forEach { appendLine("- $it") } }
            if (!testsFound) appendLine("Tests: faiza.test.js missing or defined no tests")
            else {
                appendLine("Tests: $testsPassed passed, ${testsFailed.size} failed")
                testsFailed.take(30).forEach { appendLine("- FAILED: $it") }
            }
            appendLine(if (screenshotB64 != null) "Screenshot: attached (phone width 390px)" else "Screenshot: not available")
        }
    }

    /** Test helpers the tester's faiza.test.js can use (no libraries needed). */
    private const val HARNESS = """
(function(){
  window.__ft = [];
  window.test = function(name, fn){ window.__ft.push({name:name, fn:fn}); };
  window.it = window.test;
  window.assert = function(c, msg){ if(!c) throw new Error(msg || 'assertion failed'); };
  window.assertEqual = function(a, b, msg){ if(a !== b) throw new Error((msg||'not equal') + ': got ' + JSON.stringify(a) + ', expected ' + JSON.stringify(b)); };
  window.$ = window.$ || function(s){ return document.querySelector(s); };
  window.$$ = function(s){ return Array.prototype.slice.call(document.querySelectorAll(s)); };
  window.sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };
  window.click = function(s){ var e = typeof s === 'string' ? document.querySelector(s) : s; if(!e) throw new Error('no element ' + s); e.click(); return e; };
  window.typeInto = function(s, v){ var e = document.querySelector(s); if(!e) throw new Error('no element ' + s); e.focus(); e.value = v; e.dispatchEvent(new Event('input', {bubbles:true})); e.dispatchEvent(new Event('change', {bubbles:true})); return e; };
  window.visible = function(s){ var e = typeof s === 'string' ? document.querySelector(s) : s; if(!e) return false; var r = e.getBoundingClientRect(); var st = getComputedStyle(e); return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none' && st.opacity !== '0'; };
})();
"""

    private const val RUN_TESTS = """
(async function(){
  var res = {passed:0, failed:[], found:(window.__ft||[]).length};
  for (var i=0;i<(window.__ft||[]).length;i++){
    var t = window.__ft[i];
    try { await Promise.race([Promise.resolve(t.fn()), new Promise(function(_,rej){ setTimeout(function(){ rej(new Error('timeout 8s')); }, 8000); })]); res.passed++; }
    catch(e){ res.failed.push(t.name + ' — ' + (e && e.message ? e.message : e)); }
  }
  window.__fr = JSON.stringify(res);
})(); 'started'
"""

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun check(ctx: Context, url: String, testsJs: String?): Report = withContext(Dispatchers.Main) {
        val app = ctx.applicationContext
        val density = app.resources.displayMetrics.density
        val w = (390 * density).toInt()
        val h = (844 * density).toInt()
        val errors = mutableListOf<String>()
        val finished = CompletableDeferred<Boolean>()
        val web = WebView(app).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                        errors += "${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})"
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, u: String?) { finished.complete(true) }
                override fun onReceivedError(view: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                    errors += "Couldn't load ${r?.url} (${e?.description})"
                    if (r?.isForMainFrame == true) finished.complete(false)
                }
                override fun onReceivedHttpError(view: WebView?, r: WebResourceRequest?, resp: WebResourceResponse?) {
                    if ((r?.url?.toString().orEmpty()).contains("favicon")) return
                    errors += "HTTP ${resp?.statusCode} for ${r?.url}"
                    if (r?.isForMainFrame == true) finished.complete(false)
                }
            }
        }
        // Attach invisibly so the page really renders (needed for the screenshot and for layout-dependent tests).
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val attached = Settings.canDrawOverlays(app) && runCatching {
            wm.addView(web, WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; alpha = 0f; x = 0; y = 0 })
        }.isSuccess
        if (!attached) {
            web.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            web.layout(0, 0, w, h)
        }
        try {
            web.loadUrl(url)
            val loaded = withTimeoutOrNull(25_000) { finished.await() } ?: false
            delay(1500) // scripts, fonts, animations settle
            val title = web.title.orEmpty()

            var passed = 0
            var failed = listOf<String>()
            var found = false
            if (loaded && !testsJs.isNullOrBlank()) {
                eval(web, HARNESS)
                val defined = eval(web, testsJs + "\n;'ok'")
                if (defined == null || defined.contains("Error")) errors += "faiza.test.js itself crashed: $defined"
                eval(web, RUN_TESTS)
                var raw: String? = null
                for (i in 0 until 120) { // up to ~60 s
                    delay(500)
                    raw = eval(web, "window.__fr || ''")?.let { unquote(it) }
                    if (!raw.isNullOrBlank()) break
                }
                runCatching {
                    val o = org.json.JSONObject(raw ?: "{}")
                    passed = o.optInt("passed")
                    found = o.optInt("found") > 0
                    val arr = o.optJSONArray("failed")
                    failed = (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }
                }
                if (raw.isNullOrBlank()) failed = listOf("tests did not finish within 60 s")
                // Tests may change the page: reload it fresh for the screenshot.
                val again = CompletableDeferred<Boolean>()
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, u: String?) { again.complete(true) }
                }
                web.reload()
                withTimeoutOrNull(15_000) { again.await() }
                delay(1200)
            }
            val shot = if (loaded) screenshot(web, w, h, density) else null
            Report(loaded, errors.distinct(), passed, failed, found, shot, title)
        } finally {
            if (attached) runCatching { wm.removeView(web) }
            runCatching { web.destroy() }
        }
    }

    /** The top of the page plus as much below as fits (up to ~2.5 screens), shrunk for the AI. */
    private suspend fun screenshot(web: WebView, w: Int, h: Int, density: Float): String? = runCatching {
        val contentH = (web.contentHeight * density).toInt().coerceAtLeast(h)
        val shotH = contentH.coerceAtMost(h * 5 / 2)
        if (shotH > h) {
            web.layoutParams?.let { lp ->
                lp.height = shotH
                runCatching { (web.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(web, lp) }
                    .onFailure { web.layout(0, 0, w, shotH) }
            } ?: web.layout(0, 0, w, shotH)
            delay(700)
        }
        val bmp = Bitmap.createBitmap(w, maxOf(web.height, h), Bitmap.Config.ARGB_8888)
        web.draw(Canvas(bmp))
        val scale = 480f / w
        val small = Bitmap.createScaledBitmap(bmp, 480, (bmp.height * scale).toInt(), true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 78, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private suspend fun eval(web: WebView, js: String): String? {
        val d = CompletableDeferred<String?>()
        web.evaluateJavascript(js) { d.complete(it) }
        return withTimeoutOrNull(10_000) { d.await() }
    }

    private fun unquote(raw: String): String =
        runCatching { com.google.gson.JsonParser.parseString(raw).asString }.getOrDefault(raw)
}
