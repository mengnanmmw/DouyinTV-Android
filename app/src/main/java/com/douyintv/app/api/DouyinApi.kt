package com.douyintv.app.api

import com.douyintv.app.Prefs
import com.douyintv.app.model.DouyinVideo
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DouyinApi(private val prefs: Prefs) {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        const val REFERER = "https://www.douyin.com/"
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    sealed class ApiResult {
        data class Success(val videos: List<DouyinVideo>) : ApiResult()
        data class Error(val message: String) : ApiResult()
    }

    suspend fun fetchRecommendFeed(count: Int = 12): ApiResult = withContext(Dispatchers.IO) {
        val params = "device_platform=webapp&aid=6383&channel=channel_pc_web&module_id=3003101" +
            "&count=$count&filterGids=&presented_ids=&refer_id=&refer_type=10&pull_type=2" +
            "&pc_client_type=1&version_code=170400&version_name=17.4.0&cookie_enabled=true" +
            "&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32" +
            "&browser_name=Chrome&browser_version=124.0.0.0&platform=PC"
        requestDouyinList("https://www.douyin.com/aweme/v1/web/tab/feed/", params, "aweme_list")
    }

    suspend fun fetchHotFeed(count: Int = 12): ApiResult = withContext(Dispatchers.IO) {
        val params = "device_platform=webapp&aid=6383&channel=channel_pc_web&tag_id=300201" +
            "&count=$count&refresh_index=1&video_type_select=1&pc_client_type=1" +
            "&version_code=170400&version_name=17.4.0&cookie_enabled=true&platform=PC"
        requestDouyinList("https://www.douyin.com/aweme/v1/web/channel/feed/", params, "aweme_list")
    }

    suspend fun fetchFollowFeed(count: Int = 12): ApiResult = withContext(Dispatchers.IO) {
        if (prefs.cookie.isBlank()) {
            return@withContext ApiResult.Error("关注列表需要登录 Cookie，请按【凭据/设置】扫码导入")
        }
        val params = "device_platform=webapp&aid=6383&channel=channel_pc_web&count=$count" +
            "&cursor=0&level=1&pull_type=0&pc_client_type=1&version_code=170400&version_name=17.4.0&platform=PC"
        requestDouyinList("https://www.douyin.com/aweme/v1/web/follow/feed/", params, "data")
    }

    suspend fun searchVideos(keyword: String, count: Int = 12): ApiResult = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val params = "device_platform=webapp&aid=6383&channel=channel_pc_web&search_channel=aweme_video_web" +
            "&keyword=$encoded&search_source=normal_search&query_correct_type=1&is_filter_search=0" +
            "&offset=0&count=$count&pc_client_type=1&version_code=170400&version_name=17.4.0&platform=PC"
        requestDouyinList("https://www.douyin.com/aweme/v1/web/general/search/single/", params, "data")
    }

    private fun ensureVisitorTtwid(): String {
        if (prefs.cookie.isNotBlank()) return prefs.cookie
        if (prefs.ttwid.isNotBlank()) return "ttwid=${prefs.ttwid}"

        return try {
            val bodyJson = """{"region":"cn","aid":1768,"needFid":false,"service":"www.ixigua.com","migrate_info":{"ticket":"","source":"node"},"cbUrlProtocol":"https","union":true}"""
            val req = Request.Builder()
                .url("https://ttwid.bytedance.com/ttwid/union/register/")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                val setCookies = resp.headers("Set-Cookie")
                for (sc in setCookies) {
                    if (sc.startsWith("ttwid=")) {
                        val valPart = sc.substringAfter("ttwid=").substringBefore(";")
                        if (valPart.isNotBlank()) {
                            prefs.ttwid = valPart
                            return "ttwid=$valPart"
                        }
                    }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun requestDouyinList(baseUrl: String, queryParams: String, arrayField: String): ApiResult {
        return try {
            val cookieHeader = ensureVisitorTtwid()
            val xBogus = XBogus.sign(queryParams, USER_AGENT)
            val finalUrl = if (prefs.proxyBaseUrl.isNotBlank()) {
                "${prefs.proxyBaseUrl}/proxy?url=" + URLEncoder.encode("$baseUrl?$queryParams&X-Bogus=$xBogus", "UTF-8")
            } else {
                "$baseUrl?$queryParams&X-Bogus=${URLEncoder.encode(xBogus, "UTF-8")}"
            }

            val reqBuilder = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")

            if (cookieHeader.isNotBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return ApiResult.Error("接口请求失败 (HTTP ${resp.code})")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return ApiResult.Error("抖音接口返回空内容，风控拦截中，请在【凭据/设置】扫码导入浏览器 Cookie")
                }

                val root = JsonParser.parseString(body).asJsonObject
                val listEl = root.get(arrayField) ?: root.get("aweme_list")
                if (listEl == null || !listEl.isJsonArray) {
                    return ApiResult.Error("未获取到视频列表（可能需要更新 Cookie）")
                }

                val videos = mutableListOf<DouyinVideo>()
                for (item in listEl.asJsonArray) {
                    val awemeObj = extractAwemeObject(item) ?: continue
                    parseAweme(awemeObj)?.let { videos.add(it) }
                }

                if (videos.isEmpty()) {
                    ApiResult.Error("当前列表暂无可播放视频，请尝试刷新或导入 Cookie")
                } else {
                    ApiResult.Success(videos)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error("网络请求异常: ${e.localizedMessage ?: "未知错误"}")
        }
    }

    private fun extractAwemeObject(el: JsonElement): JsonObject? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        return when {
            obj.has("aweme_info") && obj.get("aweme_info").isJsonObject -> obj.getAsJsonObject("aweme_info")
            obj.has("aweme") && obj.get("aweme").isJsonObject -> obj.getAsJsonObject("aweme")
            obj.has("aweme_id") -> obj
            else -> null
        }
    }

    private fun parseAweme(obj: JsonObject): DouyinVideo? {
        val awemeId = obj.get("aweme_id")?.asString ?: return null
        val desc = obj.get("desc")?.asString?.ifBlank { "无标题视频" } ?: "无标题视频"
        val authorName = obj.getAsJsonObject("author")?.get("nickname")?.asString ?: "抖音用户"
        val diggCount = obj.getAsJsonObject("statistics")?.get("digg_count")?.asLong ?: 0L

        val videoObj = obj.getAsJsonObject("video") ?: return null
        val durationMs = videoObj.get("duration")?.asLong ?: 0L

        val coverUrl = videoObj.getAsJsonObject("cover")
            ?.getAsJsonArray("url_list")
            ?.firstOrNull()?.asString ?: ""

        val playUrls = videoObj.getAsJsonObject("play_addr")
            ?.getAsJsonArray("url_list")
            ?.mapNotNull { it.asString }
            .orEmpty()

        val playUrl = playUrls.firstOrNull { it.startsWith("http") } ?: return null

        return DouyinVideo(
            awemeId = awemeId,
            desc = desc,
            authorName = authorName,
            coverUrl = coverUrl,
            playUrl = playUrl,
            durationMs = durationMs,
            diggCount = diggCount
        )
    }
}
