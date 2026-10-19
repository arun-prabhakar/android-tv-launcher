package me.prince.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

private const val TAG = "Wallpaper"
private const val WALLHAVEN_API = "https://wallhaven.cc/api/v1/search"
// Every batch mixes all themes; add or edit to taste
private val WALLPAPER_THEMES = listOf("movie", "nature", "scenic", "mountain")
private const val WALLPAPERS_PER_BATCH = 24
// Some CDNs reject empty/default Java user agents; identify the launcher honestly
private val WALLHAVEN_HEADERS = mapOf("User-Agent" to "android-tv-launcher/1.0 (github.com/arun-prabhakar/android-tv-launcher)")

data class TrendingTitle(
    val title: String,
    val meta: String,
    val overview: String,
    // Kept compressed (~200KB-1MB); decoded on demand — a decoded 4K bitmap costs 33MB
    val backdropJpeg: ByteArray,
    // Blurred copy for glass surfaces drawn over it
    val frosted: ImageBitmap,
    val logo: ImageBitmap?,
    val action: android.content.Intent?,
    val actionLabel: String?,
)

// A mixed batch: six random wallpapers from each theme, round-robinned so consecutive
// rotations change character. Empty on failure; the caller keeps whatever is on screen.
fun fetchWallpapers(): List<TrendingTitle> {
    val perTheme = WALLPAPERS_PER_BATCH / WALLPAPER_THEMES.size
    val search = "$WALLHAVEN_API?q=%s&categories=100&purity=100&atleast=1920x1080&ratios=16x9&sorting=random"
    val batches = WALLPAPER_THEMES.map { theme ->
        try {
            JSONObject(httpGet(search.format(URLEncoder.encode(theme, "UTF-8")), WALLHAVEN_HEADERS))
                .getJSONArray("data").objects()
                .take(perTheme)
                .mapNotNull { toWallpaper(it.getString("path")) }
        } catch (e: IOException) {
            Log.w(TAG, "Fetching '$theme' wallpapers failed", e)
            emptyList()
        } catch (e: JSONException) {
            Log.w(TAG, "Unexpected Wallhaven response for '$theme'", e)
            emptyList()
        }
    }
    return (0 until perTheme).flatMap { i -> batches.mapNotNull { it.getOrNull(i) } }
}

private fun toWallpaper(url: String): TrendingTitle? = try {
    val jpeg = httpBytes(url, WALLHAVEN_HEADERS)
    TrendingTitle(
        title = "",
        meta = "",
        overview = "",
        backdropJpeg = jpeg,
        frosted = frostedBackdrop(decode(jpeg), BACKDROP_HEIGHT_FRACTION),
        logo = null,
        action = null,
        actionLabel = null,
    )
} catch (e: IOException) {
    // One undecodable image shouldn't drop the whole batch
    Log.w(TAG, "Skipping wallpaper", e)
    null
}

fun TrendingTitle.decodeBackdrop(): ImageBitmap = decode(backdropJpeg).asImageBitmap()

// Wallhaven serves up to 4K; a 3840px bitmap costs 33MB decoded and the launcher
// keeps three decoded at a time, so sample down to screen width before decoding.
// Internal: WallpaperCache.kt decodes cached batches through the same guard.
internal fun decode(bytes: ByteArray, maxWidth: Int = 1920): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Undecodable image")
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
