package com.obs.yl

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
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
import androidx.lifecycle.Lifecycle
import com.drake.net.Get
import com.drake.net.time.Interval
import com.drake.net.utils.TipUtils
import com.drake.net.utils.scopeLife
import com.google.gson.Gson
import kotlinx.serialization.Serializable
import android.Manifest.permission
import java.util.concurrent.TimeUnit

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

    private var url = "https://13.248.251.116:7102/skl002"

    private val gson = Gson()
    protected var mSwipeBackHelper: SwipeBackHelper? = null

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

        val wbsetting = wb.settings
        with(wbsetting) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
        }
        wb.webViewClient = object : WebViewClient() {
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

        if (!isNetworkConnected()) {
            "没有网络，请检查当前网络".showToast()
            return
        }

        tvSkip.setOnClickListener {
            imvBg2.visibility = View.GONE
            tvSkip.visibility = View.GONE
        }
        tvReload.setOnClickListener {
//            loadData()
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
            val response = Get<String>(url).await()
            Log.e("111", "response-> $response")
            if (TextUtils.isEmpty(response)) {
                llError.visibility = View.VISIBLE
                return@scopeLife
            }

            imvBg2.visibility = View.VISIBLE
            tvSkip.visibility = View.VISIBLE
//            tvSkip.isEnabled = false
            startSkip()
            loadWeb(url)
//            val data = gson.fromJson(response, BaseResponse::class.java)
//            if (data.code == 2000 && data.data.isNotEmpty()) {
//                imvBg2.visibility = View.VISIBLE
//                tvSkip.visibility = View.VISIBLE
//                tvSkip.isEnabled = false
//                startSkip()
//                loadWeb(data.data[0].domain)
//            } else {
//                llError.visibility = View.VISIBLE
//            }
        }.catch {
            Log.e("111", "loadData catch->")
            llError.visibility = View.VISIBLE
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
//        wb.loadUrl("https://githubwww.com/")
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
