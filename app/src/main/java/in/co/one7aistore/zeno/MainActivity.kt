package in.co.one7aistore.zeno

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val wv = WebView(this)
            wv.setBackgroundColor(Color.parseColor("#0f2419"))
            webView = wv
            setContentView(wv)

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
            }

            wv.addJavascriptInterface(
                ZenoBridge(this) { callType -> ensureCallPermissions(callType) },
                "AndroidBridge"
            )

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    try {
                        val savedSound = getSharedPreferences("zeno_prefs", Context.MODE_PRIVATE)
                            .getString("notif_sound", "zeno_ding") ?: "zeno_ding"
                        view?.evaluateJavascript(
                            "if (typeof applySavedSoundFromNative === 'function') { applySavedSoundFromNative('$savedSound'); }",
                            null
                        )
                    } catch (_: Exception) {
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        showErrorOnScreen("WebView Load Error: ${error?.description} (code ${error?.errorCode})")
                    }
                }
            }

            wv.webChromeClient = object : WebChromeClient() {
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
                    startActivityForResult(Intent.createChooser(intent, "Select Attachment"), 1001)
                    return true
                }
            }

            wv.loadUrl("file:///android_asset/client.html")
        } catch (t: Throwable) {
            showErrorOnScreen("Startup Crash:\n${t.javaClass.simpleName}: ${t.message}\n\n${t.stackTraceToString()}")
        }
    }

    private fun showErrorOnScreen(message: String) {
        try {
            val scroll = ScrollView(this)
            val tv = TextView(this).apply {
                text = message
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1a0f0f"))
                setPadding(32, 48, 32, 48)
                textSize = 13f
            }
            scroll.addView(tv)
            setContentView(scroll)
        } catch (_: Exception) {
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val resultUri = if (resultCode == RESULT_OK) data?.data else null
            fileUploadCallback?.onReceiveValue(if (resultUri != null) arrayOf(resultUri) else null)
            fileUploadCallback = null
        }
    }

    private fun ensureCallPermissions(callType: String) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (callType.equals("Video", ignoreCase = true)) {
            needed.add(Manifest.permission.CAMERA)
        }
        val missing = needed.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Toast.makeText(this, "Starting $callType Call...", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions(missing.toTypedArray(), 2001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.isNotEmpty()) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val msg = if (allGranted) "Permissions granted" else "Some permissions denied"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
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
