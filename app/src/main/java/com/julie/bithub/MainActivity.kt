package com.julie.bithub

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

// O app é uma "casca": abre o hub do BIT que roda no PC (pelo Tailscale) em tela cheia.
// Tudo de verdade (robô 3D, painel, balões) vem do PC, então melhorias lá aparecem aqui sem atualizar o app.
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pedidoMicrofone: PermissionRequest? = null
    private val mao = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("bit", MODE_PRIVATE) }
    private val enderecoPadrao = "https://pcjulie.tailcc14c2.ts.net:8443/hub.html"
    private val endereco get() = prefs.getString("url", enderecoPadrao) ?: enderecoPadrao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0b0c10"))
        setContentView(web)
        telaCheia()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // a voz do BIT toca sem precisar tocar na tela
        }
        web.addJavascriptInterface(Ponte(), "BitApp")
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, req: WebResourceRequest, erro: WebResourceError) {
                if (req.isForMainFrame) mostrarSemConexao(erro.description?.toString() ?: "")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            // o hub pediu o microfone (botão FALAR)
            override fun onPermissionRequest(pedido: PermissionRequest) {
                runOnUiThread {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pedido.grant(pedido.resources)
                    else { pedidoMicrofone = pedido; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
                }
            }
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        web.loadUrl(endereco)
    }

    override fun onRequestPermissionsResult(codigo: Int, permissoes: Array<out String>, resultados: IntArray) {
        val p = pedidoMicrofone ?: return
        pedidoMicrofone = null
        if (resultados.firstOrNull() == PackageManager.PERMISSION_GRANTED) p.grant(p.resources) else p.deny()
    }

    // PC desligado ou fora da rede: tela de espera que tenta de novo sozinha (e deixa trocar o endereço)
    private fun mostrarSemConexao(motivo: String) {
        val html = """
            <html><body style="margin:0;background:#0b0c10;color:#ecebe6;font:16px monospace;display:grid;place-items:center;height:100vh;text-align:center">
            <div><b style="letter-spacing:.2em">BIT.SYS</b><p style="opacity:.6">procurando o PC…<br>confere se o robô está ligado e o Tailscale conectado.</p>
            <p style="opacity:.35;font-size:12px">${motivo.replace("<", "")}</p>
            <input id="u" value="$endereco" style="width:70vw;background:#000;color:#ecebe6;border:1px solid #ecebe644;padding:8px;font:13px monospace">
            <br><button onclick="BitApp.salvar(document.getElementById('u').value)" style="margin-top:10px;background:none;color:#ecebe6;border:1px solid #ecebe6;padding:10px 24px;font:14px monospace">tentar de novo</button></div>
            <script>setTimeout(() => BitApp.salvar(document.getElementById('u').value), 10000)</script></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    inner class Ponte {
        @JavascriptInterface fun salvar(url: String) {
            prefs.edit().putString("url", url.trim().ifEmpty { enderecoPadrao }).apply()
            mao.post { web.loadUrl(endereco) }
        }
    }

    @Suppress("DEPRECATION")
    private fun telaCheia() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(foco: Boolean) { super.onWindowFocusChanged(foco); if (foco) telaCheia() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() }
}
