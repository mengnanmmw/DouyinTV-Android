package com.douyintv.app.server

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

class CookieImportServer(
    private val port: Int = 8088,
    private val onConfigReceived: (cookie: String, proxyUrl: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    fun start() {
        if (running.getAndSet(true)) return
        workerThread = Thread {
            try {
                serverSocket = ServerSocket(port)
                while (running.get()) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun handleClient(socket: java.net.Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                var message = ""
                if (requestLine.startsWith("POST") && contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(buf, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                    val body = String(buf, 0, read)
                    val params = parseFormUrlEncoded(body)
                    val cookie = params["cookie"].orEmpty()
                    val proxy = params["proxy"].orEmpty()
                    onConfigReceived(cookie, proxy)
                    message = "<p style='color:#25F4EE;font-weight:bold;'>已成功同步到电视！请返回电视查看。</p>"
                }

                val html = """
                    <!DOCTYPE html>
                    <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>DouyinTV 凭据导入</title>
                    <style>
                      body{background:#121216;color:#fff;font-family:sans-serif;padding:20px;max-width:480px;margin:auto}
                      textarea,input{width:100%;box-sizing:border-box;background:#1e2028;color:#fff;border:1px solid #333;border-radius:8px;padding:10px;margin:8px 0 16px}
                      button{width:100%;background:#FE2C55;color:#fff;border:none;border-radius:8px;padding:12px;font-size:16px;font-weight:bold}
                    </style></head><body>
                    <h2>DouyinTV 电视端凭据导入</h2>
                    $message
                    <form method="POST" action="/">
                      <label>抖音网页版 Cookie (含 ttwid / sessionid):</label>
                      <textarea name="cookie" rows="5" placeholder="从电脑浏览器 F12 复制 douyin.com 的 Cookie..."></textarea>
                      <label>可选：自建解析代理地址 (留空则直连):</label>
                      <input type="text" name="proxy" placeholder="例如 http://192.168.1.10:8000" />
                      <button type="submit">发送到电视</button>
                    </form>
                    </body></html>
                """.trimIndent()

                val bytes = html.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                val out = s.getOutputStream()
                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(bytes)
                out.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun parseFormUrlEncoded(body: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in body.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }
}
