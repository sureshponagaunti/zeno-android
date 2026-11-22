package com.zeno.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data?.data
        fileUploadCallback?.onReceiveValue(if (data != null) arrayOf(data) else null)
        fileUploadCallback = null
    }

    private val mediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val camOk = grants[Manifest.permission.CAMERA] == true
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true
        val msg = if (camOk && micOk) {
            "Camera & Mic permissions granted"
        } else {
            "Some call permissions were denied"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.addJavascriptInterface(
            ZenoBridge(this) { callType -> ensureCallPermissions(callType) },
            "AndroidBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val savedSound = getSharedPreferences("zeno_prefs", Context.MODE_PRIVATE)
                    .getString("notif_sound", "zeno_ding") ?: "zeno_ding"
                webView.evaluateJavascript(
                    "if (typeof selectSound === 'function') { currentSound = '$savedSound'; localStorage.setItem('zeno_sound', '$savedSound'); }",
                    null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                filePickerLauncher.launch(Intent.createChooser(intent, "Select Attachment"))
                return true
            }
        }

        webView.loadUrl("file:///android_asset/client.html")
    }

    private fun ensureCallPermissions(callType: String) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (callType.equals("Video", ignoreCase = true)) {
            needed.add(Manifest.permission.CAMERA)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Toast.makeText(this, "Starting $callType Call...", Toast.LENGTH_SHORT).show()
        } else {
            mediaPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    class ZenoBridge(
        private val context: Context,
        private val onCallRequest: (String) -> Unit
    ) {
        @JavascriptInterface
        fun saveSoundPreference(soundName: String) {
            val prefs = context.getSharedPreferences("zeno_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("notif_sound", soundName).apply()
            playNotificationTone(soundName)
        }

        @JavascriptInterface
        fun playSavedNotificationSound() {
            val prefs = context.getSharedPreferences("zeno_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("notif_sound", "zeno_ding") ?: "zeno_ding"
            playNotificationTone(saved)
        }

        @JavascriptInterface
        fun onCallTriggered(callType: String) {
            Handler(Looper.getMainLooper()).post {
                onCallRequest(callType)
            }
        }

        private fun playNotificationTone(soundName: String) {
            if (soundName == "mute") return
            try {
                val toneType = when (soundName) {
                    "soft_plink" -> ToneGenerator.TONE_PROP_BEEP
                    "gentle_chime" -> ToneGenerator.TONE_PROP_ACK
                    else -> ToneGenerator.TONE_PROP_BEEP2
                }
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                toneGen.startTone(toneType, 180)
                Handler(Looper.getMainLooper()).postDelayed({
                    toneGen.release()
                }, 250)
            } catch (_: Exception) {
            }
        }
    }
}
