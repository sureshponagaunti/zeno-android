package in.co.one7aistore.zeno;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screenshot & Screen Recording 100% Block
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUrl(Uri.parse(url));
            }

            private boolean handleExternalUrl(Uri uri) {
                if (uri == null) return false;
                String url = uri.toString();
                String scheme = uri.getScheme();

                if ("whatsapp".equalsIgnoreCase(scheme)
                        || url.contains("wa.me")
                        || url.contains("api.whatsapp.com")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        String msgText = uri.getQueryParameter("text");
                        Intent webIntent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://api.whatsapp.com/send/?text=" + Uri.encode(msgText != null ? msgText : ""))
                        );
                        startActivity(webIntent);
                    }
                    return true;
                }
                return false;
            }
        });

        webView.loadUrl("https://one7aistore.co.in/zeno/");
        setContentView(webView);
    }
}
