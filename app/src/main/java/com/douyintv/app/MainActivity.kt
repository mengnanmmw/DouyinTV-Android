package com.douyintv.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.douyintv.app.api.DouyinApi
import com.douyintv.app.databinding.ActivityMainBinding
import com.douyintv.app.server.CookieImportServer
import com.douyintv.app.ui.VideoAdapter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var api: DouyinApi
    private lateinit var adapter: VideoAdapter
    private var importServer: CookieImportServer? = null

    private enum class Tab { RECOMMEND, HOT, FOLLOW, SEARCH }
    private var currentTab = Tab.RECOMMEND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        api = DouyinApi(prefs)

        setupRecyclerView()
        setupSidebar()
        setupSettingsOverlay()

        selectTab(Tab.RECOMMEND)
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter { position ->
            val list = ArrayList(adapter.getItems())
            if (list.isNotEmpty()) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_VIDEOS, list)
                    putExtra(PlayerActivity.EXTRA_INDEX, position)
                }
                startActivity(intent)
            }
        }
        binding.rvVideos.layoutManager = GridLayoutManager(this, 4)
        binding.rvVideos.adapter = adapter
    }

    private fun setupSidebar() {
        binding.btnRecommend.setOnClickListener { selectTab(Tab.RECOMMEND) }
        binding.btnHot.setOnClickListener { selectTab(Tab.HOT) }
        binding.btnFollow.setOnClickListener { selectTab(Tab.FOLLOW) }
        binding.btnSearch.setOnClickListener { selectTab(Tab.SEARCH) }
        binding.btnAuthSettings.setOnClickListener { openSettingsOverlay() }
        binding.btnRefreshFeed.setOnClickListener { loadCurrentTab() }

        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadCurrentTab()
                true
            } else {
                false
            }
        }
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        binding.btnRecommend.isSelected = (tab == Tab.RECOMMEND)
        binding.btnHot.isSelected = (tab == Tab.HOT)
        binding.btnFollow.isSelected = (tab == Tab.FOLLOW)
        binding.btnSearch.isSelected = (tab == Tab.SEARCH)

        binding.etSearch.visibility = if (tab == Tab.SEARCH) View.VISIBLE else View.GONE
        binding.tvSectionTitle.text = when (tab) {
            Tab.RECOMMEND -> "推荐视频"
            Tab.HOT -> "热门精选"
            Tab.FOLLOW -> "关注动态"
            Tab.SEARCH -> "搜索视频"
        }

        if (tab == Tab.SEARCH) {
            binding.etSearch.requestFocus()
        } else {
            loadCurrentTab()
        }
    }

    private fun loadCurrentTab() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            val result = when (currentTab) {
                Tab.RECOMMEND -> api.fetchRecommendFeed()
                Tab.HOT -> api.fetchHotFeed()
                Tab.FOLLOW -> api.fetchFollowFeed()
                Tab.SEARCH -> {
                    val kw = binding.etSearch.text.toString().trim()
                    if (kw.isBlank()) {
                        DouyinApi.ApiResult.Error("请输入搜索关键词")
                    } else {
                        api.searchVideos(kw)
                    }
                }
            }

            binding.progressLoading.visibility = View.GONE
            when (result) {
                is DouyinApi.ApiResult.Success -> {
                    adapter.submitList(result.videos)
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvVideos.requestFocus()
                }
                is DouyinApi.ApiResult.Error -> {
                    adapter.submitList(emptyList())
                    binding.tvEmptyState.text = result.message
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupSettingsOverlay() {
        binding.btnAutoNext.setOnClickListener {
            prefs.autoPlayNext = !prefs.autoPlayNext
            updateSettingsUi()
        }
        binding.btnLoopSingle.setOnClickListener {
            prefs.loopSingle = !prefs.loopSingle
            updateSettingsUi()
        }
        binding.btnClearCookie.setOnClickListener {
            prefs.cookie = ""
            prefs.ttwid = ""
            prefs.proxyBaseUrl = ""
            updateSettingsUi()
            Toast.makeText(this, "已清除本地 Cookie 与代理配置", Toast.LENGTH_SHORT).show()
        }
        binding.btnCloseSettings.setOnClickListener {
            closeSettingsOverlay()
        }
    }

    private fun openSettingsOverlay() {
        updateSettingsUi()
        startImportServer()
        binding.settingsOverlay.visibility = View.VISIBLE
        binding.btnAutoNext.requestFocus()
    }

    private fun closeSettingsOverlay() {
        importServer?.stop()
        importServer = null
        binding.settingsOverlay.visibility = View.GONE
        binding.btnRecommend.requestFocus()
    }

    private fun updateSettingsUi() {
        binding.btnAutoNext.text = "播完自动连播下一个: ${if (prefs.autoPlayNext) "开启" else "关闭"}"
        binding.btnLoopSingle.text = "单视频循环: ${if (prefs.loopSingle) "开启" else "关闭"}"
        val cookieState = if (prefs.cookie.isNotBlank()) "已导入用户 Cookie" else "访客模式 (自动 ttwid)"
        val proxyState = if (prefs.proxyBaseUrl.isNotBlank()) "代理: ${prefs.proxyBaseUrl}" else "直连抖音接口"
        binding.tvCookieStatus.text = "凭据状态: $cookieState · $proxyState"
    }

    private fun startImportServer() {
        importServer?.stop()
        val server = CookieImportServer(8088) { cookie, proxy ->
            runOnUiThread {
                if (cookie.isNotBlank()) prefs.cookie = cookie
                if (proxy.isNotBlank()) prefs.proxyBaseUrl = proxy
                updateSettingsUi()
                Toast.makeText(this, "已接收手机端发送的 Cookie/代理配置", Toast.LENGTH_LONG).show()
                loadCurrentTab()
            }
        }
        server.start()
        importServer = server

        val url = "http://${server.getLocalIpAddress()}:8088"
        binding.tvServerUrl.text = "手机同一Wi-Fi扫码或访问:\n$url"
        binding.ivQrCode.setImageBitmap(generateQrBitmap(url, 360))
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.settingsOverlay.visibility == View.VISIBLE &&
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP
        ) {
            closeSettingsOverlay()
            return true
        }
        if ((event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SETTINGS) &&
            event.action == KeyEvent.ACTION_UP
        ) {
            if (binding.settingsOverlay.visibility == View.VISIBLE) closeSettingsOverlay() else openSettingsOverlay()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        importServer?.stop()
        super.onDestroy()
    }
}
