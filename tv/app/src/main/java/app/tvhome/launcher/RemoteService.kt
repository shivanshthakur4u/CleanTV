package app.tvhome.launcher

import android.app.*
import android.content.Intent
import android.os.*
import android.view.KeyEvent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

class RemoteService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    private lateinit var credentials: RelayCredentials
    private lateinit var store: HomeStore
    private var socket: WebSocket? = null
    private var connected = false
    private var connecting = false
    private var pairRequested = false
    private var retry = 0
    private var stopping = false

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate(); credentials = RelayCredentials(this); store = HomeStore(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("phone", "Phone control", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(1, Notification.Builder(this, "phone").setSmallIcon(R.drawable.icon).setContentTitle("TV Home phone control").setContentText("Your paired account can change this TV's appearance.").setContentIntent(open).setOngoing(true).build())
    }
    private fun queue(action: () -> Unit) { if (!worker.isShutdown) runCatching { worker.execute(action) } }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!credentials.enabled) { stopSelf(); return START_NOT_STICKY }
        queue {
            if (intent?.action == "disconnect") {
                try { request("/v1/tv/disconnect", JSONObject()); credentials.clear(); event("status", "Account disconnected"); event("refresh", ""); stopSelf() }
                catch (_: Exception) { event("error", "Could not disconnect. Check the TV's internet connection and try again.") }
                return@queue
            }
            if (intent?.action == "decision") {
                socket?.send(JSONObject().put("type", "pairDecision").put("requestId", intent.getStringExtra("id")).put("approved", intent.getBooleanExtra("approved", false)).toString())
            } else {
                if (intent?.action == "pair") pairRequested = true
                if (connected && pairRequested) pairing() else if (!connected && !connecting) connect()
            }
        }
        return START_STICKY
    }
    private fun event(type: String, value: String) { sendBroadcast(Intent(EVENT).setPackage(packageName).putExtra("type", type).putExtra("value", value)) }
    private fun request(path: String, body: JSONObject, authenticated: Boolean = true): JSONObject {
        val builder = Request.Builder().url(credentials.url + path).post(body.toString().toRequestBody("application/json".toMediaType()))
        if (authenticated) builder.header("Authorization", "Device ${credentials.token ?: error("Pair this TV again.")}")
        client.newCall(builder.build()).execute().use { response ->
            val json = JSONObject(response.body?.string() ?: "{}")
            if (response.code == 401) { credentials.clear(); stopping = true; event("error", "Access was revoked. Pair this TV again."); stopSelf() }
            check(response.isSuccessful) { json.optString("error", "Service unavailable. Try again shortly.") }
            return json
        }
    }
    private fun connect() {
        if (stopping || !credentials.enabled) return
        connecting = true
        try {
            require(credentials.url.startsWith("https://") || BuildConfig.DEBUG && credentials.url.startsWith("http://")) { "Online services are unavailable in this build." }
            if (credentials.token == null) {
                val registration = request("/v1/tv/register", JSONObject().put("name", store.roomName), false)
                credentials.save(registration.getString("id"), registration.getString("token"))
            }
            val wsUrl = credentials.url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/tv/socket"
            socket = client.newWebSocket(Request.Builder().url(wsUrl).header("Authorization", "Device ${credentials.token}").build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { queue { connected = true; connecting = false; retry = 0; event("status", "Phone control connected"); if (pairRequested) pairing() } }
                override fun onMessage(webSocket: WebSocket, text: String) { queue { if (socket === webSocket) receive(text) } }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { queue {
                    if (socket !== webSocket) return@queue
                    if (code == 4001) { credentials.clear(); stopping = true; event("error", "This TV was removed from the account. Pair again to reconnect."); stopSelf() }
                    else disconnected()
                } }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { queue {
                    if (socket !== webSocket) return@queue
                    if (response?.code == 401) { credentials.clear(); stopping = true; event("error", "Pair this TV again."); stopSelf() } else disconnected()
                } }
            })
        } catch (_: Exception) { connecting = false; event("error", "Cannot connect. Check the internet connection and try again."); scheduleReconnect() }
    }
    private fun disconnected() { connected = false; connecting = false; socket = null; event("status", "Phone control offline · reconnecting"); scheduleReconnect() }
    private fun scheduleReconnect() {
        if (stopping || !credentials.enabled) return
        val delay = min(60000L, 1000L shl min(retry++, 6)) + (0..1000).random()
        main.removeCallbacks(reconnect); main.postDelayed(reconnect, delay)
    }
    private val reconnect = Runnable { queue { if (!connected && !connecting) connect() } }
    private fun pairing() {
        pairRequested = false
        try { event("pairCode", request("/v1/tv/pairing", JSONObject()).getString("code")) }
        catch (error: Exception) { event("error", error.message ?: "Could not create a pairing code.") }
    }
    private fun receive(text: String) {
        try {
            val json = JSONObject(text)
            when (json.getString("type")) {
                "pairRequest" -> event("pairRequest", json.toString())
                "pairComplete" -> event("status", if (json.optBoolean("approved")) "Phone paired · ready to customize" else "Pairing declined")
                "command" -> {
                    val id = json.getString("requestId")
                    val expires = json.getLong("expiresAt")
                    if (expires < System.currentTimeMillis() || expires > System.currentTimeMillis() + 30000) { result(id, "Command expired. Refresh and try again."); return }
                    val command = json.getJSONObject("command")
                    when (command.getString("type")) {
                        "getState" -> result(id)
                        "settings" -> { store.update(command.getJSONObject("settings")); event("refresh", ""); result(id) }
                        "wallpaper" -> { store.upload(command.getString("data")); event("refresh", ""); result(id) }
                        "launchApp", "navigate" -> main.post {
                            try {
                                val activity = MainActivity.active ?: error("Open TV Home on the television to use these controls.")
                                if (System.currentTimeMillis() > expires) error("Command expired. Try again.")
                                if (command.getString("type") == "launchApp") activity.openApp(command.getString("package"))
                                else activity.navigate(command.getString("key"))
                                queue { result(id) }
                            } catch (error: Exception) { queue { result(id, error.message ?: "Control is unavailable on this TV.") } }
                        }
                        else -> result(id, "Unsupported command.")
                    }
                }
            }
        } catch (error: Exception) {
            runCatching { val json = JSONObject(text); if (json.optString("type") == "command") result(json.getString("requestId"), error.message?.take(250) ?: "TV could not apply this change.") }
        }
    }
    private fun result(id: String, error: String? = null) {
        val json = JSONObject().put("type", "result").put("requestId", id).put("ok", error == null)
        if (error == null) json.put("state", store.snapshot()) else json.put("error", error.take(300))
        socket?.send(json.toString())
    }
    override fun onDestroy() {
        stopping = true; main.removeCallbacksAndMessages(null); socket?.close(1000, "Phone control paused")
        worker.shutdownNow(); client.dispatcher.executorService.shutdown(); super.onDestroy()
    }
    companion object { const val EVENT = "app.tvhome.launcher.REMOTE_EVENT" }
}
