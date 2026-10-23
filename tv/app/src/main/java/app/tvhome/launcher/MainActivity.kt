package app.tvhome.launcher

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.*
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputFilter
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var store: HomeStore
    private lateinit var credentials: RelayCredentials
    private lateinit var weather: Weather
    private lateinit var backdrop: FrameLayout
    private lateinit var atmosphere: AtmosphereView
    private lateinit var photo: ImageView
    private lateinit var shade: View
    private lateinit var page: ScrollView
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var pillHost: FrameLayout
    private lateinit var shelfFrame: FrameLayout
    private lateinit var shelf: LinearLayout
    private lateinit var gridTitle: TextView
    private lateinit var grid: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor()
    private val accents = HashMap<String, Int>()
    private var pillTime: TextView? = null
    private var overlayKind: String? = null
    private var returnFocus: View? = null
    private var lastKey: String? = null
    private var dark = false
    private var lastMinute = -1
    private var weatherAttempt = 0L
    private var tileW = 0
    private var tileH = 0
    private var gap = 0
    private var side = 0
    private var photoStamp = -1L
    private var photoBitmap: Bitmap? = null
    private var codeDialog: AlertDialog? = null
    private var pairDialog: AlertDialog? = null
    private val dialogs = mutableListOf<java.lang.ref.WeakReference<Dialog>>()
    private fun AlertDialog.showTracked(): AlertDialog {
        dialogs.removeAll { it.get()?.isShowing != true }
        dialogs.add(java.lang.ref.WeakReference(this)); show(); return this
    }
    private val ink get() = if (!dark && store.scene == "adaptive") 0xFF1C1C1E.toInt() else Color.WHITE
    private val liquid get() = store.glass == "liquid"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val value = intent.getStringExtra("value") ?: ""
            when (intent.getStringExtra("type")) {
                "refresh" -> render()
                "status" -> { showStatus(value); if (value.startsWith("Phone paired")) { codeDialog?.dismiss(); pairDialog?.dismiss() } }
                "error" -> { showStatus(value); toast(value) }
                "pairCode" -> {
                    codeDialog?.dismiss()
                    codeDialog = dialog().setTitle("Pair your phone")
                        .setMessage("In the TV Home phone app, choose Add TV and enter:\n\n${value.chunked(5).joinToString(" ")}\n\nExpires in 5 minutes. Keep this screen open. You will approve the account on this TV.")
                        .setPositiveButton("Done", null).create().showTracked()
                }
                "pairRequest" -> {
                    val json = JSONObject(value)
                    pairDialog?.dismiss()
                    pairDialog = dialog().setTitle("Allow this account?")
                        .setMessage("${json.getString("email")}\n\nThis account will be able to change wallpapers and control TV Home, including while away from home.")
                        .setPositiveButton("Allow") { _, _ -> decision(json.getString("requestId"), true) }
                        .setNegativeButton("Decline") { _, _ -> decision(json.getString("requestId"), false) }
                        .setOnCancelListener { decision(json.getString("requestId"), false) }.create().showTracked()
                    pairDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
                }
            }
        }
    }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { store.invalidate(); accents.clear(); render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        store = HomeStore(this); credentials = RelayCredentials(this); weather = Weather(this)
        buildHome()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (overlayKind != null) close() else { page.smoothScrollTo(0, 0); firstTile()?.requestFocus() } }
        })
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, receiver, IntentFilter(RemoteService.EVENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package") }
        ContextCompat.registerReceiver(this, packages, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onResume() {
        super.onResume(); active = this; store.invalidate(); render(); tick.run()
        if (credentials.enabled) startForegroundService(Intent(this, RemoteService::class.java))
    }
    override fun onPause() { if (active === this) active = null; handler.removeCallbacks(tick); super.onPause() }
    override fun onStop() {
        unregisterReceiver(receiver); unregisterReceiver(packages); codeDialog?.dismiss(); pairDialog?.dismiss()
        close(); release(); super.onStop()
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); background.shutdownNow(); super.onDestroy() }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) release() }

    /** Drops tile pictures and the wallpaper while another app is in front; they are redrawn on return. */
    private fun release() {
        shelf.removeAllViews(); grid.removeAllViews(); accents.clear()
        photo.setImageDrawable(null); photoBitmap?.recycle(); photoBitmap = null; photoStamp = -1L
    }

    private fun paintClock() {
        val now = java.util.Date()
        val clock = java.text.SimpleDateFormat(if (store.clock24h) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(now)
        time.text = clock; pillTime?.text = if (store.clock24h) clock else java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(now).lowercase()
    }
    private val tick = object : Runnable {
        override fun run() {
            paintClock()
            val minute = (System.currentTimeMillis() / 60_000).toInt()
            if (minute != lastMinute) { lastMinute = minute; if (store.darkNow() != dark) render() else renderHeader(); refreshWeather() }
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    // region Building blocks
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply { text = value; textSize = size; setTextColor(color); fontFeatureSettings = "tnum"; includeFontPadding = false }
    private fun glyph(name: String, color: Int, size: Int) = ImageView(this).apply { setImageDrawable(Glyph(name, color)); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
    private fun round(v: View, radius: Float) { v.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, radius) } }; v.clipToOutline = true }
    private fun pillDrawable() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(99).toFloat() })
        addState(intArrayOf(), Look.shelf(liquid, true, dp(99).toFloat(), dp(1)))
    }
    private val darkFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(.74f, .74f, .74f, 1f) })

    private fun buildHome() {
        val metrics = resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels); val height = minOf(metrics.widthPixels, metrics.heightPixels)
        side = (width * .075f).toInt(); gap = dp(20)
        tileW = (width - 2 * side - 5 * gap) / 6; tileH = tileW * 9 / 16
        val root = FrameLayout(this)
        backdrop = FrameLayout(this)
        atmosphere = AtmosphereView(this); backdrop.addView(atmosphere, FrameLayout.LayoutParams(-1, -1))
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }; backdrop.addView(photo, FrameLayout.LayoutParams(-1, -1))
        shade = View(this).apply { setBackgroundColor(Color.BLACK) }; backdrop.addView(shade, FrameLayout.LayoutParams(-1, -1))
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        page = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; clipChildren = false; clipToPadding = false }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(side, dp(26), side, dp(48)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        time = label("", 46f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setShadowLayer(dp(8).toFloat(), 0f, 0f, 0x40000000) }; clock.addView(time)
        date = label("", 13f).apply { setPadding(0, dp(6), 0, 0); setShadowLayer(dp(6).toFloat(), 0f, 0f, 0x40000000) }; clock.addView(date)
        header.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        pillHost = FrameLayout(this).apply { clipChildren = false }; header.addView(pillHost, LinearLayout.LayoutParams(-2, -2))
        column.addView(header)
        column.addView(View(this), LinearLayout.LayoutParams(-1, (height * .25f).toInt()))

        shelfFrame = FrameLayout(this).apply { clipChildren = false; clipToPadding = false; setPadding(dp(14), dp(14), dp(14), dp(2)) }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFillViewport = true }
        shelf = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        scroll.addView(shelf); shelfFrame.addView(scroll)
        column.addView(shelfFrame, LinearLayout.LayoutParams(-1, -2).apply { setMargins(-dp(14), 0, -dp(14), 0) })
        gridTitle = label("", 13f).apply { setPadding(0, dp(28), 0, 0); letterSpacing = .04f }
        column.addView(gridTitle)
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        column.addView(grid)
        page.addView(column)
        root.addView(page, FrameLayout.LayoutParams(-1, -1))

        overlay = FrameLayout(this).apply { visibility = View.GONE; isClickable = true }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        status = label("", 11f).apply { alpha = 0f; setPadding(dp(14), dp(8), dp(14), dp(8)); background = GradientDrawable().apply { setColor(0xB3000000.toInt()); cornerRadius = dp(99).toFloat() } }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(side, 0, 0, dp(18)) })
        setContentView(root)
    }
    // endregion

    // region Home rendering
    private fun render() {
        store.seedFavorites()
        dark = store.darkNow()
        atmosphere.dark = dark; atmosphere.scene = store.scene
        shade.alpha = (store.dim + if (dark && store.scene != "adaptive") .3f else 0f).coerceAtMost(.85f)
        if (store.scene == "photo" && store.photoFile.exists()) {
            if (photoStamp != store.photoFile.lastModified()) {
                photo.setImageDrawable(null); photoBitmap?.recycle()
                photoBitmap = BitmapFactory.decodeFile(store.photoFile.path); photo.setImageBitmap(photoBitmap); photoStamp = store.photoFile.lastModified()
            }
            photo.visibility = View.VISIBLE
        } else { photo.visibility = View.GONE; photo.setImageDrawable(null); photoBitmap?.recycle(); photoBitmap = null; photoStamp = -1L }
        renderHeader(); renderPill()
        shelfFrame.background = Look.shelf(liquid, dark || store.scene != "adaptive", dp(26).toFloat(), dp(1))
        val keep = lastKey
        val apps = store.apps(); val byPkg = apps.associateBy { it.pkg }
        val favorites = store.favorites; val folders = store.folders; val hidden = store.hidden.toSet()
        shelf.removeAllViews()
        favorites.mapNotNull { byPkg[it] }.forEach { shelf.addView(appTile(it), cell()) }
        if (favorites.isEmpty()) shelf.addView(actionTile("choose", "Choose favourites", "plus") { favoritesPicker() }, cell())
        val filed = folders.flatMap { it.apps }.toSet()
        val items = folders.map { folderTile(it, byPkg) } + apps.filter { it.pkg !in favorites && it.pkg !in hidden && it.pkg !in filed }.map { appTile(it) }
        grid.removeAllViews()
        gridTitle.text = if (items.isEmpty()) "" else "All apps"; gridTitle.setTextColor(ink)
        items.chunked(6).forEach { row ->
            grid.addView(LinearLayout(this).apply { clipChildren = false; clipToPadding = false; setPadding(0, dp(18), 0, 0); row.forEach { addView(it, cell()) } })
        }
        if (overlayKind == null) {
            val target = keep?.let { root().findViewWithTag<View>(it) } ?: firstTile()
            if (currentFocus == null || currentFocus?.isAttachedToWindow != true || keep != null) target?.requestFocus()
        }
    }
    private fun root() = window.decorView
    private fun cell() = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = gap }
    private fun firstTile(): View? = (shelf.getChildAt(0) as? ViewGroup)?.getChildAt(0)

    private fun renderHeader() {
        val today = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
        val w = weather.takeIf { it.city.isNotBlank() && it.city == store.weatherCity }
        date.text = if (w == null) today else "$today  ·  ${w.temp}°  ${w.summary}${if (w.place.isNotBlank()) " in ${w.place}" else ""}"
    }

    private fun renderPill() {
        pillHost.removeAllViews(); pillTime = null
        val style = store.pill
        val tinted = mutableListOf<Any>()
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true; tag = "pill"
            background = pillDrawable(); setPadding(dp(16), dp(7), dp(7), dp(7))
            setOnClickListener { controlCenter() }
            contentDescription = "Control Center"
        }
        fun text(value: String) = label(value, 14f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); tinted += this }
        fun icon(name: String) = glyph(name, Color.WHITE, 20).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(14); tinted += drawable }
        val w = weather.takeIf { it.city.isNotBlank() && it.city == store.weatherCity }
        when (style) {
            "weather" -> { pill.setPadding(dp(12), dp(12), dp(12), dp(12)); pill.addView(text(if (w != null) "${w.temp}°" else "--°")) }
            "hidden" -> { pill.setPadding(dp(9), dp(9), dp(9), dp(9)); pill.addView(glyph("gear", Color.WHITE, 20).also { tinted += it.drawable }) }
            else -> {
                pill.addView(text("").also { pillTime = it })
                if (style == "full") {
                    if (w != null) { pill.addView(icon(w.glyph)); pill.addView(text(" ${w.temp}°")) }
                    pill.addView(icon(network())); if (bluetoothOn() == true) pill.addView(icon("bluetooth")); pill.addView(icon("gear"))
                    pill.setPadding(dp(16), dp(9), dp(16), dp(9))
                } else pill.addView(avatar(26).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(12) })
            }
        }
        pill.setOnFocusChangeListener { _, focused ->
            val color = if (focused) 0xFF1C1C1E.toInt() else Color.WHITE
            tinted.forEach { (it as? TextView)?.setTextColor(color); (it as? Glyph)?.tint(color) }
            pill.animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f).setDuration(140).start()
        }
        pillHost.addView(pill)
        paintClock()
    }
    private fun avatar(size: Int) = label(store.roomName.take(1).uppercase(), size * .42f).apply {
        gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF5AC8FA.toInt(), 0xFF2F6FDB.toInt())).apply { shape = GradientDrawable.OVAL }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
    private fun network(): String {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "ethernet" else "wifi"
    }
    private fun bluetoothOn(): Boolean? = runCatching { getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()

    private fun tile(key: String, name: String, art: View, alwaysLabel: Boolean, accent: () -> Int, onClick: () -> Unit, onLong: () -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val frame = FrameLayout(this).apply { isFocusable = true; isClickable = true; isLongClickable = true; tag = key; contentDescription = name; addView(art, FrameLayout.LayoutParams(-1, -1)) }
        round(frame, dp(12).toFloat())
        box.addView(frame, LinearLayout.LayoutParams(tileW, tileH))
        val text = label(name, 12f, ink).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; alpha = if (alwaysLabel) 1f else 0f; setPadding(0, dp(10), 0, dp(2)) }
        box.addView(text, LinearLayout.LayoutParams(tileW, -2))
        frame.setOnClickListener { onClick() }
        frame.setOnLongClickListener { onLong(); true }
        frame.setOnFocusChangeListener { _, focused ->
            frame.animate().scaleX(if (focused) 1.1f else 1f).scaleY(if (focused) 1.1f else 1f).translationZ(if (focused) dp(16).toFloat() else 0f).setDuration(160).start()
            text.animate().alpha(if (focused || alwaysLabel) 1f else 0f).translationY(if (focused) dp(4).toFloat() else 0f).setDuration(160).start()
            if (focused) { lastKey = key; atmosphere.tint(accent()) }
        }
        return box
    }
    private fun iconPlate(icon: Drawable?) = FrameLayout(this).apply {
        background = if (dark) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF3A3A3C.toInt(), 0xFF242426.toInt()))
            else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.WHITE, 0xFFE9E9EE.toInt()))
        addView(ImageView(this@MainActivity).apply { setImageDrawable(icon) }, FrameLayout.LayoutParams((tileH * .62f).toInt(), (tileH * .62f).toInt(), Gravity.CENTER))
    }
    private fun art(app: App): Pair<View, Drawable?> {
        val banner = if (store.iconStyle == "banner" && app.leanback) runCatching { app.info.loadBanner(packageManager) }.getOrNull() else null
        if (banner != null) return ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY; setImageDrawable(banner); if (dark) colorFilter = darkFilter } to banner
        val icon = runCatching { app.info.loadIcon(packageManager) }.getOrNull()
        return iconPlate(icon) to icon
    }
    private fun appTile(app: App, folder: String? = null): View {
        val (view, picture) = art(app)
        return tile(app.pkg, app.label, view, false, { accents.getOrPut(app.pkg) { picture?.let(Look::accent) ?: Look.DEFAULT_ACCENT } },
            { runCatching { openApp(app.pkg) }.onFailure { toast(it.message ?: "App unavailable.") } }, { appMenu(app, folder) })
    }
    private fun actionTile(key: String, name: String, icon: String, onClick: () -> Unit) =
        tile(key, name, iconPlate(Glyph(icon, if (dark) Color.WHITE else 0xFF1C1C1E.toInt())), true, { Look.DEFAULT_ACCENT }, onClick, onClick)
    private fun folderTile(folder: Folder, apps: Map<String, App>): View {
        val art = GridLayout(this).apply {
            columnCount = 3; rowCount = 2; setPadding(dp(8), dp(6), dp(8), dp(6))
            background = if (dark) GradientDrawable().apply { setColor(0x59FFFFFF) } else GradientDrawable().apply { setColor(0x8CFFFFFF.toInt()) }
            val size = minOf((tileW - dp(16)) / 3, (tileH - dp(12)) / 2) - dp(6)
            folder.apps.take(6).mapNotNull { apps[it] }.forEach { app ->
                addView(ImageView(this@MainActivity).apply { setImageDrawable(runCatching { app.info.loadIcon(packageManager) }.getOrNull()) },
                    GridLayout.LayoutParams().apply { width = size; height = size; setMargins(dp(3), dp(3), dp(3), dp(3)); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
            }
        }
        return tile("folder:${folder.name}", folder.name, art, true, { Look.DEFAULT_ACCENT }, { openFolder(folder.name) }, { folderMenu(folder) })
    }
    // endregion

    // region Overlays
    private fun open(kind: String, panel: View, params: FrameLayout.LayoutParams) {
        if (overlayKind == null) returnFocus = currentFocus
        overlayKind = kind
        overlay.removeAllViews(); overlay.addView(panel, params); overlay.visibility = View.VISIBLE
        page.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        blur(true)
        panel.post { (panel as? ViewGroup)?.getFocusables(View.FOCUS_DOWN)?.firstOrNull()?.requestFocus() }
    }
    private fun close() {
        if (overlayKind == null) return
        overlayKind = null; handler.removeCallbacks(ram)
        overlay.removeAllViews(); overlay.visibility = View.GONE
        page.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        blur(false)
        (returnFocus?.takeIf { it.isAttachedToWindow } ?: firstTile())?.requestFocus(); returnFocus = null
    }
    private fun blur(on: Boolean) {
        val radius = store.blur * dp(2).toFloat()
        val native = Build.VERSION.SDK_INT >= 31 && radius > 0
        if (Build.VERSION.SDK_INT >= 31) {
            val effect = if (on && native) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null
            backdrop.setRenderEffect(effect); page.setRenderEffect(effect)
        }
        // Older TVs cannot blur cheaply, so they get a deeper scrim instead.
        overlay.setBackgroundColor(if (!on) Color.TRANSPARENT else if (native) 0x40000000 else 0xA6000000.toInt())
    }

    private fun controlCenter() {
        val u = dp(62); val g = dp(10)
        fun ccTile(key: String, icon: String, title: String, subtitle: String?, w: Int, h: Int, big: Boolean = false, action: () -> Unit): View {
            val drawn = Glyph(icon, Color.WHITE)
            val box = LinearLayout(this).apply {
                orientation = if (big) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = if (big) Gravity.CENTER else Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true; tag = key; background = Look.control(liquid, dp(if (big) 26 else 20).toFloat(), dp(1)); setPadding(dp(14), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(w, h); contentDescription = title
            }
            box.addView(ImageView(this).apply { setImageDrawable(drawn) }, LinearLayout.LayoutParams(dp(if (big) 34 else 22), dp(if (big) 34 else 22)))
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (big) Gravity.CENTER else Gravity.START; setPadding(if (big) 0 else dp(10), if (big) dp(10) else 0, 0, 0) }
            val t = label(title, 13f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }; texts.addView(t)
            val sub = subtitle?.let { label(it, 10f, 0xB3FFFFFF.toInt()).apply { setPadding(0, dp(3), 0, 0) } }; sub?.let(texts::addView)
            box.addView(texts)
            box.setOnFocusChangeListener { _, f -> val c = if (f) 0xFF1C1C1E.toInt() else Color.WHITE; drawn.tint(c); t.setTextColor(c); sub?.setTextColor(if (f) 0xB31C1C1E.toInt() else 0xB3FFFFFF.toInt()) }
            box.setOnClickListener { action() }
            return box
        }
        fun dot(key: String, icon: String, name: String, on: Boolean = false, action: () -> Unit) = FrameLayout(this).apply {
            isFocusable = true; isClickable = true; tag = key; contentDescription = name
            val drawn = Glyph(icon, if (on) 0xFF1C1C1E.toInt() else Color.WHITE)
            background = if (on) GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) } else Look.control(liquid, dp(99).toFloat(), dp(1))
            addView(ImageView(this@MainActivity).apply { setImageDrawable(drawn) }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnFocusChangeListener { _, f -> drawn.tint(if (f || on) 0xFF1C1C1E.toInt() else Color.WHITE); animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(120).start() }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(u, u).apply { marginEnd = g }
        }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = Look.panel(liquid, dp(30).toFloat(), dp(1)); setPadding(dp(16), dp(14), dp(16), dp(16)); clipChildren = false }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, 0, dp(12)) }
        head.addView(label(time.text.toString(), 15f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(avatar(28)); panel.addView(head)
        val net = network(); val bt = bluetoothOn()
        val row1 = LinearLayout(this).apply { clipChildren = false }
        row1.addView(ccTile("cc:sleep", "power", "Screensaver", "Start now", 2 * u + g, 2 * u + g, big = true) { screensaver() }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = g })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ccTile("cc:wifi", net, if (net == "ethernet") "Ethernet" else "Wi-Fi", if (net == "offline") "Not connected" else "Connected", 2 * u + g, u) { system(Settings.ACTION_WIFI_SETTINGS) })
        col.addView(View(this), LinearLayout.LayoutParams(1, g))
        col.addView(ccTile("cc:bt", "bluetooth", "Bluetooth", when (bt) { true -> "On"; false -> "Off"; null -> null }, 2 * u + g, u) { system(Settings.ACTION_BLUETOOTH_SETTINGS) })
        row1.addView(col); panel.addView(row1)
        val row2 = LinearLayout(this).apply { setPadding(0, g, 0, 0) }
        row2.addView(ccTile("cc:inputs", "input", "Inputs", null, 2 * u + g, u) { inputs() }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = g })
        row2.addView(ccTile("cc:settings", "gear", "Settings", null, 2 * u + g, u) { system(Settings.ACTION_SETTINGS) })
        panel.addView(row2)
        val row3 = LinearLayout(this).apply { setPadding(0, g, 0, 0) }
        row3.addView(dot("cc:dark", "moon", "Dark Mode", on = dark) { set("darkMode", if (dark) "off" else "on"); controlCenter(); overlay.findViewWithTag<View>("cc:dark")?.requestFocus() })
        row3.addView(dot("cc:cast", "cast", "Cast settings") { system(Settings.ACTION_CAST_SETTINGS) })
        row3.addView(dot("cc:recents", "recents", "Recent apps") { recents() })
        row3.addView(dot("cc:home", "home", "TV Home settings") { close(); settings() }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = 0 })
        panel.addView(row3)
        open("control", panel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(22), side - dp(16), 0) })
    }

    private var ramText: TextView? = null
    private var ramFill: View? = null
    private val ram = object : Runnable {
        override fun run() {
            val info = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val used = info.totalMem - info.availMem
            ramText?.text = String.format(java.util.Locale.getDefault(), "RAM %.1f GB used of %.1f GB", used / 1e9, info.totalMem / 1e9)
            ramFill?.layoutParams = (ramFill?.layoutParams as? FrameLayout.LayoutParams)?.apply { width = (dp(70) * used / info.totalMem.coerceAtLeast(1)).toInt() }
            ramFill?.requestLayout()
            handler.postDelayed(this, 2000)
        }
    }
    private fun recents() {
        val panel = FrameLayout(this)
        panel.addView(label("Recent Apps", 22f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(side, dp(34), 0, 0) })
        val meter = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = Look.panel(liquid, dp(99).toFloat(), dp(1)); setPadding(dp(14), dp(8), dp(14), dp(8)) }
        ramText = label("", 11f).also { meter.addView(it) }
        val track = FrameLayout(this).apply { background = GradientDrawable().apply { setColor(0x40FFFFFF); cornerRadius = dp(9).toFloat() } }
        ramFill = View(this).apply { background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(9).toFloat() } }
        track.addView(ramFill, FrameLayout.LayoutParams(0, -1)); meter.addView(track, LinearLayout.LayoutParams(dp(70), dp(5)).apply { marginStart = dp(12) })
        panel.addView(meter, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(32), side, 0) })
        val apps = store.apps().associateBy { it.pkg }
        val list = store.recents.mapNotNull { apps[it] }
        val row = LinearLayout(this).apply { clipChildren = false; clipToPadding = false; setPadding(side, dp(20), side, dp(20)) }
        val cardW = (tileW * 2.2f).toInt(); val cardH = cardW * 9 / 16
        val empty = label("Apps you open from TV Home appear here.", 15f, 0xCCFFFFFF.toInt())
        list.forEach { app ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
            val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
            head.addView(ImageView(this).apply { setImageDrawable(runCatching { app.info.loadIcon(packageManager) }.getOrNull()) }, LinearLayout.LayoutParams(dp(20), dp(20)))
            head.addView(label(app.label, 12f).apply { setPadding(dp(8), 0, 0, 0) })
            item.addView(head)
            val card = FrameLayout(this).apply { isFocusable = true; isClickable = true; contentDescription = app.label; addView(art(app).first, FrameLayout.LayoutParams(-1, -1)) }
            round(card, dp(18).toFloat())
            card.setOnFocusChangeListener { _, f -> card.animate().scaleX(if (f) 1.06f else .94f).scaleY(if (f) 1.06f else .94f).translationZ(if (f) dp(18).toFloat() else 0f).setDuration(160).start() }
            card.scaleX = .94f; card.scaleY = .94f
            card.setOnClickListener { close(); runCatching { openApp(app.pkg) }.onFailure { toast(it.message ?: "App unavailable.") } }
            card.setOnKeyListener { _, code, event ->
                if (code != KeyEvent.KEYCODE_DPAD_UP || event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                runCatching { getSystemService(ActivityManager::class.java).killBackgroundProcesses(app.pkg) }
                store.forget(app.pkg)
                val index = row.indexOfChild(item); row.removeView(item)
                (row.getChildAt(index) ?: row.getChildAt(index - 1))?.let { (it as ViewGroup).getChildAt(1).requestFocus() } ?: row.addView(empty)
                toast("Closed ${app.label}"); true
            }
            item.addView(card, LinearLayout.LayoutParams(cardW, cardH))
            row.addView(item, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(26) })
        }
        if (list.isEmpty()) row.addView(empty)
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(row) }
        panel.addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        panel.addView(label(if (list.isEmpty()) "" else "Press ▲ on an app to close it", 11f, 0xB3FFFFFF.toInt()), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(30) })
        open("recents", panel, FrameLayout.LayoutParams(-1, -1))
        handler.removeCallbacks(ram); ram.run()
    }

    private fun openFolder(name: String) {
        val folder = store.folders.find { it.name == name } ?: return
        val apps = store.apps().associateBy { it.pkg }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; background = Look.panel(liquid, dp(30).toFloat(), dp(1)); setPadding(dp(28), dp(22), dp(10), dp(18)); clipChildren = false; clipToPadding = false }
        panel.addView(label(folder.name, 20f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, 0, dp(18), dp(4)) })
        val members = folder.apps.mapNotNull { apps[it] }
        if (members.isEmpty()) panel.addView(label("This folder is empty. Hold OK on an app and choose Add to folder.", 13f, 0xCCFFFFFF.toInt()).apply { setPadding(0, dp(18), dp(18), dp(10)) })
        members.chunked(4).forEach { chunk -> panel.addView(LinearLayout(this).apply { clipChildren = false; clipToPadding = false; setPadding(0, dp(16), 0, 0); chunk.forEach { addView(appTile(it, folder.name), cell()) } }) }
        open("folder", panel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }
    // endregion

    // region Actions and menus
    private fun action(name: String) = when (name) {
        "controlCenter" -> if (overlayKind == "control") close() else controlCenter()
        "recents" -> if (overlayKind == "recents") close() else recents()
        "apps" -> { close(); ((grid.getChildAt(0) as? ViewGroup)?.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.requestFocus(); Unit }
        "settings" -> { close(); settings() }
        else -> Unit
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val mapped = when (keyCode) { KeyEvent.KEYCODE_MENU -> store.menuAction; KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE -> store.infoAction; else -> null }
        if (mapped != null && mapped != "none") { action(mapped); return true }
        return super.onKeyDown(keyCode, event)
    }

    fun openApp(pkg: String) {
        require(store.apps().any { it.pkg == pkg }) { "This app is not installed on the TV." }
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg) ?: error("App cannot be opened.")
        startActivity(intent); store.opened(pkg)
    }
    fun navigate(key: String) {
        val code = when (key) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN; "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "select" -> KeyEvent.KEYCODE_DPAD_CENTER; "back" -> KeyEvent.KEYCODE_BACK
            "home" -> { dialogs.forEach { it.get()?.dismiss() }; close(); page.smoothScrollTo(0, 0); firstTile()?.requestFocus(); return }
            "controlCenter", "recents", "apps" -> { dialogs.forEach { it.get()?.dismiss() }; action(key); return }
            else -> error("Unknown remote key.")
        }
        val target = dialogs.lastOrNull { it.get()?.isShowing == true }?.get()?.window?.decorView ?: window.decorView
        target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun set(key: String, value: Any) { runCatching { store.update(JSONObject().put(key, value)) }.onFailure { toast(it.message ?: "Could not save.") }; render() }
    private fun menu(title: String, items: List<Pair<String, () -> Unit>>) {
        dialog().setTitle(title).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.setNegativeButton("Close", null).create().showTracked()
    }
    private fun choose(title: String, options: List<Pair<String, String>>, current: String, pick: (String) -> Unit) {
        dialog().setTitle(title).setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOfFirst { it.first == current }) { d, i -> d.dismiss(); pick(options[i].first) }
            .setNegativeButton("Close", null).create().showTracked()
    }
    private fun ask(title: String, initial: String, max: Int, done: (String) -> Unit) {
        val input = EditText(this).apply { setText(initial); setSelection(initial.length); filters = arrayOf(InputFilter.LengthFilter(max)); isSingleLine = true }
        val frame = FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        dialog().setTitle(title).setView(frame).setPositiveButton("Save") { _, _ -> done(input.text.toString().trim()) }.setNegativeButton("Cancel", null).create().showTracked()
    }

    private fun saveFolders(folders: List<Folder>) = set("folders", JSONArray().apply { folders.forEach { put(JSONObject().put("name", it.name).put("apps", JSONArray(it.apps))) } })
    private fun appMenu(app: App, folder: String?) {
        val favorites = store.favorites; val index = favorites.indexOf(app.pkg); val folders = store.folders
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (index >= 0) {
            items += "Remove from favourites" to { set("favorites", JSONArray(favorites - app.pkg)) }
            if (index > 0) items += "Move left" to { set("favorites", JSONArray(favorites.toMutableList().apply { add(index - 1, removeAt(index)) })) }
            if (index < favorites.lastIndex) items += "Move right" to { set("favorites", JSONArray(favorites.toMutableList().apply { add(index + 1, removeAt(index)) })) }
        } else items += "Add to favourites" to { if (favorites.size >= 12) toast("Favourites hold up to 12 apps.") else set("favorites", JSONArray(favorites + app.pkg)) }
        val owner = folders.find { app.pkg in it.apps }
        if (owner != null) items += "Remove from ${owner.name}" to { saveFolders(folders.map { if (it === owner) Folder(it.name, it.apps - app.pkg) else it }); if (folder != null) openFolder(folder) }
        else items += "Add to folder…" to { folderPicker(app) }
        items += "Hide from home" to { set("hidden", JSONArray(store.hidden + app.pkg)); if (folder != null) close() }
        items += "App info" to { system(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)) }
        items += "Uninstall" to { system(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null)) }
        menu(app.label, items)
    }
    private fun folderPicker(app: App) {
        val folders = store.folders
        fun add(name: String) {
            if (name.isBlank()) return
            val next = if (folders.any { it.name == name }) folders.map { if (it.name == name) Folder(it.name, it.apps + app.pkg) else it }
                else if (folders.size >= 12) { toast("You can have up to 12 folders."); return } else folders + Folder(name, listOf(app.pkg))
            saveFolders(next)
        }
        val items = folders.map { f -> f.name to { add(f.name) } } + ("New folder…" to { ask("Folder name", "Folder ${folders.size + 1}", 24, ::add) })
        menu("Add ${app.label} to", items)
    }
    private fun folderMenu(folder: Folder) = menu(folder.name, listOf(
        "Open" to { openFolder(folder.name) },
        "Rename" to { ask("Folder name", folder.name, 24) { name -> if (name.isNotBlank() && store.folders.none { it.name == name }) saveFolders(store.folders.map { if (it.name == folder.name) Folder(name, it.apps) else it }) } },
        "Delete folder (keeps the apps)" to { saveFolders(store.folders.filter { it.name != folder.name }) },
    ))
    private fun favoritesPicker() = pickApps("Favourites (up to 12)", store.favorites, 12) { set("favorites", JSONArray(it)) }
    private fun pickApps(title: String, current: List<String>, max: Int, save: (List<String>) -> Unit) {
        val apps = store.apps(); val selected = current.toMutableList()
        if (apps.isEmpty()) { toast("Install TV apps from your TV's app store, then return here."); return }
        dialog().setTitle(title)
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), apps.map { it.pkg in selected }.toBooleanArray()) { d, i, checked ->
                val pkg = apps[i].pkg
                if (checked && selected.size >= max) { (d as AlertDialog).listView.setItemChecked(i, false); toast("Choose up to $max apps.") }
                else if (checked) selected.add(pkg) else selected.remove(pkg)
            }.setPositiveButton("Save") { _, _ -> save(selected) }.setNegativeButton("Cancel", null).create().showTracked()
    }

    private fun settings() {
        val names = mapOf("liquid" to "Liquid Glass", "frosted" to "Frosted Glass", "off" to "Off", "on" to "On", "sunset" to "Sunset to sunrise", "custom" to "Custom schedule",
            "minimal" to "Minimal", "full" to "Full status bar", "weather" to "Weather", "hidden" to "Hidden", "banner" to "TV banners", "icon" to "Icons",
            "controlCenter" to "Control Center", "recents" to "Recent apps", "apps" to "All apps", "settings" to "TV Home settings", "none" to "Do nothing")
        menu("TV Home settings", listOf(
            "Background" to ::backgrounds,
            "Glass style  ·  ${names[store.glass]}" to { choose("Glass style", listOf("liquid", "frosted").map { it to names[it]!! }, store.glass) { set("glass", it) } },
            "Overall blur  ·  ${if (store.blur == 0) "Off" else store.blur}" to { choose("Blur of menus and Control Center. Higher blur can affect performance.", (0..25 step 5).map { "$it" to if (it == 0) "Off" else "$it" }, "${store.blur}") { set("blur", it.toInt()) } },
            "Dark Mode  ·  ${names[store.darkMode]}" to ::darkSettings,
            "Status pill  ·  ${names[store.pill]}" to { choose("Status pill", listOf("minimal", "full", "weather", "hidden").map { it to names[it]!! }, store.pill) { set("pill", it) } },
            "App tiles  ·  ${names[store.iconStyle]}" to { choose("App tiles", listOf("banner", "icon").map { it to names[it]!! }, store.iconStyle) { set("iconStyle", it) } },
            "Weather  ·  ${store.weatherCity.ifBlank { "Off" }}" to ::weatherSettings,
            "Favourites" to ::favoritesPicker,
            "Hidden apps" to { pickApps("Hidden apps", store.hidden, 300) { set("hidden", JSONArray(it)) } },
            "Remote buttons" to { menu("Remote buttons", listOf("menu" to "Menu button  ·  ${names[store.menuAction]}", "info" to "Info or Guide button  ·  ${names[store.infoAction]}").map { (key, text) ->
                text to { choose(text.substringBefore("  ·"), HomeStore.ACTIONS.map { it to names[it]!! }, if (key == "menu") store.menuAction else store.infoAction) { set(if (key == "menu") "menuAction" else "infoAction", it) } } }) },
            (if (store.clock24h) "Clock  ·  24-hour" else "Clock  ·  12-hour") to { set("clock24h", !store.clock24h) },
            "Room name  ·  ${store.roomName}" to { ask("Room name", store.roomName, 40) { if (it.isNotBlank()) set("roomName", it) } },
            "Phone control" to ::phoneMenu,
            "Default home screen" to { system(Settings.ACTION_HOME_SETTINGS) },
            "Screensaver settings" to { system(Settings.ACTION_DREAM_SETTINGS) },
            "About" to ::about,
        ))
    }
    private fun backgrounds() {
        val ids = listOf("adaptive", "dusk", "aurora", "sand", "ocean") + if (store.photoFile.exists()) listOf("photo") else emptyList()
        val names = mapOf("adaptive" to "Adaptive (follows the focused app)", "dusk" to "Quiet dusk", "aurora" to "Northern light", "sand" to "Desert calm", "ocean" to "Open water", "photo" to "Your photo")
        menu("Background", listOf(
            "Scene  ·  ${names[store.scene]}" to { choose("Scene", ids.map { it to names[it]!! }, store.scene) { set("scene", it) } },
            "Dimming  ·  ${(store.dim * 100).toInt()}%" to { choose("Dimming", listOf(0f, .1f, .2f, .3f, .45f, .6f).map { "$it" to "${(it * 100).toInt()}%" }, "${store.dim}") { set("dim", it.toDouble()) } },
        ))
    }
    private fun darkSettings() {
        val modes = listOf("off" to "Off", "on" to "On", "sunset" to "Sunset to sunrise (uses the weather city)", "custom" to "Custom schedule")
        choose("Dark Mode", modes, store.darkMode) { mode ->
            set("darkMode", mode)
            if (mode == "sunset" && store.weatherCity.isBlank()) toast("Set a weather city for local sunset times. Until then 19:00 to 07:00 is used.")
            if (mode == "custom") {
                val times = (0 until 48).map { "${it * 30}" to String.format(java.util.Locale.ROOT, "%02d:%02d", it / 2, it % 2 * 30) }
                choose("Dark from", times, "${store.darkStart}") { start -> set("darkStart", start.toInt()); choose("Light from", times, "${store.darkEnd}") { set("darkEnd", it.toInt()) } }
            }
        }
    }
    private fun weatherSettings() = menu("Weather", listOf(
        "City  ·  ${store.weatherCity.ifBlank { "Not set" }}" to { ask("City for weather and sunset", store.weatherCity, 60) { set("weatherCity", it); weatherAttempt = 0; refreshWeather() } },
        "Units  ·  ${if (store.tempUnit == "f") "Fahrenheit" else "Celsius"}" to { set("tempUnit", if (store.tempUnit == "f") "c" else "f"); weatherAttempt = 0; refreshWeather() },
    ))
    private fun refreshWeather() {
        val city = store.weatherCity; val unit = store.tempUnit
        if (!weather.stale(city, unit) || System.currentTimeMillis() - weatherAttempt < 5 * 60_000) return
        weatherAttempt = System.currentTimeMillis()
        runCatching { background.execute {
            val result = runCatching { weather.fetch(city, unit) }
            handler.post { if (!isDestroyed) { if (result.isFailure && city.isNotBlank()) showStatus("Weather unavailable for $city"); renderHeader(); if (overlayKind == null) renderPill() } }
        } }
    }
    private fun phoneMenu() = menu("Phone control", listOf(
        "Pair a phone" to ::pair,
        (if (credentials.enabled) "Pause phone control" else "Resume phone control") to {
            if (credentials.url.isBlank()) toast("Online services are not configured in this build.")
            else { credentials.enabled = !credentials.enabled; if (credentials.enabled) startForegroundService(Intent(this, RemoteService::class.java)) else stopService(Intent(this, RemoteService::class.java)); render() }
        },
        "Disconnect account" to {
            dialog().setTitle("Disconnect account?").setMessage("This account will lose control of the TV. You can pair again afterwards.")
                .setPositiveButton("Disconnect") { _, _ -> credentials.enabled = true; startForegroundService(Intent(this, RemoteService::class.java).setAction("disconnect")) }.setNegativeButton("Cancel", null).create().showTracked()
        },
    ))
    private fun about() {
        val info = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val phone = when { credentials.url.isBlank() -> "Not configured in this build"; credentials.enabled -> "Enabled"; else -> "Paused" }
        dialog().setTitle("TV Home ${BuildConfig.VERSION_NAME}").setMessage(
            "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                String.format(java.util.Locale.getDefault(), "Memory: %.1f GB free of %.1f GB\n", info.availMem / 1e9, info.totalMem / 1e9) +
                "Phone control: $phone\nWeather data: Open-Meteo.com"
        ).setPositiveButton("Close", null).create().showTracked()
    }
    private fun screensaver() {
        close()
        // SystemUI's Somnambulator starts the selected screensaver on stock Android TV; other firmware falls back to its settings.
        runCatching { startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))) }
            .onFailure { system(Settings.ACTION_DREAM_SETTINGS) }
    }
    private fun inputs() {
        val candidates = listOf(Intent("android.settings.TV_INPUT_SETTINGS"), Intent("com.android.tv.action.VIEW_INPUTS"), Intent(Settings.ACTION_SETTINGS))
        close()
        if (candidates.none { runCatching { startActivity(it) }.isSuccess }) toast("Inputs are unavailable on this TV. Use the Input button on your remote.")
    }
    private fun system(action: String, data: Uri? = null) {
        runCatching { startActivity(Intent(action).apply { if (data != null) setData(data) }) }.onFailure { toast("This shortcut is unavailable on this TV. Use your TV's Settings menu.") }
    }
    private fun pair() {
        if (credentials.url.isBlank()) {
            dialog().setTitle("Phone control unavailable").setMessage("Online services are not configured in this build. You can still use the launcher and all appearance settings.").setPositiveButton("Close", null).create().showTracked()
            return
        }
        credentials.enabled = true
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, RemoteService::class.java).setAction("pair")); showStatus("Connecting to the TV Home service…")
    }
    private fun decision(id: String, approved: Boolean) { startForegroundService(Intent(this, RemoteService::class.java).setAction("decision").putExtra("id", id).putExtra("approved", approved)) }
    private val hideStatus = Runnable { status.animate().alpha(0f).setDuration(300).start() }
    private fun showStatus(message: String) { status.text = message; status.animate().alpha(1f).setDuration(200).start(); handler.removeCallbacks(hideStatus); handler.postDelayed(hideStatus, 6000) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    // endregion

    companion object {
        @Volatile private var activeRef: java.lang.ref.WeakReference<MainActivity>? = null
        var active: MainActivity?
            get() = activeRef?.get()
            private set(value) { activeRef = value?.let { java.lang.ref.WeakReference(it) } }
    }
}
