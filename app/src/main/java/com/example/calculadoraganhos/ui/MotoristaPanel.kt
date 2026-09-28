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
                injectResetPassword(view)
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

    private fun injectResetPassword(view: WebView?) {
        view?.evaluateJavascript(RESET_JS, null)
    }

    companion object {
        const val URL = "https://motorista.drivewin.shop/"

        private val RESET_JS = """
            (function(){
              try{
                if(!document.getElementById('toggle-redefinir')){
                  var cad = document.getElementById('toggle-cadastro');
                  if(cad && cad.parentNode){
                    var el = document.createElement('div');
                    el.id = 'toggle-redefinir';
                    el.className = cad.className;
                    el.style.cssText = 'margin-top:8px; font-size:12px; cursor:pointer;';
                    el.textContent = 'Esqueci a senha';
                    el.onclick = function(){ if(window.redefinirSenha) window.redefinirSenha(); };
                    cad.parentNode.insertBefore(el, cad.nextSibling);
                  }
                }
                if(!document.getElementById('redefinir-overlay')){
                  var wrap = document.createElement('div');
                  wrap.id = 'redefinir-overlay';
                  wrap.className = 'config-overlay';
                  wrap.innerHTML = '<div class="config-panel"><div class="config-header"><div class="config-title">Redefinir senha</div><div class="config-close" onclick="fecharRedefinir()">✕</div></div><div class="meta-box" style="margin-bottom:12px;"><input type="password" id="redefinir-senha" placeholder="Nova senha (mín. 8 caracteres)" style="text-align:left; width:100%;" autocomplete="new-password"></div><div class="meta-box" style="margin-bottom:14px;"><input type="password" id="redefinir-senha2" placeholder="Confirme a nova senha" style="text-align:left; width:100%;" autocomplete="new-password"></div><div class="login-erro" id="redefinir-erro"></div><button class="btn-primary" onclick="salvarNovaSenha()">Salvar nova senha</button></div>';
                  (document.getElementById('phone')||document.body).appendChild(wrap);
                }
                if(typeof window.mostrarErroRedefinir !== 'function'){
                  window.mostrarErroRedefinir = function(msg){
                    var e = document.getElementById('redefinir-erro');
                    if(!e) return;
                    e.textContent = msg;
                    e.style.display = 'block';
                  };
                }
                if(typeof window.fecharRedefinir !== 'function'){
                  window.fecharRedefinir = function(){
                    var o = document.getElementById('redefinir-overlay');
                    if(o) o.classList.remove('open');
                  };
                }
                if(typeof window.abrirRedefinir !== 'function'){
                  window.abrirRedefinir = function(){
                    var e = document.getElementById('redefinir-erro');
                    if(e) e.style.display = 'none';
                    var s1 = document.getElementById('redefinir-senha');
                    var s2 = document.getElementById('redefinir-senha2');
                    if(s1) s1.value = '';
                    if(s2) s2.value = '';
                    var o = document.getElementById('redefinir-overlay');
                    if(o) o.classList.add('open');
                  };
                }
                if(typeof window.redefinirSenha !== 'function'){
                  window.redefinirSenha = async function(){
                    var emailEl = document.getElementById('login-email');
                    var email = emailEl ? emailEl.value.trim().toLowerCase() : '';
                    if(!email){
                      if(window.mostrarErroLogin) window.mostrarErroLogin('Informe seu e-mail pra redefinir a senha.');
                      return;
                    }
                    if(!window.DWClient || !window.DWClient.auth) return;
                    var redirectTo = window.location.origin + (window.location.pathname || '/');
                    var r = await window.DWClient.auth.resetPasswordForEmail(email, { redirectTo: redirectTo });
                    if(r && r.error){
                      if(window.mostrarErroLogin) window.mostrarErroLogin(r.error.message || 'Não foi possível enviar o e-mail.');
                      return;
                    }
                    if(window.mostrarErroLogin) window.mostrarErroLogin('Enviamos um link de redefinição para o seu e-mail. Confira a caixa de entrada e o spam.');
                  };
                }
                if(typeof window.salvarNovaSenha !== 'function'){
                  window.salvarNovaSenha = async function(){
                    var s1 = (document.getElementById('redefinir-senha')||{}).value || '';
                    var s2 = (document.getElementById('redefinir-senha2')||{}).value || '';
                    var e = document.getElementById('redefinir-erro');
                    if(e) e.style.display = 'none';
                    if(!s1 || s1.length < 8){
                      window.mostrarErroRedefinir('A senha precisa ter pelo menos 8 caracteres.');
                      return;
                    }
                    if(s1 !== s2){
                      window.mostrarErroRedefinir('As senhas não coincidem.');
                      return;
                    }
                    if(!window.DWClient || !window.DWClient.auth) return;
                    var r = await window.DWClient.auth.updateUser({ password: s1 });
                    if(r && r.error){
                      window.mostrarErroRedefinir(r.error.message || 'Não foi possível salvar a nova senha.');
                      return;
                    }
                    window.fecharRedefinir();
                    if(window.mostrarErroLogin) window.mostrarErroLogin('Senha redefinida. Entre com a nova senha.');
                    try { await window.DWClient.auth.signOut(); } catch(_){}
                  };
                }
                try{
                  var hash = (window.location.hash || '').replace(/^#/, '');
                  var qs = new URLSearchParams(hash);
                  if(qs.get('type') === 'recovery' && window.abrirRedefinir) window.abrirRedefinir();
                }catch(_){}
                if(window.DWClient && window.DWClient.auth && window.DWClient.auth.onAuthStateChange){
                  window.DWClient.auth.onAuthStateChange(function(event){
                    if(event === 'PASSWORD_RECOVERY' && window.abrirRedefinir) window.abrirRedefinir();
                  });
                }
              }catch(_){}
            })();
        """.trimIndent()
    }
}
