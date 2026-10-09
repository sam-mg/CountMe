package com.jd_s4nd_b0x.CountMe.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.jd_s4nd_b0x.CountMe.R;

/**
 * Shows one of the bundled legal pages (the same HTML files published to GitHub Pages from /docs).
 * Pages are local assets, so they open offline; external links open in the browser.
 */
public abstract class LegalActivity extends AppCompatActivity {

    private static final String ASSET_BASE = "file:///android_asset/";

    @StringRes
    protected abstract int titleRes();

    /** File name inside /docs, e.g. "privacy-policy.html". */
    protected abstract String assetName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_legal);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.legalMain),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(titleRes());
        toolbar.setNavigationOnClickListener(v -> finish());

        WebView web = findViewById(R.id.webView);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(false);
        ws.setAllowFileAccess(false); // asset URLs still load; arbitrary files cannot
        ws.setAllowContentAccess(false);
        web.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        if (uri.toString().startsWith(ASSET_BASE)) {
                            return false; // our own pages
                        }
                        if ("http".equals(uri.getScheme())
                                || "https".equals(uri.getScheme())
                                || "mailto".equals(uri.getScheme())) {
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        }
                        return true;
                    }
                });
        web.loadUrl(ASSET_BASE + assetName());
    }
}
