package com.calebms.openflix.data.server

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.calebms.openflix.data.local.AppDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.Duration
import java.util.Collections


class LocalMediaServer private constructor(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private var server: ApplicationEngine? = null
    val port = 8080

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val _serverAddress = MutableStateFlow<String?>(null)
    val serverAddress: StateFlow<String?> = _serverAddress.asStateFlow()

    private val _isClientConnected = MutableStateFlow(false)
    val isClientConnected: StateFlow<Boolean> = _isClientConnected.asStateFlow()


    private val sessions = Collections.synchronizedSet<DefaultWebSocketServerSession>(mutableSetOf())

    var onProgressReceived: ((RemoteMessage) -> Unit)? = null

    private val _incomingMessages = kotlinx.coroutines.flow.MutableSharedFlow<RemoteMessage>(extraBufferCapacity = 64)
    val incomingMessages: kotlinx.coroutines.flow.SharedFlow<RemoteMessage> = _incomingMessages

    @Volatile
    var lastReportedPositionMs: Long = 0L
        private set

    @Synchronized
    fun start() {
        val ip = getLocalIpAddress()
        if (ip != null) {
            _serverAddress.value = "http://$ip:$port"
        }

        if (server != null) return

        try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Range)
                    exposeHeader(HttpHeaders.ContentRange)
                    exposeHeader(HttpHeaders.AcceptRanges)
                    exposeHeader(HttpHeaders.ContentLength)
                }

                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15)
                    timeout = Duration.ofSeconds(15)
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }

                routing {
                    get("/ping") {
                        call.respondText("OpenFlix Server Active", ContentType.Text.Plain)
                    }

                    get("/stream/video") {
                        val mediaId = call.request.queryParameters["mediaId"]
                        val episodeId = call.request.queryParameters["episodeId"]

                        if (mediaId.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, "Missing mediaId")
                            return@get
                        }

                        val uriString = withContext(Dispatchers.IO) {
                            if (!episodeId.isNullOrBlank()) {
                                db.mediaDao().getEpisodesListForShow(mediaId)
                                    .find { it.id == episodeId }?.localFileUri
                            } else {
                                val item = db.mediaDao().getAllMediaList().find { it.id == mediaId }
                                if (item?.type == "TV_SHOW") {
                                    db.mediaDao().getEpisodesListForShow(mediaId)
                                        .minByOrNull { it.seasonNumber * 1000 + it.episodeNumber }?.localFileUri
                                } else {
                                    item?.localUri
                                }
                            }
                        }

                        if (uriString.isNullOrBlank()) {
                            call.respond(HttpStatusCode.NotFound, "Media or episode file not found")
                            return@get
                        }

                        streamContentUri(call, Uri.parse(uriString))
                    }

                    get("/stream/subtitle") {
                        val mediaId = call.request.queryParameters["mediaId"]
                        val episodeId = call.request.queryParameters["episodeId"]

                        val subUriString = withContext(Dispatchers.IO) {
                            if (!episodeId.isNullOrBlank()) {
                                db.mediaDao().getEpisodesListForShow(mediaId ?: "")
                                    .find { it.id == episodeId }?.subtitleUri
                            } else {
                                val item = db.mediaDao().getAllMediaList().find { it.id == mediaId }
                                if (item?.type == "TV_SHOW") {
                                    db.mediaDao().getEpisodesListForShow(mediaId ?: "")
                                        .minByOrNull { it.seasonNumber * 1000 + it.episodeNumber }?.subtitleUri
                                } else {
                                    item?.subtitleUri
                                }
                            }
                        }

                        if (subUriString.isNullOrBlank()) {
                            call.respond(HttpStatusCode.NotFound, "Subtitle not found")
                            return@get
                        }

                        val uri = Uri.parse(subUriString)
                        val mime = if (subUriString.endsWith(".vtt", ignoreCase = true)) "text/vtt" else "text/plain"
                        streamContentUri(call, uri, mime)
                    }

                    get("/setup") {
                        val html = """
   <!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>OpenFlix Desktop Companion Setup</title>
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: #0f0f10;
            color: #ededed;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: grid;
            place-items: center;
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            background: #18181b;
            border: 1px solid #27272a;
            border-radius: 12px;
            max-width: 460px;
            width: 100%;
            padding: 32px 24px;
            box-shadow: 0 16px 40px rgba(0,0,0,0.6);
        }
        .brand {
            color: #e50914;
            font-size: 24px;
            font-weight: 800;
            letter-spacing: -0.5px;
        }
        .subtitle {
            color: #a1a1aa;
            font-size: 13.5px;
            line-height: 1.5;
            margin: 6px 0 24px;
        }
        .section-title {
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 0.8px;
            color: #71717a;
            margin-bottom: 12px;
        }
        .btn-group {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            margin-bottom: 24px;
        }
        .download-btn {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding: 12px;
            border-radius: 8px;
            font-weight: 600;
            font-size: 14px;
            text-decoration: none;
            transition: opacity 0.15s ease, background-color 0.15s ease;
        }
        .download-btn:active { transform: scale(0.98); }
        .btn-linux { background: #e50914; color: #fff; }
        .btn-linux:hover { background: #c10711; }
        .btn-windows { background: #27272a; color: #fff; border: 1px solid #3f3f46; }
        .btn-windows:hover { background: #323238; }

        details {
            border: 1px solid #27272a;
            border-radius: 8px;
            margin-bottom: 8px;
            background: #121214;
            overflow: hidden;
            font-size: 13px;
        }
        summary {
            padding: 12px 14px;
            cursor: pointer;
            font-weight: 600;
            color: #d4d4d8;
            user-select: none;
        }
        summary:hover { color: #fff; }
        .details-body {
            padding: 0 14px 14px;
            color: #a1a1aa;
            line-height: 1.6;
        }
        .details-body ol { padding-left: 18px; margin: 4px 0 10px; }
        .details-body li { margin-bottom: 4px; }
        code {
            background: #27272a;
            color: #4ade80;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
            font-size: 12px;
        }
        .tagline {
            font-size: 11px;
            font-weight: 700;
            color: #71717a;
            margin: 10px 0 4px;
        }
        .footer {
            margin-top: 20px;
            text-align: center;
            font-size: 12px;
            color: #52525b;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1 class="brand">OpenFlix Companion</h1>
        <p class="subtitle">Stream content directly from your phone to your PC over the local network.</p>

        <div class="section-title">DOWNLOAD CLIENT</div>
        <div class="btn-group">
            <a class="download-btn btn-linux" href="https://cdn.calebms.com/openflix/releases/openflix-desktop.AppImage" download>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14.5v-9l6 4.5-6 4.5z"/></svg>
                Linux
            </a>
            <a class="download-btn btn-windows" href="https://cdn.calebms.com/openflix/releases/openflix-desktop.msi" download>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M0 3.449L9.75 2.1v9.451H0m10.949-9.602L24 0v11.4H10.949M0 12.6h9.75v9.451L0 20.699M10.949 12.6H24V24l-12.901-1.801"/></svg>
                Windows
            </a>
        </div>

        <div class="section-title">SETUP INSTRUCTIONS</div>
        <details open>
            <summary>Linux Setup</summary>
            <div class="details-body">
                <div class="tagline">GUI</div>
                <ol>
                    <li>Right-click <code>.AppImage</code> &rarr; <strong>Properties</strong> &rarr; <strong>Permissions</strong>.</li>
                    <li>Enable <strong>Allow executing file as program</strong>, then double-click to launch.</li>
                </ol>
                <div class="tagline">TERMINAL</div>
                <code>chmod +x openflix-desktop.AppImage && ./openflix-desktop.AppImage</code>
            </div>
        </details>

        <details>
            <summary>Windows Setup</summary>
            <div class="details-body">
                <ol>
                    <li>Run the downloaded <code>.msi</code> installer.</li>
                    <li>Launch <strong>OpenFlix</strong> from the Start Menu.</li>
                </ol>
            </div>
        </details>

        <div class="footer">VLC decoding runtimes embedded</div>
    </div>
</body>
</html>                                                                                                                          
    """.trimIndent()
                        call.respondText(html, ContentType.Text.Html)
                    }

                    webSocket("/ws/remote") {
                        sessions.add(this)
                        _isClientConnected.value = true
                        Log.d("LocalMediaServer", "PC companion connected to remote WebSocket")

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    try {
                                        val message = Json.decodeFromString<RemoteMessage>(text)
                                        if (message.action == CommandAction.SYNC_TICK || message.positionMs > 0) {
                                            lastReportedPositionMs = message.positionMs
                                        }
                                        // Emit all incoming messages (ticks, NEXT_EPISODE, etc.)
                                        _incomingMessages.emit(message)
                                    } catch (e: Exception) {
                                        Log.e("LocalMediaServer", "Failed to parse remote message", e)
                                    }
                                }
                            }
                        } finally {

                            sessions.remove(this)
                            _isClientConnected.value = sessions.isNotEmpty()
                            Log.d("LocalMediaServer", "PC companion disconnected")
                        }
                    }
                }
            }.start(wait = false)

            registerNsdService()
            Log.d("LocalMediaServer", "Server started at ${_serverAddress.value}")
        } catch (e: Exception) {
            Log.e("LocalMediaServer", "Failed to bind server", e)
            server = null
            _serverAddress.value = null
        }
    }

    suspend fun sendCommand(message: RemoteMessage) = withContext(Dispatchers.IO) {
        val payload = Json.encodeToString(message)
        val deadSessions = mutableListOf<DefaultWebSocketServerSession>()

        sessions.forEach { session ->
            try {
                session.send(Frame.Text(payload))
            } catch (e: Exception) {
                deadSessions.add(session)
            }
        }
        sessions.removeAll(deadSessions.toSet())
        _isClientConnected.value = sessions.isNotEmpty()
    }

    @Synchronized
    fun stop() {
        unregisterNsdService()
        server?.stop(1000, 2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        server = null
        _serverAddress.value = null
        _isClientConnected.value = false
        sessions.clear()
        lastReportedPositionMs = 0L
        Log.d("LocalMediaServer", "Server stopped")
    }

    @Synchronized
    fun restart() {
        stop()
        start()
    }

    private fun registerNsdService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "OpenFlix-Server"
            serviceType = "_openflix._tcp"
            port = 8080
        }

        nsdManager = (context.getSystemService(Context.NSD_SERVICE) as? NsdManager)?.apply {
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.d("LocalMediaServer", "NSD service registered: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    private fun unregisterNsdService() {
        registrationListener?.let { nsdManager?.unregisterService(it) }
        registrationListener = null
    }

    private suspend fun streamContentUri(
        call: ApplicationCall,
        uri: Uri,
        overrideMimeType: String? = null
    ) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        val pfd = try {
            contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Log.e("LocalMediaServer", "Failed to open parcel file descriptor", e)
            null
        }

        if (pfd == null) {
            call.respond(HttpStatusCode.NotFound, "File descriptor unavailable")
            return@withContext
        }

        val totalLength = pfd.statSize
        val mimeType = overrideMimeType ?: contentResolver.getType(uri) ?: "video/*"
        val rangeHeader = call.request.headers[HttpHeaders.Range]

        if (rangeHeader != null && totalLength > 0L) {
            val range = parseRange(rangeHeader, totalLength)
            if (range == null) {
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                pfd.close()
                return@withContext
            }

            val (start, end) = range
            val contentLength = end - start + 1

            call.response.status(HttpStatusCode.PartialContent)
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalLength")
            call.response.header(HttpHeaders.ContentLength, contentLength.toString())
            call.response.header(HttpHeaders.ContentType, mimeType)

            val fileInputStream = java.io.FileInputStream(pfd.fileDescriptor)
            fileInputStream.channel.position(start)

            call.respondBytesWriter(ContentType.parse(mimeType), HttpStatusCode.PartialContent) {
                val buffer = ByteArray(64 * 1024)
                var bytesRemaining = contentLength

                try {
                    while (bytesRemaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                        val bytesRead = fileInputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        writeFully(buffer, 0, bytesRead)
                        bytesRemaining -= bytesRead
                    }
                } finally {
                    fileInputStream.close()
                    pfd.close()
                }
            }
        } else {
            call.response.status(HttpStatusCode.OK)
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            if (totalLength > 0) {
                call.response.header(HttpHeaders.ContentLength, totalLength.toString())
            }

            val fileInputStream = java.io.FileInputStream(pfd.fileDescriptor)
            call.respondBytesWriter(ContentType.parse(mimeType), HttpStatusCode.OK) {
                try {
                    val channel = fileInputStream.toByteReadChannel(context = Dispatchers.IO)
                    channel.copyTo(this)
                } finally {
                    fileInputStream.close()
                    pfd.close()
                }
            }
        }
    }

    private fun parseRange(rangeHeader: String, totalLength: Long): Pair<Long, Long>? {
        if (!rangeHeader.startsWith("bytes=")) return null
        val values = rangeHeader.removePrefix("bytes=").split("-")
        val start = values[0].toLongOrNull() ?: 0L
        val end = if (values.size > 1 && values[1].isNotBlank()) {
            values[1].toLongOrNull() ?: (totalLength - 1)
        } else {
            totalLength - 1
        }
        if (start > end || start >= totalLength) return null
        return Pair(start, minOf(end, totalLength - 1))
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())

            val ignoredPrefixes = listOf(
                "rmnet", "ccmni", "pdp", "ppp", "dummy",
                "tun", "tap", "sit", "ip6tnl", "lo"
            )

            val candidateAddresses = mutableListOf<Pair<String, String>>()

            for (intf in interfaces) {
                val name = intf.name.lowercase()

                if (!intf.isUp || intf.isLoopback || ignoredPrefixes.any { name.startsWith(it) }) {
                    continue
                }

                for (addr in java.util.Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue

                        if (!ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            candidateAddresses.add(Pair(name, ip))
                        }
                    }
                }
            }

            // 1. Hotspot Interface Priority (ap0, ap1, softap, swlan, etc.)
            val hotspotIp = candidateAddresses.firstOrNull { (name, _) ->
                name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan")
            }?.second

            if (hotspotIp != null) return hotspotIp

            // 2. Standard Wi-Fi Client Priority (wlan0, wlan1)
            val wifiIp = candidateAddresses.firstOrNull { (name, _) ->
                name.startsWith("wlan") || name.startsWith("eth")
            }?.second

            if (wifiIp != null) return wifiIp

            // 3. Fallback: If Android assigns 192.168.43.1 to an unusually named interface
            val defaultHotspotSubnetIp = candidateAddresses.firstOrNull { (_, ip) ->
                ip.startsWith("192.168.43.")
            }?.second

            return defaultHotspotSubnetIp

        } catch (e: Exception) {
            Log.e("LocalMediaServer", "Error finding IP address", e)
        }
        return null
    }

    companion object {
        @Volatile
        private var INSTANCE: LocalMediaServer? = null

        fun getInstance(context: Context): LocalMediaServer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalMediaServer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}