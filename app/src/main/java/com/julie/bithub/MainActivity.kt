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
        manterAcesa()
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
        // versão do app instalado (o hub compara com a última do GitHub)
        @JavascriptInterface fun versao(): Int = packageManager.getPackageInfo(packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }
        @JavascriptInterface fun recarregar() { mao.post { web.loadUrl(endereco) } }
        @JavascriptInterface fun instalar(url: String) { mao.post { baixarEInstalar(url) } }
        // abre um link num app do tablet (ex.: música no YT Music); sem o app, abre no navegador
        @JavascriptInterface fun abrir(url: String, pacote: String) {
            mao.post {
                val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(if (pacote.isNotEmpty()) android.content.Intent(i).setPackage(pacote) else i) }
                catch (e: android.content.ActivityNotFoundException) { try { startActivity(i) } catch (_: Exception) {} }
            }
        }
    }

    // atualizar pelo próprio app: baixa o APK novo do GitHub e abre o instalador do Android
    private fun baixarEInstalar(url: String) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            // primeira vez: o Android pede pra liberar "instalar apps desta fonte"
            startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:$packageName")))
            return
        }
        val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
        java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "bit-hub.apk").delete()
        val id = dm.enqueue(android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle("BIT · atualização")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, "bit-hub.apk"))
        val receptor = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                if (i.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                unregisterReceiver(this)
                val uri = dm.getUriForDownloadedFile(id) ?: return
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val filtro = android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, filtro, android.content.Context.RECEIVER_EXPORTED) else registerReceiver(receptor, filtro)
    }

    @Suppress("DEPRECATION")
    private fun telaCheia() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(foco: Boolean) { super.onWindowFocusChanged(foco); if (foco) telaCheia() }
    override fun onResume() { super.onResume(); manterAcesa(); web.onResume() }

    // tela sempre acesa com o app aberto: não apaga, não escurece e aparece por cima da tela de bloqueio
    @Suppress("DEPRECATION")
    private fun manterAcesa() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        if (::web.isInitialized) web.keepScreenOn = true
    }
    override fun onPause() { web.onPause(); super.onPause() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() }
}
