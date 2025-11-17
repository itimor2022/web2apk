package com.baidu.tty2025

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
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.google.gson.Gson
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // 用来计算返回键的点击间隔时间
    private var lastExitTime: Long = 0

    private lateinit var imvBg: ImageView
    private lateinit var imvBg2: ImageView
    private lateinit var tvSkip: TextView
    private lateinit var wb: WebView
    private lateinit var tvReload: TextView
    private lateinit var llError: LinearLayout

    private lateinit var interval: Interval

    private val configJsonUrls = listOf(
        "http://[::ffff:6cba:ba2e]:55530/ty.txt",
        "https://bj-1334056550.cos.ap-beijing.myqcloud.com/oss/ty.txt",
        "https://cd-1334056550.cos.ap-chongqing.myqcloud.com/oss/ty.txt",
        "https://gz-1334056550.cos.ap-guangzhou.myqcloud.com/oss/ty.txt",
        "https://ck-1365383788.cos.ap-chongqing.myqcloud.com/oss/ty.txt",
        "https://gz-1365383788.cos.ap-guangzhou.myqcloud.com/oss/ty.txt",
        "https://sk-1365383788.cos.ap-hongkong.myqcloud.com/oss/ty.txt",
        "https://bk-1365383788.cos.ap-nanjing.myqcloud.com/oss/ty.txt",
        "https://sg-1365383788.cos.ap-singapore.myqcloud.com/oss/ty.txt",
        "https://hg-1334056550.cos.ap-seoul.myqcloud.com/oss/ty.txt",
        "https://jp-1334056550.cos.ap-tokyo.myqcloud.com/oss/ty.txt",
        "https://nj-1334056550.cos.ap-nanjing.myqcloud.com/oss/ty.txt",
    )

    private val defaultUrl = "https://m1.xy417.cc:7443"

    // 本地保存 config.json 的路径
    private val configFile by lazy { File(filesDir, "duo.txt") }

    private val gson = Gson()
    protected var mSwipeBackHelper: SwipeBackHelper? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION_REQUEST_CODE = 2
        // 调整超时时间
        private const val ENTRY_REQUEST_TIMEOUT = 8000L // 增加到15秒
        private const val LINE_TEST_TIMEOUT = 8000L     // 增加到8秒
        private const val CONNECT_TIMEOUT = 10000       // 增加到10秒
        private const val READ_TIMEOUT = 10000          // 增加到10秒
    }

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var currentPhotoUri: Uri

    private fun logAndToast(msg: String) {
        Log.e("111", msg)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private val webClient = object : WebViewClient() {
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.e("111", "onReceivedError->")
            if (request.isForMainFrame) {
                Log.e("111", "onReceivedError2->")
                llError.visibility = View.VISIBLE
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
//                super.onReceivedSslError(view, handler, error)
            Log.e("111", "onReceivedSslError->")
            handler?.proceed()
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest
        ): Boolean {
            if (request.url.toString().startsWith("http")) {
                view?.loadUrl(request.url.toString())
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: Exception) {
                    Log.d("111", "shouldOverrideUrlLoading: $e")
                }
            }
            return true
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = filePathCallback

            if (fileChooserParams?.acceptTypes?.contains("image/*") == true && fileChooserParams.isCaptureEnabled) {
                // Launch camera
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    launchCamera()
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(android.Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                // Use file picker
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*"
                val chooserIntent = Intent.createChooser(intent, "选择文件")
                startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE)
            }

            return true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (fileUploadCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }

            val results: Array<Uri>? = when {
                resultCode == RESULT_OK && data?.data != null -> arrayOf(data.data!!)
                resultCode == RESULT_OK -> arrayOf(currentPhotoUri)
                else -> null
            }

            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, launch camera
                launchCamera()
            } else {
                // Permission denied, show an error or request permission again
                Toast.makeText(this, "相机权限拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        currentPhotoUri = createImageFileUri()
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        startActivityForResult(captureIntent, FILE_CHOOSER_REQUEST_CODE)
    }

    private fun createImageFileUri(): Uri {
        val fileName =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }
        }
        val resolver: ContentResolver = contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return imageUri ?: throw RuntimeException("图片链接为空")
    }

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

        setWebSetting()
    }

    private fun setWebSetting() {
        val wbsetting = wb.settings
        with(wbsetting) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
        }

        wb.webChromeClient = chromeClient
        wb.webViewClient = webClient
        wb.apply {
            clearCache(true)
            clearHistory()
        }

        // 文件下载功能
        wb.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            Log.e("HEHE", "开始下载")
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        })

        if (!isNetworkConnected()) {
            "没有网络，请检查当前网络".showToast()
            return
        }

        tvSkip.setOnClickListener {
            imvBg2.visibility = View.GONE
            tvSkip.visibility = View.GONE
        }
        tvReload.setOnClickListener {
            loadData()
        }

        onBackPressedDispatcher.addCallback {
            if (wb.canGoBack()) {
                wb.goBack()
                return@addCallback
            }
            if (System.currentTimeMillis() - lastExitTime > 2000) {
                //弹出提示，可以有多种方式
                TipUtils.toast("再按一次返回键退出")
                lastExitTime = System.currentTimeMillis()
            } else {
                finish()
            }
        }

        loadData()

    }

    private fun loadData() {
        scopeLife {
            llError.visibility = View.GONE
            var finalUrl: String? = null

            var fetched = false
            // 遍历所有入口，直到找到一个可用的
            for ((index, url) in configJsonUrls.withIndex()) {
                try {
                    val noCacheUrl = if (url.contains("?")) {
                        "$url&_t=${System.currentTimeMillis()}"
                    } else {
                        "$url?_t=${System.currentTimeMillis()}"
                    }

                    Log.e("111", "尝试获取入口${index + 1}: $noCacheUrl")

                    val response = withTimeoutOrNull(ENTRY_REQUEST_TIMEOUT) {
                        Get<String>(noCacheUrl).await()
                    }

                    if (response != null) {
                        Log.e("111", "入口${index + 1}获取成功")
                        configFile.writeText(response.trim())
                        Log.e("111", "已更新txt -> $configFile")
                        fetched = true
                        break  // 成功获取到配置，跳出循环
                    } else {
                        Log.e("111", "入口${index + 1}请求超时，继续尝试下一个入口")
                        // 超时后自动继续下一个循环
                    }
                } catch (e: Exception) {
                    Log.e("111", "入口${index + 1}获取失败: ${e.message}，继续尝试下一个入口")
                    // 异常后自动继续下一个循环
                }
            }

            // 如果没有任何入口成功，但有本地缓存，仍然继续
            if (!fetched) {
                if (configFile.exists() && configFile.length() > 0) {
                    Log.e("111", "使用本地缓存的配置文件")
                    fetched = true
                } else {
                    logAndToast("所有入口域名获取失败，请联系客服")
                    llError.visibility = View.VISIBLE
                    return@scopeLife
                }
            }

            // 后续的线路测试逻辑保持不变...
            if (configFile.exists() && configFile.length() > 0) {
                try {
                    val urls = configFile.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && isValidUrl(it) }

                    Log.e("111", "找到 ${urls.size} 个线路进行测试")

                    for ((index, url) in urls.withIndex()) {
                        Log.e("111", "测试线路${index + 1}：$url")
                        if (isUrlReachable(url)) {
                            finalUrl = url
                            Log.e("111", "可用线路：$url")
                            break
                        } else {
                            logAndToast("线路${index + 1}不可用")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("111", "读取本地 txt 出错 -> ${e.message}")
                }
            }

            if (finalUrl == null) {
                Log.e("111", "开始测试默认域名: $defaultUrl")
                if (isUrlReachable(defaultUrl)) {
                    finalUrl = defaultUrl
                    Log.e("111", "默认域名可用: $defaultUrl")
                } else {
                    logAndToast("默认域名检测失败，请联系客服")
                    llError.visibility = View.VISIBLE
                    return@scopeLife
                }
            }

            Log.e("111", "最终选择的URL: $finalUrl")
            imvBg2.visibility = View.VISIBLE
            tvSkip.visibility = View.VISIBLE
            startSkip()
            loadWeb(finalUrl)
        }
    }


    /**
     * 检查URL是否可访问（协程版）
     */
    private suspend fun isUrlReachable(testUrl: String): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(testUrl).openConnection() as HttpURLConnection
            connection.apply {
                // 使用常量设置连接和读取超时
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
                useCaches = false
            }
            connection.connect()
            connection.responseCode in 200..399
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 基本URL格式验证
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            URL(url).toURI()
            true
        } catch (e: Exception) {
            false
        }
    }


    private fun startSkip() {
        interval = Interval(0, 1, TimeUnit.SECONDS, 3, 0).life(
            this@MainActivity, Lifecycle.Event.ON_DESTROY
        ).subscribe {
            if (it > 0) {
                tvSkip.text = "跳过 ${it}"
            } else {
                tvSkip.text = "跳过"
            }
        }.finish {
//            tvSkip.isEnabled = true
            imvBg2.visibility = View.GONE
            tvSkip.visibility = View.GONE
        }.start()
    }

    private fun loadWeb(url: String) {
        wb.loadUrl(url)
    }

    private fun isNetworkConnected(): Boolean {
        val mConnectivityManager = getSystemService(CONNECTIVITY_SERVICE)
        if (mConnectivityManager is ConnectivityManager) {
            val mNetworkInfo = mConnectivityManager.activeNetworkInfo
            return mNetworkInfo?.isAvailable ?: false
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        mSwipeBackHelper.dispatchTouchEvent(ev) {
            super.dispatchTouchEvent(ev)
        }
}

/**
 * 启动页广告
 */
@Serializable
data class BaseResponse(
    var code: Int = 0,
    var data: List<SplashData> = mutableListOf(),
)

@Serializable
data class SplashData(
    var domain: String = "",
)

fun String.showToast() {
    if (this.isEmpty()) return
    Toast.makeText(App.application, this, Toast.LENGTH_SHORT).show()
}
