package com.sina.bld2026

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
        "https://nj-1334056550.cos.ap-nanjing.myqcloud.com/oss/bld.txt",
        "https://gz-1334056550.cos.ap-guangzhou.myqcloud.com/oss/bld.txt",
        "https://hg-1334056550.cos.ap-seoul.myqcloud.com/oss/bld.txt",
        "https://jp-1334056550.cos.ap-tokyo.myqcloud.com/oss/bld.txt",
        "https://bj-1334056550.cos.ap-beijing.myqcloud.com/oss/bld.txt",
        "https://cd-1334056550.cos.ap-chongqing.myqcloud.com/oss/bld.txt",
    )
    private val defaultUrl = "https://xh.t5w.icu"

    private val configFile by lazy { File(filesDir, "bld.txt") }
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
            runOnUiThread {
                imvBg2.visibility = View.GONE
                tvSkip.visibility = View.GONE
            }
            if (::interval.isInitialized) {
                interval.cancel()   // 停止计时器，避免 finish 回调再执行一次
            }
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

        tvSkip.setOnClickListener {
//            imvBg2.visibility = View.GONE;
//            tvSkip.visibility = View.GONE;
             Toast.makeText(this, "等待加载中，请稍候...", Toast.LENGTH_SHORT).show()
        }
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
                if (best.isNotEmpty()) {
                    val ok = withTimeoutOrNull(4000) { checkUrlAvailable(best) } ?: false

                    if (ok) {
                        toast("秒开上次线路")
                        startSkip()
                        wb.loadUrl(best)
                        return@scopeLife
                    } else {
                        toast("上次线路失效，重新获取")
                    }
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
                        runOnUiThread { toast("入口失败：$url") }
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
                toast("入口失败，使用本地缓存")
            } else {
                toast("入口无法访问，请联系客服检查")
                llError.visibility = View.VISIBLE
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

            val bestLine = findAvailableLine(lines)
                ?: if (withTimeoutOrNull(5000) { checkUrlAvailable(defaultUrl) } == true) defaultUrl else null

            if (bestLine == null) {
                toast("全部线路失效，请联系客服检查网络")
                llError.visibility = View.VISIBLE
                return@scopeLife
            }

            bestLineFile.writeText(bestLine)
            toast("使用最快线路")
            startSkip()
            wb.loadUrl(bestLine)
        }
    }


    private suspend fun checkUrlAvailable(url: String): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val testUrl = if (!url.contains("?")) {
                "$url?_t=${System.currentTimeMillis()}"
            } else url

            conn = URL(testUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            // 优先 HEAD
            try {
                conn.requestMethod = "HEAD"
                conn.connect()
            } catch (e: Exception) {
                // 不支持 HEAD → fallback GET
                conn.disconnect()
                conn = URL(testUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connect()
            }

            val code = conn.responseCode
            code in 200..399

        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun findAvailableLine(lines: List<String>): String? = coroutineScope {

        val result = CompletableDeferred<String?>()

        lines.forEach { line ->
            launch {
                val ok = withTimeoutOrNull(4000) {
                    checkUrlAvailable(line)
                } ?: false

                if (ok && !result.isCompleted) {
                    result.complete(line) // 谁先成功用谁
                }
            }
        }

        // 最多等5秒
        return@coroutineScope withTimeoutOrNull(5000) {
            result.await()
        }
    }

    private fun isValidUrl(url: String): Boolean = try { URL(url).toURI(); true } catch (e: Exception) { false }

    private fun startSkip() {
        // 创建 7 秒倒计时
        interval = Interval(0, 1, TimeUnit.SECONDS, 7, 0)
            .life(this, Lifecycle.Event.ON_DESTROY)
            .subscribe { count ->
                // count 是已经过去的秒数
                tvSkip.text = if (count > 0) "等待加载 $count" else "等待加载"
            }
            .finish {
                // 7秒到，强制隐藏
                runOnUiThread {
                    imvBg2.visibility = View.GONE
                    tvSkip.visibility = View.GONE
                }
            }
            .start()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        mSwipeBackHelper.dispatchTouchEvent(ev) { super.dispatchTouchEvent(ev) }
}