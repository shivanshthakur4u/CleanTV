package app.tvhome.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar

class App(val pkg: String, val label: String, val leanback: Boolean, val info: ActivityInfo)
class Folder(val name: String, val apps: List<String>)

class HomeStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    val scene get() = prefs.getString("scene", "adaptive")!!
    val dim get() = prefs.getFloat("dim", .2f)
    val roomName get() = prefs.getString("roomName", "My TV")!!
    val clock24h get() = prefs.getBoolean("clock24h", false)
    val glass get() = prefs.getString("glass", "liquid")!!
    val blur get() = prefs.getInt("blur", 10)
    val darkMode get() = prefs.getString("darkMode", "off")!!
    val darkStart get() = prefs.getInt("darkStart", 19 * 60)
    val darkEnd get() = prefs.getInt("darkEnd", 7 * 60)
    val pill get() = prefs.getString("pill", "full")!!
    val iconStyle get() = prefs.getString("iconStyle", "banner")!!
    val weatherCity get() = prefs.getString("weatherCity", "")!!
    val tempUnit get() = prefs.getString("tempUnit", "c")!!
    val menuAction get() = prefs.getString("menuAction", "controlCenter")!!
    val infoAction get() = prefs.getString("infoAction", "recents")!!
    val favorites get() = strings("favorites").filter { it in installed() }
    val hidden get() = strings("hidden")
    val recents get() = strings("recents").filter { it in installed() }
    val folders get() = JSONArray(prefs.getString("folders", "[]")).let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.map { f -> Folder(f.getString("name"), f.getJSONArray("apps").let { x -> (0 until x.length()).map { x.getString(it) } }.filter { it in installed() }) }
    }
    val photoFile get() = File(context.filesDir, "wallpaper.jpg")
    private fun strings(key: String) = JSONArray(prefs.getString(key, "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }

    /** Dark appearance for the current minute, honoring the schedule and the cached sunset when one is known. */
    fun darkNow(): Boolean {
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val (start, end) = when (darkMode) {
            "on" -> return true
            "off" -> return false
            "sunset" -> Weather(context).let { it.sunset to it.sunrise }
            else -> darkStart to darkEnd
        }
        return if (start <= end) now in start until end else now >= start || now < end
    }

    fun apps(): List<App> {
        cache?.takeIf { SystemClock.elapsedRealtime() - cachedAt < 5000 }?.let { return it }
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return (leanback.map { it to true } + phone.map { it to false })
            .filter { it.first.activityInfo.packageName != context.packageName }
            .distinctBy { it.first.activityInfo.packageName }
            .map { (info, tv) -> App(info.activityInfo.packageName, info.loadLabel(pm).toString().take(160), tv, info.activityInfo) }
            .sortedBy { it.label.lowercase() }.take(300)
            .also { cache = it; cachedAt = SystemClock.elapsedRealtime() }
    }
    private fun installed() = apps().mapTo(HashSet()) { it.pkg }
    fun invalidate() { cache = null }

    @Synchronized fun update(settings: JSONObject) {
        val edit = prefs.edit()
        fun choice(key: String, values: List<String>) { if (settings.has(key)) { val v = settings.getString(key); require(v in values) { "Unknown $key." }; edit.putString(key, v) } }
        fun minutes(key: String) { if (settings.has(key)) { val v = settings.getInt(key); require(v in 0..1439); edit.putInt(key, v) } }
        // Uninstalled apps are dropped rather than rejected, so a stale phone list still saves.
        fun packages(key: String, max: Int) = settings.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }.also { require(it.size <= max && it.distinct().size == it.size) }.filter { it in installed() }
        if (settings.has("scene")) {
            val value = settings.getString("scene")
            require(value in SCENES) { "Unknown scene." }
            require(value != "photo" || photoFile.exists()) { "Upload a photo first." }
            edit.putString("scene", value)
        }
        if (settings.has("dim")) { val value = settings.getDouble("dim"); require(value.isFinite() && value in 0.0..0.8); edit.putFloat("dim", value.toFloat()) }
        if (settings.has("roomName")) { val value = settings.getString("roomName").trim(); require(value.length in 1..40); edit.putString("roomName", value) }
        if (settings.has("clock24h")) edit.putBoolean("clock24h", settings.getBoolean("clock24h"))
        if (settings.has("blur")) { val value = settings.getInt("blur"); require(value in 0..25); edit.putInt("blur", value) }
        choice("glass", listOf("liquid", "frosted")); choice("darkMode", listOf("off", "on", "sunset", "custom"))
        choice("pill", listOf("minimal", "full", "weather", "hidden")); choice("iconStyle", listOf("banner", "icon"))
        choice("tempUnit", listOf("c", "f")); choice("menuAction", ACTIONS); choice("infoAction", ACTIONS)
        minutes("darkStart"); minutes("darkEnd")
        if (settings.has("weatherCity")) { val value = settings.getString("weatherCity").trim(); require(value.length <= 60); edit.putString("weatherCity", value) }
        if (settings.has("favorites")) edit.putString("favorites", JSONArray(packages("favorites", 12)).toString())
        if (settings.has("hidden")) edit.putString("hidden", JSONArray(packages("hidden", 300)).toString())
        if (settings.has("folders")) {
            val array = settings.getJSONArray("folders"); require(array.length() <= 12)
            val seen = HashSet<String>()
            val clean = JSONArray()
            for (i in 0 until array.length()) {
                val f = array.getJSONObject(i); val name = f.getString("name").trim(); require(name.length in 1..24) { "Folder names need 1 to 24 characters." }
                val apps = f.getJSONArray("apps").let { a -> (0 until a.length()).map { a.getString(it) } }
                require(apps.size <= 24) { "A folder holds up to 24 apps." }
                clean.put(JSONObject().put("name", name).put("apps", JSONArray(apps.filter { it in installed() && seen.add(it) })))
            }
            edit.putString("folders", clean.toString())
        }
        check(edit.commit()) { "TV could not save the settings." }
    }

    /** First run: start the favourites shelf with familiar streaming apps, then any other TV apps. */
    @Synchronized fun seedFavorites() {
        if (prefs.contains("favorites")) return
        val apps = apps().filter { it.leanback }.map { it.pkg }
        if (apps.isEmpty()) return
        val known = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "in.startv.hotstar", "com.jio.jioplay.tv", "com.apple.atve.androidtv.appletv", "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.android.vending")
        prefs.edit().putString("favorites", JSONArray((known.filter { it in apps } + apps).distinct().take(6)).toString()).apply()
    }

    @Synchronized fun opened(pkg: String) {
        prefs.edit().putString("recents", JSONArray((listOf(pkg) + strings("recents").filter { it != pkg }).take(12)).toString()).apply()
    }
    @Synchronized fun forget(pkg: String) { prefs.edit().putString("recents", JSONArray(strings("recents").filter { it != pkg }).toString()).apply() }

    @Synchronized fun upload(encoded: String) {
        require(encoded.length <= 2_800_000) { "Photo is too large. Choose a smaller image." }
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        require(data.size <= 2_100_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000 && bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPEG, PNG, or WebP photo under 40 megapixels." }
        val options = BitmapFactory.Options()
        while (bounds.outWidth / options.inSampleSize > 1920 || bounds.outHeight / options.inSampleSize > 1080) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: error("Photo could not be opened.")
        val temp = File(context.filesDir, "wallpaper.pending")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            check(temp.renameTo(photoFile)) { "TV could not save the photo." }
            check(prefs.edit().putString("scene", "photo").commit())
        } finally { bitmap.recycle(); temp.delete() }
    }

    @Synchronized fun snapshot(): JSONObject = JSONObject().apply {
        put("roomName", roomName); put("scene", scene); put("dim", kotlin.math.round(dim * 1000.0) / 1000.0); put("clock24h", clock24h)
        put("glass", glass); put("blur", blur); put("darkMode", darkMode); put("darkStart", darkStart); put("darkEnd", darkEnd); put("darkActive", darkNow())
        put("pill", pill); put("iconStyle", iconStyle); put("weatherCity", weatherCity); put("tempUnit", tempUnit); put("menuAction", menuAction); put("infoAction", infoAction)
        put("favorites", JSONArray(favorites)); put("hidden", JSONArray(hidden.filter { it in installed() })); put("recents", JSONArray(recents))
        put("folders", JSONArray().apply { folders.forEach { put(JSONObject().put("name", it.name).put("apps", JSONArray(it.apps))) } })
        put("apps", JSONArray().apply { apps().forEach { put(JSONObject().put("package", it.pkg).put("label", it.label)) } })
        put("model", "${Build.MANUFACTURER} ${Build.MODEL}".take(160)); put("sdk", Build.VERSION.SDK_INT); put("version", BuildConfig.VERSION_NAME)
        put("homeVisible", MainActivity.active != null)
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        put("ram", JSONObject().put("total", memory.totalMem).put("available", memory.availMem))
        Weather(context).takeIf { it.city.isNotBlank() && it.city == weatherCity }?.let { put("weather", JSONObject().put("temp", it.temp).put("code", it.code).put("place", it.place)) }
        if (scene == "photo" && photoFile.exists()) {
            val image = BitmapFactory.decodeFile(photoFile.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            if (image != null) {
                val bytes = ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG, 55, bytes); image.recycle()
                val preview = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                if (preview.length < 160000) put("wallpaperPreview", preview)
            }
        }
    }

    companion object {
        val SCENES = listOf("dusk", "aurora", "sand", "ocean", "adaptive", "photo")
        val ACTIONS = listOf("controlCenter", "recents", "apps", "settings", "none")
        // Shared across instances so the service and activity reuse one package scan.
        @Volatile private var cache: List<App>? = null
        @Volatile private var cachedAt = 0L
    }
}
