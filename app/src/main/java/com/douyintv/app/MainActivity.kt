package com.douyintv.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import com.douyintv.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBackPressedTime = 0L
    private var okKeyDownTime = 0L
    private var okLongPressHandled = false

    companion object {
        private const val URL_RECOMMEND = "https://www.douyin.com/?recommend=1"
        private const val URL_DISCOVER = "https://www.douyin.com/discover"
        private const val URL_FOLLOW = "https://www.douyin.com/follow"
        private const val URL_HOT = "https://www.douyin.com/hot"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        prefs = Prefs(this)
        applyOrientation()
        applySidebarVisibility()
        setupSidebarActions()
        setupSettingsOverlay()
        setupWebView(binding.webView)

        binding.webView.loadUrl(URL_RECOMMEND)
        binding.btnRecommend.isSelected = true
        showHintBanner()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = if (prefs.compatMobileUa) MOBILE_UA else DESKTOP_UA
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return !(url.startsWith("http://") || url.startsWith("https://"))
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
                injectTvBridge()
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // Recover WebView gracefully if renderer crashes on low-RAM TV boxes
                val parent = binding.webContainer
                parent.removeView(binding.webView)
                val freshWebView = WebView(this@MainActivity).apply {
                    id = R.id.webView
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                parent.addView(freshWebView, 0)
                setupWebView(freshWebView)
                freshWebView.loadUrl(URL_RECOMMEND)
                Toast.makeText(this@MainActivity, "页面已自动恢复", Toast.LENGTH_SHORT).show()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.addView(view)
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.mainContainer.visibility = View.GONE
            }

            override fun onHideCustomView() {
                customView?.let {
                    binding.fullscreenContainer.removeView(it)
                    customView = null
                }
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                binding.fullscreenContainer.visibility = View.GONE
                binding.mainContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun injectTvBridge() {
        val js = DouyinJsBridge.buildEnhanceScript(prefs.autoPlayNext)
        currentWebView().evaluateJavascript(js, null)
    }

    private fun currentWebView(): WebView {
        return binding.webContainer.findViewById(R.id.webView) ?: binding.webView
    }

    private fun setupSidebarActions() {
        val navButtons = listOf(
            binding.btnRecommend,
            binding.btnDiscover,
            binding.btnFollow,
            binding.btnHot
        )

        fun selectNav(selected: View, url: String) {
            navButtons.forEach { it.isSelected = (it == selected) }
            currentWebView().loadUrl(url)
            currentWebView().requestFocus()
        }

        binding.btnRecommend.setOnClickListener { selectNav(it, URL_RECOMMEND) }
        binding.btnDiscover.setOnClickListener { selectNav(it, URL_DISCOVER) }
        binding.btnFollow.setOnClickListener { selectNav(it, URL_FOLLOW) }
        binding.btnHot.setOnClickListener { selectNav(it, URL_HOT) }

        binding.btnLogin.setOnClickListener {
            currentWebView().evaluateJavascript("window.__douyinTvOpenLogin && window.__douyinTvOpenLogin();", null)
            currentWebView().requestFocus()
            Toast.makeText(this, "请使用手机抖音扫描屏幕二维码登录", Toast.LENGTH_LONG).show()
        }

        binding.btnSettings.setOnClickListener {
            openSettingsOverlay()
        }
    }

    private fun setupSettingsOverlay() {
        updateSettingsLabels()

        val webViewVersion = WebViewCompat.getCurrentWebViewPackage(this)?.versionName ?: "未知"
        binding.tvWebViewVersion.text = "WebView 内核: $webViewVersion · 长按 OK 或按菜单键呼出"

        binding.btnToggleSidebar.setOnClickListener {
            prefs.sidebarVisible = !prefs.sidebarVisible
            applySidebarVisibility()
            updateSettingsLabels()
        }

        binding.btnAutoPlayNext.setOnClickListener {
            prefs.autoPlayNext = !prefs.autoPlayNext
            injectTvBridge()
            updateSettingsLabels()
        }

        binding.btnOrientation.setOnClickListener {
            prefs.orientationMode = (prefs.orientationMode + 1) % 3
            applyOrientation()
            updateSettingsLabels()
        }

        binding.btnCompatMode.setOnClickListener {
            prefs.compatMobileUa = !prefs.compatMobileUa
            currentWebView().settings.userAgentString =
                if (prefs.compatMobileUa) MOBILE_UA else DESKTOP_UA
            currentWebView().reload()
            updateSettingsLabels()
        }

        binding.btnRefresh.setOnClickListener {
            closeSettingsOverlay()
            currentWebView().reload()
        }

        binding.btnClearCache.setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            currentWebView().clearCache(true)
            closeSettingsOverlay()
            currentWebView().loadUrl(URL_RECOMMEND)
            Toast.makeText(this, "已清除缓存并退出登录", Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseSettings.setOnClickListener {
            closeSettingsOverlay()
        }
    }

    private fun updateSettingsLabels() {
        binding.btnToggleSidebar.text =
            if (prefs.sidebarVisible) "隐藏左侧导航栏 (沉浸模式)" else "显示左侧导航栏"
        binding.btnAutoPlayNext.text =
            "播完自动切下一个: ${if (prefs.autoPlayNext) "开启" else "关闭"}"
        val orientText = when (prefs.orientationMode) {
            1 -> "强制横屏"
            2 -> "强制竖屏"
            else -> "自动"
        }
        binding.btnOrientation.text = "画面方向: $orientText"
        binding.btnCompatMode.text =
            "兼容模式 (手机版UA): ${if (prefs.compatMobileUa) "开启" else "关闭"}"
    }

    private fun applySidebarVisibility() {
        binding.sidebarLayout.visibility = if (prefs.sidebarVisible) View.VISIBLE else View.GONE
    }

    private fun applyOrientation() {
        requestedOrientation = when (prefs.orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun openSettingsOverlay() {
        updateSettingsLabels()
        binding.settingsOverlay.visibility = View.VISIBLE
        binding.btnToggleSidebar.requestFocus()
    }

    private fun closeSettingsOverlay() {
        binding.settingsOverlay.visibility = View.GONE
        currentWebView().requestFocus()
    }

    private fun showHintBanner() {
        binding.toastBanner.visibility = View.VISIBLE
        handler.postDelayed({ binding.toastBanner.visibility = View.GONE }, 5000)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // 1. Settings overlay open
        if (binding.settingsOverlay.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                closeSettingsOverlay()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // 2. Menu / Red function key opens settings
        if (keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == KeyEvent.KEYCODE_PROG_RED
        ) {
            if (event.action == KeyEvent.ACTION_UP) {
                openSettingsOverlay()
            }
            return true
        }

        // 3. Back button handling
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (customView != null) {
                    currentWebView().webChromeClient?.onHideCustomView()
                    return true
                }
                val wv = currentWebView()
                if (wv.canGoBack()) {
                    wv.goBack()
                    return true
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressedTime < 2000L) {
                    finish()
                } else {
                    lastBackPressedTime = now
                    Toast.makeText(this, "再按一次返回键退出 DouyinTV", Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }

        // 4. If focus is in sidebar, allow normal D-pad navigation & DPAD_RIGHT to WebView
        val focused = currentFocus
        val inSidebar = focused != null && isDescendant(focused, binding.sidebarLayout)
        if (inSidebar) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                currentWebView().requestFocus()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // 5. D-pad controls inside WebView player
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    currentWebView().evaluateJavascript("window.__douyinTvPrev && window.__douyinTvPrev();", null)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    currentWebView().evaluateJavascript("window.__douyinTvNext && window.__douyinTvNext();", null)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    currentWebView().evaluateJavascript("window.__douyinTvSeek && window.__douyinTvSeek(-10);", null)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    currentWebView().evaluateJavascript("window.__douyinTvSeek && window.__douyinTvSeek(10);", null)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        okKeyDownTime = System.currentTimeMillis()
                        okLongPressHandled = false
                    } else if (!okLongPressHandled && System.currentTimeMillis() - okKeyDownTime > 650L) {
                        okLongPressHandled = true
                        openSettingsOverlay()
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (!okLongPressHandled) {
                        currentWebView().evaluateJavascript("window.__douyinTvTogglePlay && window.__douyinTvTogglePlay();", null)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    currentWebView().evaluateJavascript("window.__douyinTvTogglePlay && window.__douyinTvTogglePlay();", null)
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun isDescendant(child: View, parent: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current == parent) return true
            current = current.parent as? View
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        currentWebView().onResume()
    }

    override fun onPause() {
        super.onPause()
        currentWebView().onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        currentWebView().destroy()
        super.onDestroy()
    }
}
