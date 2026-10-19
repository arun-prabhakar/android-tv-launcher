package me.prince.launcher

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

private const val TAG = "WallpaperCache"
private const val DIR = "wallpapers"
private const val INDEX_FILE = "batch.json"
private const val PREFS = "wallpapers"
private const val PREF_SHOWN_INDEX = "shownIndex"

// A cold start (TV reboot, process kill) would render a blank backdrop for the ~40s a
// fresh batch needs to download. The last batch is cached here and painted instantly
// at startup while the fresh one downloads behind it.
data class SavedWallpapers(val titles: List<TrendingTitle>, val shownIndex: Int)

fun saveWallpaperBatch(context: Context, titles: List<TrendingTitle>) {
    try {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val entries = JSONArray()
        titles.forEachIndexed { i, title ->
            val file = File(dir, "w%02d.jpg".format(i))
            file.writeBytes(title.backdropJpeg)
            // Watch actions relaunch by package name (the app may be gone by restore
            // time); TMDB logos are skipped — the restored title renders as text
            entries.put(
                JSONObject()
                    .put("file", file.name)
                    .put("title", title.title)
                    .put("meta", title.meta)
                    .put("overview", title.overview)
                    .put("actionPackage", title.action?.component?.packageName ?: "")
                    .put("actionLabel", title.actionLabel ?: "")
            )
        }
        File(dir, INDEX_FILE).writeText(entries.toString())
        // listFiles order is unspecified: match by name so only unreferenced leftovers go
        val keep = titles.indices.map { "w%02d.jpg".format(it) }.toSet()
        dir.listFiles { f -> f.name.endsWith(".jpg") }?.filter { it.name !in keep }?.forEach { it.delete() }
    } catch (e: IOException) {
        Log.w(TAG, "Saving batch failed", e)
    }
}

// Written on every rotation/press (cheap prefs write, unlike the batch files)
fun saveWallpaperIndex(context: Context, index: Int) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(PREF_SHOWN_INDEX, index).apply()
}

fun loadWallpaperBatch(context: Context): SavedWallpapers {
    val dir = File(context.cacheDir, DIR)
    val index = File(dir, INDEX_FILE)
    if (!index.exists()) return SavedWallpapers(emptyList(), 0)
    return try {
        val entries = JSONArray(index.readText())
        val pm = context.packageManager
        val titles = (0 until entries.length()).mapNotNull { i ->
            val entry = entries.getJSONObject(i)
            val file = File(dir, entry.optString("file"))
            if (!file.exists()) return@mapNotNull null
            try {
                val jpeg = file.readBytes()
                val actionPackage = entry.optString("actionPackage")
                // Only rebuild the watch action if the app is still installed;
                // a stale label without a working action would be a dead button
                val action = actionPackage.takeIf { it.isNotBlank() }
                    ?.let { pm.getLeanbackLaunchIntentForPackage(it) }
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                TrendingTitle(
                    title = entry.optString("title"),
                    meta = entry.optString("meta"),
                    overview = entry.optString("overview"),
                    backdropJpeg = jpeg,
                    frosted = frostedBackdrop(decode(jpeg), BACKDROP_HEIGHT_FRACTION),
                    logo = null,
                    action = action,
                    actionLabel = if (action != null) entry.optString("actionLabel").ifBlank { null } else null,
                )
            } catch (e: IOException) {
                Log.w(TAG, "Skipping cached ${entry.optString("title")}", e)
                null
            }
        }
        val shown = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(PREF_SHOWN_INDEX, 0)
        SavedWallpapers(titles, shown)
    } catch (e: IOException) {
        Log.w(TAG, "Loading batch failed", e)
        SavedWallpapers(emptyList(), 0)
    } catch (e: JSONException) {
        Log.w(TAG, "Corrupt cache index", e)
        SavedWallpapers(emptyList(), 0)
    }
}
