package in.co.one7aistore.zeno

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
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
                        "if (typeof applySavedSoundFromNative === 'function') { applySavedSoundFromNative('$savedSound'); }",
                        null
                    )
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(
                            this@MainActivity,
                            "Load error: ${error?.description}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
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
                    startActivityForResult(Intent.createChooser(intent, "Select Attachment"), 1001)
                    return true
                }
            }

            webView.loadUrl("file:///android_asset/client.html")
        } catch (e: Exception) {
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
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
