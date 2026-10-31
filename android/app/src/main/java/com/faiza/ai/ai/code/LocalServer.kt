package com.faiza.ai.ai.code

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * A tiny web server on the phone itself (http://127.0.0.1:8790/<project>/) so Code Studio's websites open in any
 * browser on the phone. Only this phone can reach it (bound to 127.0.0.1).
 */
object LocalServer {

    const val PORT = 8790
    @Volatile private var root: File? = null
    @Volatile private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun url(slug: String, file: String = "") = "http://127.0.0.1:$PORT/$slug/$file"

    @Synchronized
    fun ensure(projectsRoot: File) {
        root = projectsRoot
        if (socket?.isClosed == false) return
        val s = runCatching { ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1")) }.getOrNull() ?: return
        socket = s
        Thread({
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }, "faiza-local-server").apply { isDaemon = true }.start()
    }

    private fun handle(c: Socket) {
        c.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(c.getInputStream()))
        val first = reader.readLine() ?: return
        while (true) { val l = reader.readLine() ?: break; if (l.isEmpty()) break }
        val parts = first.split(' ')
        val method = parts.getOrNull(0) ?: "GET"
        var path = URLDecoder.decode(parts.getOrNull(1)?.substringBefore('?') ?: "/", "UTF-8")
        val out = c.getOutputStream()
        val base = root ?: return send(out, 503, "text/plain", "not ready".toByteArray(), method)
        if (path == "/" || path.isEmpty()) {
            val list = (base.listFiles() ?: emptyArray()).filter { it.isDirectory && !it.name.startsWith(".") }
                .joinToString("") { "<li><a href=\"/${it.name}/\">${it.name}</a></li>" }
            return send(out, 200, "text/html; charset=utf-8", "<h2>Faiza Code</h2><ul>$list</ul>".toByteArray(), method)
        }
        var f = File(base, path.trimStart('/'))
        if (!f.canonicalPath.startsWith(base.canonicalPath)) return send(out, 403, "text/plain", "forbidden".toByteArray(), method)
        if (f.isDirectory) {
            if (!path.endsWith("/")) {
                path += "/"
                val h = "HTTP/1.1 301 Moved\r\nLocation: $path\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                out.write(h.toByteArray()); out.flush(); return
            }
            f = File(f, "index.html")
        }
        if (!f.isFile) return send(out, 404, "text/html; charset=utf-8", "<h3>404 — ${path}</h3>".toByteArray(), method)
        send(out, 200, mime(f.name), f.readBytes(), method)
    }

    private fun send(out: java.io.OutputStream, code: Int, type: String, body: ByteArray, method: String) {
        val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error" }
        val h = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray())
        if (method != "HEAD") out.write(body)
        out.flush()
    }

    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "txt", "md", "py", "csv" -> "text/plain; charset=utf-8"
        "wasm" -> "application/wasm"
        else -> "application/octet-stream"
    }
}
