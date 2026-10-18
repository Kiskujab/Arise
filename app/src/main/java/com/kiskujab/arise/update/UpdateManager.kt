package com.kiskujab.arise.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kiskujab.arise.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateState
}

/**
 * Checks this repository's GitHub Releases for a newer build, downloads the APK and hands it to
 * the system installer. Builds are signed with the same key, so the update keeps all data.
 */
class UpdateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("arise_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val busy: Boolean
        get() = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Ready }

    /** Asks GitHub for the latest release. Returns it when newer than this build. */
    suspend fun check(showProgress: Boolean = false): UpdateInfo? {
        if (busy) return null
        if (showProgress) _state.value = UpdateState.Checking
        val info = try {
            fetchLatest()
        } catch (e: Exception) {
            _state.value = if (showProgress) UpdateState.Failed("Could not reach GitHub.", null) else UpdateState.Idle
            return null
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        return if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
            _state.value = UpdateState.Available(info)
            info
        } else {
            _state.value = UpdateState.UpToDate
            null
        }
    }

    /** Background check, at most every few hours. Returns a newer release not yet announced. */
    suspend fun checkInBackground(): UpdateInfo? {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < BACKGROUND_INTERVAL_MS) return null
        val info = check() ?: return null
        if (prefs.getInt(KEY_NOTIFIED, 0) >= info.versionCode) return null
        prefs.edit().putInt(KEY_NOTIFIED, info.versionCode).apply()
        return info
    }

    suspend fun download(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Downloading(info, 0f)
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "arise-${info.versionCode}.apk")
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            conn.disconnect()
            _state.value = UpdateState.Ready(info, file)
            file
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Download failed. Check your connection and try again.", info)
            null
        }
    }

    /** False when the player first has to allow Arise to install apps (settings are opened). */
    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val body = try {
            if (conn.responseCode != 200) return@withContext null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        parseRelease(Json.parseToJsonElement(body).jsonObject)
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/Kiskujab/Arise/releases/latest"
        private const val USER_AGENT = "Arise-Android"
        private const val KEY_LAST_CHECK = "lastCheck"
        private const val KEY_NOTIFIED = "notifiedVersion"
        private const val BACKGROUND_INTERVAL_MS = 6 * 60 * 60 * 1000L

        fun parseRelease(release: JsonObject): UpdateInfo? {
            val tag = release["tag_name"]?.jsonPrimitive?.content ?: return null
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val asset = release["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return null
            val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
            return UpdateInfo(
                versionCode = code,
                versionName = tag.removePrefix("v"),
                apkUrl = url,
                notes = cleanNotes(release["body"]?.jsonPrimitive?.content.orEmpty()),
                sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }

        /** Keeps the human part of the release body (the commit message). */
        fun cleanNotes(body: String): String = body
            .substringBefore("\n---")
            .lines()
            .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") || it.startsWith("Built from") || it.startsWith("Download the APK") }
            .joinToString("\n")
            .trim()
    }
}
