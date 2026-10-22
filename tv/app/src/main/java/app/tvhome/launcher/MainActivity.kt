package app.tvhome.launcher

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var store: HomeStore
    private lateinit var credentials: RelayCredentials
    private lateinit var root: FrameLayout
    private lateinit var atmosphere: AtmosphereView
    private lateinit var photo: ImageView
    private lateinit var shade: View
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var title: TextView
    private lateinit var dock: LinearLayout
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var photoStamp = -1L
    private var photoBitmap: Bitmap? = null
    private var codeDialog: AlertDialog? = null
    private var pairDialog: AlertDialog? = null
    private val dialogs = mutableListOf<java.lang.ref.WeakReference<Dialog>>()
    private fun AlertDialog.showTracked(): AlertDialog {
        dialogs.removeAll { it.get()?.isShowing != true }
        dialogs.add(java.lang.ref.WeakReference(this)); show(); return this
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val value = intent.getStringExtra("value") ?: ""
            when (intent.getStringExtra("type")) {
                "refresh" -> refresh()
                "status" -> { status.text = value; if (value.startsWith("Phone paired")) { codeDialog?.dismiss(); pairDialog?.dismiss() } }
                "error" -> { status.text = value; Toast.makeText(this@MainActivity, value, Toast.LENGTH_LONG).show() }
                "pairCode" -> {
                    codeDialog?.dismiss()
                    codeDialog = AlertDialog.Builder(this@MainActivity).setTitle("Pair your phone")
                        .setMessage("In the TV Home phone app, choose Add TV and enter:\n\n${value.chunked(5).joinToString(" ")}\n\nExpires in 5 minutes. Keep this screen open. You will approve the account on this TV.")
                        .setPositiveButton("Done", null).create().showTracked()
                }
                "pairRequest" -> {
                    val json = JSONObject(value)
                    pairDialog?.dismiss()
                    pairDialog = AlertDialog.Builder(this@MainActivity).setTitle("Allow this account?")
                        .setMessage("${json.getString("email")}\n\nThis account will be able to change wallpapers and control TV Home, including while away from home.")
                        .setPositiveButton("Allow") { _, _ -> decision(json.getString("requestId"), true) }
                        .setNegativeButton("Decline") { _, _ -> decision(json.getString("requestId"), false) }
                        .setOnCancelListener { decision(json.getString("requestId"), false) }.create().showTracked()
                    pairDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        store = HomeStore(this); credentials = RelayCredentials(this)
        buildHome(); refresh()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { dock.getChildAt(0)?.requestFocus() }
        })
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(RemoteService.EVENT)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onResume() {
        super.onResume(); active = this; refresh(); tick.run()
        if (credentials.enabled) startForegroundService(Intent(this, RemoteService::class.java))
    }
    override fun onPause() { if (active === this) active = null; handler.removeCallbacks(tick); super.onPause() }
    override fun onStop() { unregisterReceiver(receiver); codeDialog?.dismiss(); pairDialog?.dismiss(); super.onStop() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); photo.setImageDrawable(null); photoBitmap?.recycle(); super.onDestroy() }
    private val tick = object : Runnable {
        override fun run() {
            time.text = java.text.SimpleDateFormat(if (store.clock24h) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(java.util.Date())
            date.text = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
            handler.postDelayed(this, 1000)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply { text = value; textSize = size; setTextColor(color); fontFeatureSettings = "tnum"; includeFontPadding = false }
    private fun shape(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun focusBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), shape(0xEDDEE7DB.toInt(), 0xFFFFFFFF.toInt()))
        addState(intArrayOf(), shape(0x40223231))
    }
    private fun pill(text: String, action: () -> Unit) = label(text, 13f).apply {
        gravity = Gravity.CENTER; setPadding(dp(19), dp(12), dp(19), dp(12)); background = focusBackground(); isFocusable = true; isClickable = true
        setOnClickListener { action() }
        setOnFocusChangeListener { _, focused -> setTextColor(if (focused) 0xFF172721.toInt() else Color.WHITE) }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) }
    }
    private fun buildHome() {
        root = FrameLayout(this)
        atmosphere = AtmosphereView(this); root.addView(atmosphere, FrameLayout.LayoutParams(-1, -1))
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }; root.addView(photo, FrameLayout.LayoutParams(-1, -1))
        shade = View(this).apply { setBackgroundColor(Color.BLACK) }; root.addView(shade, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x33000000, Color.TRANSPARENT, 0xC7081111.toInt())) }, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val mark = label("TV HOME", 10f, 0xFFC8D5CC.toInt()).apply { letterSpacing = .22f }
        clock.addView(mark)
        time = label("", 48f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setPadding(0, dp(9), 0, dp(4)) }; clock.addView(time)
        date = label("", 12f, 0xFFC8D5CC.toInt()); clock.addView(date)
        header.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        controls.addView(pill("Scenes") { scenes() }); controls.addView(pill("All apps") { library() }); controls.addView(pill("Connect phone") { pair() }); controls.addView(pill("Settings") { settings() })
        header.addView(controls)
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(44), dp(28), dp(44), 0) })
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        title = label("Make this space yours", 27f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setPadding(dp(7), 0, 0, dp(18)) }; bottom.addView(title)
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; setPadding(dp(7), dp(8), dp(7), dp(8)) }
        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        scroll.addView(dock); bottom.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(7), dp(17), dp(7), 0) }
        status = label("", 10f, 0xFFACBBB2.toInt()); footer.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(label("DEVELOPER PREVIEW  /  0.1", 9f, 0xFFACBBB2.toInt()).apply { letterSpacing = .12f })
        bottom.addView(footer)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(37), 0, dp(37), dp(26)) })
        setContentView(root)
    }
    private fun refresh() {
        atmosphere.scene = store.scene; shade.alpha = store.dim
        if (store.scene == "photo" && store.photoFile.exists()) {
            if (photoStamp != store.photoFile.lastModified()) {
                photo.setImageDrawable(null); photoBitmap?.recycle()
                photoBitmap = BitmapFactory.decodeFile(store.photoFile.path); photo.setImageBitmap(photoBitmap); photoStamp = store.photoFile.lastModified()
            }
            photo.visibility = View.VISIBLE
        } else photo.visibility = View.GONE
        status.text = store.roomName + if (credentials.enabled) "  ·  Phone control enabled" else "  ·  Your space, your way"
        val apps = store.apps().associate { it.first to it.second }
        dock.removeAllViews()
        store.favorites.filter { apps.containsKey(it) }.forEach { pkg -> dock.addView(appCard(pkg, apps.getValue(pkg))) }
        dock.addView(appCard(null, if (store.favorites.isEmpty()) "Choose your apps" else "All apps"))
        title.text = if (store.favorites.isEmpty()) "Make this space yours" else "Your favorites"
        if (currentFocus == null) dock.getChildAt(0)?.requestFocus()
    }
    private fun appCard(pkg: String?, name: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true; background = focusBackground()
            setPadding(dp(12), dp(12), dp(12), dp(12)); layoutParams = LinearLayout.LayoutParams(dp(133), dp(92)).apply { marginEnd = dp(12) }
        }
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        if (pkg != null) runCatching { icon.setImageDrawable(packageManager.getApplicationIcon(pkg)) } else icon.setImageResource(R.drawable.icon)
        card.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))
        val text = label(name, 11f).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(10), 0, 0) }
        card.addView(text, LinearLayout.LayoutParams(-1, -2))
        card.setOnClickListener { if (pkg == null) library() else runCatching { openApp(pkg) }.onFailure { toast(it.message ?: "App unavailable.") } }
        card.setOnLongClickListener { favorites(); true }
        card.setOnFocusChangeListener { _, focused ->
            card.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f).setDuration(140).start()
            text.setTextColor(if (focused) 0xFF172721.toInt() else Color.WHITE)
            if (focused) title.text = name
        }
        return card
    }
    fun openApp(pkg: String) {
        require(store.apps().any { it.first == pkg }) { "This app is not installed on the TV." }
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg) ?: error("App cannot be opened.")
        startActivity(intent)
    }
    fun navigate(key: String) {
        val code = when (key) { "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN; "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT; "select" -> KeyEvent.KEYCODE_DPAD_CENTER; "back" -> KeyEvent.KEYCODE_BACK; "home" -> { dialogs.forEach { it.get()?.dismiss() }; dock.getChildAt(0)?.requestFocus(); return }; else -> error("Unknown remote key.") }
        val target = dialogs.lastOrNull { it.get()?.isShowing == true }?.get()?.window?.decorView ?: window.decorView
        target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
    private fun library() {
        val apps = store.apps()
        if (apps.isEmpty()) { toast("Install TV apps from your TV's app store, then return here."); return }
        AlertDialog.Builder(this).setTitle("All apps").setItems(apps.map { it.second }.toTypedArray()) { _, index -> runCatching { openApp(apps[index].first) }.onFailure { toast("App could not be opened.") } }
            .setNeutralButton("Manage favorites") { _, _ -> favorites() }.setNegativeButton("Close", null).create().showTracked()
    }
    private fun favorites() {
        val apps = store.apps(); val selected = store.favorites.toMutableList()
        AlertDialog.Builder(this).setTitle("Choose up to 8 favorites")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), apps.map { it.first in selected }.toBooleanArray()) { dialog, index, checked ->
                val pkg = apps[index].first
                if (checked && selected.size >= 8) { (dialog as AlertDialog).listView.setItemChecked(index, false); toast("Choose up to 8 apps.") }
                else if (checked) selected.add(pkg) else selected.remove(pkg)
            }.setPositiveButton("Save") { _, _ -> store.update(JSONObject().put("favorites", JSONArray(selected))); refresh() }.setNegativeButton("Cancel", null).create().showTracked()
    }
    private fun scenes() {
        val ids = listOf("dusk", "aurora", "sand", "ocean") + if (store.photoFile.exists()) listOf("photo") else emptyList()
        AlertDialog.Builder(this).setTitle("Choose your atmosphere").setSingleChoiceItems(ids.map { it.replaceFirstChar(Char::uppercase) }.toTypedArray(), ids.indexOf(store.scene)) { dialog, index ->
            store.update(JSONObject().put("scene", ids[index])); refresh(); dialog.dismiss()
        }.setNegativeButton("Close", null).create().showTracked()
    }
    private fun settings() {
        val options = arrayOf("Choose scene", "Manage favorite apps", if (store.clock24h) "Use 12-hour clock" else "Use 24-hour clock", "Pair phone", if (credentials.enabled) "Pause phone control" else "Resume phone control", "Default home screen", "Ambient screensaver settings", "TV inputs", "Disconnect account")
        AlertDialog.Builder(this).setTitle("Make yourself at home").setItems(options) { _, index -> when (index) {
            0 -> scenes(); 1 -> favorites(); 2 -> { store.update(JSONObject().put("clock24h", !store.clock24h)); handler.removeCallbacks(tick); tick.run() }; 3 -> pair()
            4 -> { if (credentials.url.isBlank()) { toast("Online services are not configured in this preview yet."); return@setItems }; credentials.enabled = !credentials.enabled; if (credentials.enabled) startForegroundService(Intent(this, RemoteService::class.java)) else stopService(Intent(this, RemoteService::class.java)); refresh() }
            5 -> systemScreen(Settings.ACTION_HOME_SETTINGS); 6 -> systemScreen(Settings.ACTION_DREAM_SETTINGS); 7 -> systemScreen("android.settings.TV_INPUT_SETTINGS")
            8 -> AlertDialog.Builder(this).setTitle("Disconnect account?").setMessage("This account will lose control of the TV. You can pair again afterwards.").setPositiveButton("Disconnect") { _, _ -> credentials.enabled = true; startForegroundService(Intent(this, RemoteService::class.java).setAction("disconnect")) }.setNegativeButton("Cancel", null).create().showTracked()
        } }.setNegativeButton("Close", null).create().showTracked()
    }
    private fun systemScreen(action: String) { runCatching { startActivity(Intent(action)) }.onFailure { toast("This shortcut is unavailable on this TV. Use your TV's Settings menu.") } }
    private fun pair() {
        if (credentials.url.isBlank()) {
            AlertDialog.Builder(this).setTitle("Phone control unavailable").setMessage("Online services are not configured in this preview yet. You can still use the local launcher and scenes.").setPositiveButton("Close", null).create().showTracked()
            return
        }
        enablePairing()
    }
    private fun enablePairing() {
        credentials.enabled = true
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, RemoteService::class.java).setAction("pair")); status.text = "Connecting to your TV Home service…"
    }
    private fun decision(id: String, approved: Boolean) { startForegroundService(Intent(this, RemoteService::class.java).setAction("decision").putExtra("id", id).putExtra("approved", approved)) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    companion object {
        @Volatile private var activeRef: java.lang.ref.WeakReference<MainActivity>? = null
        var active: MainActivity?
            get() = activeRef?.get()
            private set(value) { activeRef = value?.let { java.lang.ref.WeakReference(it) } }
    }
}
