package app.tvhome.launcher

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cached current weather from Open-Meteo, which needs no account or key. The city comes from the TV or phone settings. */
class Weather(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    val city get() = prefs.getString("city", "")!!
    val place get() = prefs.getString("place", "")!!
    val temp get() = prefs.getInt("temp", 0)
    val code get() = prefs.getInt("code", 0)
    val isDay get() = prefs.getBoolean("isDay", true)
    val sunrise get() = prefs.getInt("sunrise", 7 * 60)
    val sunset get() = prefs.getInt("sunset", 19 * 60)
    private val fetchedAt get() = prefs.getLong("fetchedAt", 0)
    private val unit get() = prefs.getString("unit", "c")!!

    fun stale(city: String, unit: String) = city.isNotBlank() && (city != this.city || unit != this.unit || System.currentTimeMillis() - fetchedAt > 30 * 60_000)

    /** Blocking network call; run it off the main thread. */
    fun fetch(city: String, unit: String) {
        val geo = get("https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder().addQueryParameter("name", city).addQueryParameter("count", "1").build().toString())
        val hit = geo.optJSONArray("results")?.optJSONObject(0) ?: error("City not found.")
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", hit.getDouble("latitude").toString()).addQueryParameter("longitude", hit.getDouble("longitude").toString())
            .addQueryParameter("current", "temperature_2m,weather_code,is_day").addQueryParameter("daily", "sunrise,sunset")
            .addQueryParameter("timezone", "auto").addQueryParameter("forecast_days", "1")
            .addQueryParameter("temperature_unit", if (unit == "f") "fahrenheit" else "celsius").build()
        val json = get(url.toString())
        val current = json.getJSONObject("current"); val daily = json.getJSONObject("daily")
        fun minutes(iso: String) = iso.substringAfter('T').split(':').let { it[0].toInt() * 60 + it[1].toInt() }
        prefs.edit().putString("city", city).putString("unit", unit).putString("place", hit.optString("name", city).take(40))
            .putInt("temp", Math.round(current.getDouble("temperature_2m")).toInt()).putInt("code", current.getInt("weather_code")).putBoolean("isDay", current.optInt("is_day", 1) == 1)
            .putInt("sunrise", minutes(daily.getJSONArray("sunrise").getString(0))).putInt("sunset", minutes(daily.getJSONArray("sunset").getString(0)))
            .putLong("fetchedAt", System.currentTimeMillis()).apply()
    }

    private fun get(url: String): JSONObject = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "Weather unavailable." }
        JSONObject(response.body!!.string())
    }

    /** WMO weather interpretation codes grouped into the glyphs the launcher draws. */
    val glyph get() = when (code) { 0, 1 -> if (isDay) "sun" else "moon"; 2, 3, 45, 48 -> "cloud"; in 71..77, 85, 86 -> "snow"; in 95..99 -> "storm"; else -> "rain" }
    val summary get() = when (code) { 0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Fog"; in 51..67, in 80..82 -> "Rain"; in 71..77, 85, 86 -> "Snow"; in 95..99 -> "Thunderstorm"; else -> "" }

    companion object { private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build() }
}
