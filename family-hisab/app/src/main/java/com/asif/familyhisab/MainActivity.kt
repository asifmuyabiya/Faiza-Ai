package com.asif.familyhisab

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.ui.App
import com.asif.familyhisab.ui.theme.HisabTheme
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.Fmt
import com.asif.familyhisab.util.LocalFmt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store.get(this)
        setContent {
            val data by store.data.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalFmt provides Fmt(data.settings.banglaDigits)) {
                HisabTheme(data.settings.theme) {
                    val dark = LocalExtra.current.dark
                    LaunchedEffect(dark) {
                        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    }
                    App(store, data)
                }
            }
        }
    }
}
