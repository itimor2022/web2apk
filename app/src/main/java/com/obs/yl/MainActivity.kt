package com.apple.fmjh2025

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.drake.net.Get
import com.drake.net.time.Interval
import com.drake.net.utils.TipUtils
import com.drake.net.utils.scopeLife
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private var lastExitTime: Long = 0
    private lateinit var imvBg: ImageView
    private lateinit var imvBg2: ImageView
    private lateinit var tvSkip: TextView
    private lateinit var wb: WebView
    private lateinit var tvReload: TextView
    private lateinit var llError: LinearLayout
    private lateinit var interval: Interval

    private val configJsonUrls = listOf(
        "https://108.186.186.63:701/main.txt",
        "https://ck-1365383788.cos.ap-chongqing.myqcloud.com/oss/main.txt",
        "https://sg-1365383788.cos.ap-singapore.myqcloud.com/oss/main.txt",
        "https://gz-1365383788.cos.ap-guangzhou.myqcloud.com/oss/main.txt",
        "https://sk-1365383788.cos.ap-hongkong.myqcloud.com/oss/main.txt",
        "https://bk-1365383788.cos.ap-nanjing.myqcloud.com/oss/main.txt",
    )
    private val defaultUrl = "https://108.186.186.57:701/skl001"

    private val configFile by lazy { File(filesDir, "main.txt") }
    private val bestLineFile by lazy { File(filesDir, "best_line.txt") }

    protected var mSwipeBackHelper: SwipeBackHelper? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION_REQUEST_CODE = 2
    }

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var currentPhotoUri: Uri

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // ============================== WebView 基础设置 ==============================
    private val webClient = object : WebViewClient() {
        override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
            if (request.isForMainFrame) llError.visibility = View.VISIBLE
        }
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.proceed()
        }
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http")) view?.loadUrl(url) else try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) {}
            return true
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            imvBg2.visibility = View.GONE
            tvSkip.visibility = View.GONE
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) = request.grant(request.resources)
        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = filePathCallback
            if (fileChooserParams?.acceptTypes?.contains("image/*") == true && fileChooserParams.isCaptureEnabled) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                }
            } else {
                startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, "选择图片"), FILE_CHOOSER_REQUEST_CODE)
            }
            return true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val results = when {
                resultCode == RESULT_OK && data?.data != null -> arrayOf(data.data!!)
                resultCode == RESULT_OK -> arrayOf(currentPhotoUri)
                else -> null
            }
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        currentPhotoUri = createImageFileUri()
        intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
    }

    private fun createImageFileUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timeStamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/TTY2025")
            }
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("无法创建图片文件")
    }

    // ============================== onCreate ==============================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        mSwipeBackHelper = SwipeBackHelper(this)
        imvBg = findViewById(R.id.imv_bg)
        imvBg2 = findViewById(R.id.imv_bg2)
        tvSkip = findViewById(R.id.tv)
        wb = findViewById(R.id.web)
        tvReload = findViewById(R.id.tv_reload)
        llError = findViewById(R.id.ll_error)

        wb.settings.javaScriptEnabled = true
        wb.settings.domStorageEnabled = true
        wb.settings.allowFileAccess = true
        wb.settings.loadWithOverviewMode = true
        wb.settings.javaScriptCanOpenWindowsAutomatically = true
        wb.settings.mediaPlaybackRequiresUserGesture = false
        wb.webChromeClient = chromeClient
        wb.webViewClient = webClient
        wb.clearCache(true)

        tvSkip.setOnClickListener { imvBg2.visibility = View.GONE; tvSkip.visibility = View.GONE }
        tvReload.setOnClickListener { loadData() }

        onBackPressedDispatcher.addCallback {
            if (wb.canGoBack()) wb.goBack() else {
                if (System.currentTimeMillis() - lastExitTime > 2000) {
                    TipUtils.toast("再按一次退出")
                    lastExitTime = System.currentTimeMillis()
                } else finish()
            }
        }

        loadData()
    }

    // ============================== 核心加速 + Toast 提示 ==============================
    private fun loadData() {
        scopeLife {
            llError.visibility = View.GONE

            // 1. 优先使用上次最佳线路
            if (bestLineFile.exists()) {
                val best = bestLineFile.readText().trim()
                if (best.isNotEmpty() && withTimeoutOrNull(3000) { testLineRTT(best) } != Long.MAX_VALUE) {
                    toast("秒开上次线路")
                    startSkip()
                    wb.loadUrl(best)
                    return@scopeLife
                }
            }

            // 2. 并发抢入口（失败的 Toast 提示）
            var configText: String? = null
            val entryResults = configJsonUrls.map { url ->
                async {
                    val noCache = "$url?_t=${System.currentTimeMillis()}"
                    try {
                        val text = withTimeoutOrNull(6000) { Get<String>(noCache).await() }
                        if (!text.isNullOrBlank()) text.trim() else null
                    } catch (e: Exception) {
//                        runOnUiThread { toast("入口失败：$url") }
                        runOnUiThread { toast("请等待：正在获取最新入口") }
                        null
                    }
                }
            }.awaitAll()

            configText = entryResults.firstOrNull { !it.isNullOrBlank() }
            if (configText != null) {
                configFile.writeText(configText)
                toast("入口成功")
            } else if (configFile.exists() && configFile.length() > 0) {
                configText = configFile.readText().trim()
                toast("所有入口失败，使用本地缓存")
            } else {
                toast("所有入口都挂了，启用默认线路")
                bestLineFile.writeText(defaultUrl)   // 记住这次用的是默认
                startSkip()
                wb.loadUrl(defaultUrl)
                return@scopeLife
            }

            // 3. 并发测速线路（失败的也 Toast）
            val lines = configText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && isValidUrl(it) }
                .distinct()

            if (lines.isEmpty()) {
                toast("线路列表为空")
                llError.visibility = View.VISIBLE
                return@scopeLife
            }

            val rttResults = lines.map { line ->
                async {
                    val rtt = testLineRTT(line)
                    if (rtt == Long.MAX_VALUE) runOnUiThread { toast("线路失效：$line") }
                    line to rtt
                }
            }.awaitAll()

            val bestLine = rttResults
                .filter { it.second < Long.MAX_VALUE }
                .minByOrNull { it.second }
                ?.first
                ?: if (withTimeoutOrNull(5000) { testLineRTT(defaultUrl) } != Long.MAX_VALUE) defaultUrl else null

            if (bestLine == null) {
                toast("全部线路失效，请联系客服检查网络")
                bestLineFile.writeText(defaultUrl)
                startSkip()
                wb.loadUrl(defaultUrl)
                return@scopeLife
            }

            bestLineFile.writeText(bestLine)
            toast("使用最快线路")
            startSkip()
            wb.loadUrl(bestLine)
        }
    }

    // 彻底解决 IP:端口 AssertionError + 兼容所有 Android 版本
    private suspend fun testLineRTT(url: String): Long = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        try {
            // 关键修复：强制让所有「纯 IP + 端口」形式的 URL 变成标准格式
            val fixedUrl = when {
                // 匹配 https://1.1.1.1:8443  或 http://192.168.1.1:8080/xxx
                url.matches(Regex("^https?://\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+.*$")) -> {
                    val base = if (url.endsWith("/")) url.dropLast(1) else url
                    // 保证有路径 + 查询参数
                    if (base.contains("?")) "$base&_t=$start" else "$base?_t=$start"
                }
                else -> {
                    // 普通域名
                    if (url.contains("?")) "$url&_t=$start" else "$url?_t=$start"
                }
            }

            // 防止极端机型 URL() 构造阶段就炸
            val urlObj = try {
                URL(fixedUrl)
            } catch (e: Exception) {
                return@withContext Long.MAX_VALUE
            }

            conn = (urlObj.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                // 重点：禁止 IPv6 映射（很多机型在这里炸）
                if (this is javax.net.ssl.HttpsURLConnection) {
                    // 可选：强制使用 IPv4（彻底根治 AssertionError）
                    System.setProperty("java.net.preferIPv6Addresses", "false")
                    System.setProperty("java.net.preferIPv4Stack", "true")
                }
            }

            conn.connect()
            val code = conn.responseCode
            if (code in 200..399) {
                return@withContext System.currentTimeMillis() - start
            }
        } catch (e: Throwable) {
            // 静默失败
        } finally {
            conn?.disconnect()
        }
        Long.MAX_VALUE
    }

    private fun isValidUrl(url: String): Boolean = try { URL(url).toURI(); true } catch (e: Exception) { false }

    private fun startSkip() {
        interval = Interval(0, 1, TimeUnit.SECONDS, 3, 0)
            .life(this, Lifecycle.Event.ON_DESTROY)
            .subscribe { tvSkip.text = if (it > 0) "跳过 $it" else "跳过" }
            .finish { imvBg2.visibility = View.GONE; tvSkip.visibility = View.GONE }
            .start()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        mSwipeBackHelper.dispatchTouchEvent(ev) { super.dispatchTouchEvent(ev) }
}