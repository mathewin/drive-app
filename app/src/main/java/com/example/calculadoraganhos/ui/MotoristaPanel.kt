package com.example.calculadoraganhos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dono do WebView do painel do motorista.
 *
 * O WebView é criado uma única vez e fica vivo enquanto o app estiver aberto:
 * alternar entre a calculadora e o modo motorista apenas mostra/esconde a mesma
 * página já carregada, em vez de recriar tudo e baixar o painel de novo.
 */
class MotoristaPanel(context: Context) {

    private val appCtx = context.applicationContext

    var webView: WebView? = null
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf(false)
        private set

    private var created = false

    @SuppressLint("SetJavaScriptEnabled")
    fun ensure(): WebView {
        webView?.let { return it }
        val wv = WebView(appCtx)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.databaseEnabled = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        CookieManager.getInstance().let { cm ->
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, true)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loading = true
                error = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                error = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                webError: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loading = false
                    error = true
                }
            }
        }
        wv.loadUrl(URL)
        webView = wv
        created = true
        return wv
    }

    fun reload() {
        val wv = webView ?: return
        loading = true
        error = false
        wv.reload()
    }

    fun retry() {
        val wv = webView ?: return
        loading = true
        error = false
        wv.loadUrl(URL)
    }

    fun sairPainel() {
        val wv = webView ?: return
        wv.evaluateJavascript(
            "(function(){var done=function(){try{localStorage.clear();sessionStorage.clear();}catch(e){} location.href='${URL}';}; if(window.DWClient&&window.DWClient.auth){window.DWClient.auth.signOut().then(done)['catch'](done);}else{done();}})();",
            null
        )
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    fun canGoBack() = webView?.canGoBack() ?: false

    fun goBack() {
        webView?.goBack()
    }

    fun pause() {
        if (created) webView?.onPause()
    }

    fun resume() {
        if (created) webView?.onResume()
    }

    companion object {
        const val URL = "https://motorista.drivewin.shop/"
    }
}
