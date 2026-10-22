package app.tvhome.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class HomeStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    val scene get() = prefs.getString("scene", "dusk")!!
    val dim get() = prefs.getFloat("dim", .2f)
    val roomName get() = prefs.getString("roomName", "My TV")!!
    val clock24h get() = prefs.getBoolean("clock24h", false)
    val favorites get() = JSONArray(prefs.getString("favorites", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
    val photoFile get() = File(context.filesDir, "wallpaper.jpg")

    fun apps(): List<Pair<String, String>> {
        val pm = context.packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { category ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.filter { it.activityInfo.packageName != context.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString().take(160) }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }.take(300)
    }

    @Synchronized fun update(settings: JSONObject) {
        val edit = prefs.edit()
        if (settings.has("scene")) {
            val value = settings.getString("scene")
            require(value in listOf("dusk", "aurora", "sand", "ocean", "photo")) { "Unknown scene." }
            require(value != "photo" || photoFile.exists()) { "Upload a photo first." }
            edit.putString("scene", value)
        }
        if (settings.has("dim")) { val value = settings.getDouble("dim"); require(value.isFinite() && value in 0.0..0.8); edit.putFloat("dim", value.toFloat()) }
        if (settings.has("roomName")) { val value = settings.getString("roomName").trim(); require(value.length in 1..40); edit.putString("roomName", value) }
        if (settings.has("clock24h")) edit.putBoolean("clock24h", settings.getBoolean("clock24h"))
        if (settings.has("favorites")) {
            val array = settings.getJSONArray("favorites")
            require(array.length() <= 8)
            val values = (0 until array.length()).map { array.getString(it) }
            val installed = apps().map { it.first }.toSet()
            require(values.distinct().size == values.size && values.all { it in installed }) { "Choose apps installed on this TV." }
            edit.putString("favorites", array.toString())
        }
        check(edit.commit()) { "TV could not save the settings." }
    }

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
        put("favorites", JSONArray(favorites)); put("apps", JSONArray().apply { apps().forEach { (pkg, label) -> put(JSONObject().put("package", pkg).put("label", label)) } })
        put("model", "${Build.MANUFACTURER} ${Build.MODEL}".take(160)); put("sdk", Build.VERSION.SDK_INT)
        put("homeVisible", MainActivity.active != null)
        if (scene == "photo" && photoFile.exists()) {
            val image = BitmapFactory.decodeFile(photoFile.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            if (image != null) {
                val bytes = ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG, 55, bytes); image.recycle()
                val preview = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                if (preview.length < 160000) put("wallpaperPreview", preview)
            }
        }
    }
}
