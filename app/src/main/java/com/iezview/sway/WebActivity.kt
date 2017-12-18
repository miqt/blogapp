package com.iezview.sway

import android.Manifest.permission.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.webkit.*
import android.webkit.WebSettings.LayoutAlgorithm
import android.widget.Toast
import com.google.gson.Gson
import com.umeng.socialize.ShareAction
import com.umeng.socialize.UMShareAPI
import com.umeng.socialize.UMShareListener
import com.umeng.socialize.bean.SHARE_MEDIA
import com.umeng.socialize.media.UMImage
import com.umeng.socialize.media.UMWeb
import okhttp3.*
import java.io.IOException


/**
 * 网页展示界面
 */
class WebActivity : AppCompatActivity() {

    lateinit var web_view: WebView
    lateinit var srl_layout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        findView()
        settingView()
        checkPermission()
           loadUrl()
//        web_view.loadUrl(cfg.url)

    }

    /**
     * 访问服务器获取根路径并加载网页
     */
    private fun loadUrl() {
        OkHttpClient().newCall(Request.Builder()
                .url("http://www.sway-3d.com:51/")
                .build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call?, e: IOException?) {
                        runOnUiThread {
                            Toast.makeText(this@WebActivity, resources.getString(R.string.SERVICE_ERROR), Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call?, response: Response?) {
                        if (response == null || !response.isSuccessful) {
                            runOnUiThread {
                                Toast.makeText(this@WebActivity, resources.getString(R.string.SERVICE_ERROR), Toast.LENGTH_SHORT).show()
                            }
                        }
                        val result = response?.body()?.string()
                        val urls = Gson().fromJson(result, Url::class.java)
                        runOnUiThread { web_view.loadUrl(urls.sway3d) }
                    }

                })
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = packageManager
            val permission = PackageManager.PERMISSION_GRANTED == pm.checkPermission("android.permission.WRITE_EXTERNAL_STORAGE", packageName)
            if (!permission) {
                val mPermissionList = arrayOf<String>(
                        WRITE_EXTERNAL_STORAGE,
                        ACCESS_FINE_LOCATION,
                        CALL_PHONE, READ_LOGS,
                        READ_PHONE_STATE,
                        READ_EXTERNAL_STORAGE,
                        SET_DEBUG_APP,
                        SYSTEM_ALERT_WINDOW,
                        GET_ACCOUNTS,
                        WRITE_APN_SETTINGS)
                ActivityCompat.requestPermissions(this, mPermissionList, 123)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {

    }


    private fun settingView() {
        settingRefview()
        settingWebView()
    }

    private fun settingRefview() {
        srl_layout.isEnabled = false
        srl_layout.setColorSchemeColors(Color.RED, Color.GRAY)
        srl_layout.setOnRefreshListener {
            web_view.loadUrl(web_view.url)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        UMShareAPI.get(this).onActivityResult(requestCode, resultCode, data)
    }

    private fun settingWebView() {
        web_view.settings.layoutAlgorithm = LayoutAlgorithm.SINGLE_COLUMN
        web_view.settings.useWideViewPort = true
        web_view.settings.loadWithOverviewMode = true
        web_view.settings.javaScriptEnabled = true
        web_view.addJavascriptInterface(JSHook(), "share")
        web_view.setWebViewClient(object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    when {
                    //打开网页
                        url.startsWith("http") -> view.loadUrl(url)
                    //发邮件
                        url.startsWith("mailto") -> mailto(url)
                    //打电话
                        url.startsWith("tel") -> callTo(url)
                    }
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val url = request.url.toString()
                    when {
                    //打开网页
                        url.startsWith("http") -> view.loadUrl(request.url.toString())
                    //发邮件
                        url.startsWith("mailto") -> mailto(url)
                    //打电话
                        url.startsWith("tel") -> callTo(url)
                    }
                }
                return true
            }
        })
        web_view.setWebChromeClient(object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!srl_layout.isRefreshing && newProgress != 100) {
                    //    srl_layout.isRefreshing = true
                }
                if (newProgress == 100) {
                    srl_layout.isRefreshing = false
                }
                super.onProgressChanged(view, newProgress)
            }
        })
    }

    private fun findView() {
        web_view = findViewById(R.id.webview) as WebView
        srl_layout = findViewById(R.id.srl_layout) as SwipeRefreshLayout
    }

    /**
     * 拨打电话
     */
    private fun callTo(url: String) {
        val intent = Intent()
        intent.action = Intent.ACTION_DIAL   //android.intent.action.DIAL
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    /**
     * 发送邮件
     */
    private fun mailto(url: String) {
        val data = Intent(Intent.ACTION_SENDTO)
        data.data = Uri.parse(url)
        data.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.id.mail_subject))
        data.putExtra(Intent.EXTRA_TEXT, resources.getString(R.id.mail_text))
        startActivity(data)
    }

    /**
     * 监听手机按键
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        //优先在web页面中back
        if (web_view.canGoBack() && keyCode == KeyEvent.KEYCODE_BACK) {
            web_view.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    inner class JSHook {
        /**
         * 分享
         */
        @JavascriptInterface
        fun javascriptShare(title: String, imgUrl: String, url: String, text: String) {
            val web = UMWeb(url)
            web.setThumb(UMImage(this@WebActivity, imgUrl))
            web.title = title
            web.description = text
            ShareAction(this@WebActivity)
                    .withMedia(web)
                    .setDisplayList(SHARE_MEDIA.QQ, SHARE_MEDIA.QZONE, SHARE_MEDIA.WEIXIN, SHARE_MEDIA.WEIXIN_CIRCLE)
                    .setCallback(umShareListener)
                    .open()
        }

        @JavascriptInterface
        fun isSwayApp(): Boolean = true
    }
}


object umShareListener : UMShareListener {
    override fun onResult(p0: SHARE_MEDIA?) {
    }

    override fun onCancel(p0: SHARE_MEDIA?) {
    }

    override fun onError(p0: SHARE_MEDIA?, p1: Throwable?) {
    }

    override fun onStart(p0: SHARE_MEDIA?) {
    }

}
