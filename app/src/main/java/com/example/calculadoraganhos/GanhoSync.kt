package com.example.calculadoraganhos

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.calculadoraganhos.ui.MotoristaPanel
import java.util.Locale

/**
 * Envia o valor do dia informado no card de trabalho para o painel do motorista
 * (Supabase). Usa um WebView oculto que compartilha a sessao ja logada no painel,
 * entao o dado cai direto no rank / area de camp do motorista.
 *
 * A gravacao e "merge-safe": busca o lancamento do dia, atualiza apenas o campo
 * de ganho informado e reenvia a linha completa, sem apagar km, horas, gastos etc.
 */
object GanhoSync {

    private class JsBridge(private val deliver: (String) -> Unit) {
        @JavascriptInterface
        fun result(s: String) {
            deliver(s)
        }
    }

    private var wv: WebView? = null
    private var pageReady = false
    private var pendingDate: String? = null
    private var pendingValue: Double = 0.0
    private var callback: ((Boolean, String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    /** Indica se ja existe uma sessao util (ultimo envio deu certo). */
    @Volatile
    var lastOk: Boolean = false
        private set

    fun push(context: Context, dateKey: String, total: Double, onResult: ((Boolean, String) -> Unit)? = null) {
        runOnMain {
            val ctx = context.applicationContext
            pendingDate = dateKey
            pendingValue = total
            callback = onResult
            ensureWeb(ctx)
            if (pageReady) flush()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(ctx: Context) {
        if (wv != null) return
        try {
            val w = WebView(ctx)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.databaseEnabled = true
            w.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
            w.addJavascriptInterface(JsBridge { r -> main.post { onJsResult(r) } }, "DWAndroid")
            w.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    flush()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    // mantem o webview para tentar de novo quando houver rede
                }
            }
            wv = w
            w.loadUrl(MotoristaPanel.URL)
            DriveWinLog.log("sync", "webview de sincronizacao iniciada")
        } catch (t: Throwable) {
            DriveWinLog.log("sync", "erro ao criar webview: ${t.message}")
        }
    }

    private fun flush() {
        val w = wv ?: return
        val date = pendingDate ?: return
        if (!pageReady) return
        val valor = String.format(Locale.US, "%.2f", pendingValue)
        try {
            w.evaluateJavascript(script(date, valor), null)
            DriveWinLog.log("sync", "enviando valor $valor do dia $date")
        } catch (t: Throwable) {
            DriveWinLog.log("sync", "erro ao enviar via js: ${t.message}")
        }
    }

    private fun onJsResult(raw: String) {
        DriveWinLog.log("sync", "resultado: $raw")
        if (raw == "ok") {
            lastOk = true
            pendingDate = null
            callback?.invoke(true, "no ar")
        } else {
            lastOk = false
            val msg = when {
                raw.startsWith("fail:sem login") -> "abra o painel Motorista e faca login uma vez"
                raw.startsWith("fail:sem painel") -> "sem conexao com o painel"
                raw.startsWith("fail:") -> raw.removePrefix("fail:")
                else -> "nao foi possivel enviar"
            }
            callback?.invoke(false, msg)
        }
        callback = null
    }

    private fun script(date: String, valor: String): String {
        return "(function(){" +
            "try{" +
            "var date=\"" + date + "\";" +
            "var valor=" + valor + ";" +
            "var tries=0;" +
            "function send(m){try{if(window.DWAndroid)window.DWAndroid.result(m);}catch(e){}}" +
            "function go(){" +
            "if(!window.DWClient){if(tries++<40){return setTimeout(go,250);}return send('fail:sem painel');}" +
            "window.DWClient.auth.getSession().then(function(s){" +
            "if(!s||!s.data||!s.data.session){if(tries++<40){return setTimeout(go,250);}return send('fail:sem login');}" +
            "var uid=s.data.session.user.id;" +
            "window.DWClient.from('lancamentos').select('*').eq('usuario_id',uid).eq('data',date).maybeSingle().then(function(res){" +
            "try{" +
            "var row=(res&&res.data)?res.data:null;" +
            "var dd=(row&&row.dados)?row.dados:{};" +
            "dd.outroApp=valor;" +
            "var bruto=(dd.uber||0)+(dd.n99||0)+(dd.promo||0)+(dd.outroApp||0);" +
            "var gasolina=row?(row.gasolina||0):0;" +
            "var alimentacao=row?(row.alimentacao||0):0;" +
            "var outros=dd.outros||0;" +
            "var payload={usuario_id:uid,data:date,corridas:row?(row.corridas||0):0,km:row?(row.km||0):0,horas:row?(row.horas||0):0,bruto:bruto,gasolina:gasolina,apps:row?(row.apps||0):0,rede:row?(row.rede||0):0,alimentacao:alimentacao,total:bruto-gasolina-alimentacao-outros,nota:row?(row.nota||null):null,dados:dd};" +
            "window.DWClient.from('lancamentos').upsert(payload,{onConflict:'usuario_id,data'}).then(function(r2){" +
            "if(r2&&r2.error){return send('fail:'+r2.error.message);}" +
            "send('ok');" +
            "}).catch(function(e){send('fail:'+e.message);});" +
            "}catch(e){send('fail:'+e.message);}" +
            "}).catch(function(e){send('fail:'+e.message);});" +
            "}).catch(function(e){send('fail:'+e.message);});" +
            "}" +
            "go();" +
            "}catch(e){try{if(window.DWAndroid)window.DWAndroid.result('fail:'+e.message);}catch(_){}}" +
            "})();"
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            main.post(block)
        }
    }
}
