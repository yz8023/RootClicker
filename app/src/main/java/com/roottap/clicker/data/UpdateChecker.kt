package com.roottap.clicker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Available application update. */
data class AvailableUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
)

/** Result of an update check. */
sealed interface UpdateResult {
    data class Available(val update: AvailableUpdate) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

/** Source of update information. */
fun interface UpdateChecker {
    suspend fun check(currentVersion: String): UpdateResult
}

/** Reads the latest release of a GitHub repository. */
class GithubReleaseChecker(private val repository: String) : UpdateChecker {
    override suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("https://api.github.com/repos/$repository/releases/latest")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = TimeoutMillis
            connection.readTimeout = TimeoutMillis
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (connection.responseCode) {
                    // GitHub answers 404 for a repository with no release yet.
                    // That is not a failure worth showing to the user.
                    HttpURLConnection.HTTP_NOT_FOUND -> UpdateResult.UpToDate
                    HttpURLConnection.HTTP_OK -> parse(
                        connection.inputStream.bufferedReader().use { it.readText() },
                        currentVersion,
                    )
                    else -> UpdateResult.Failed
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(UpdateResult.Failed)
    }

    private fun parse(body: String, currentVersion: String): UpdateResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val version = root.text("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion)) return UpdateResult.UpToDate
        val htmlUrl = root.text("html_url")
        val apk = root["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it.text("name").endsWith(".apk") }
        return UpdateResult.Available(
            AvailableUpdate(
                versionName = version,
                releaseNotes = root.text("body"),
                downloadUrl = apk?.text("browser_download_url") ?: htmlUrl,
                htmlUrl = htmlUrl,
            ),
        )
    }

    private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    companion object {
        private const val TimeoutMillis = 8_000

        /**
         * Compares dotted version strings numerically.
         * Suffixes are not understood: "0-beta" reads as 0, so 1.2.0-beta equals 1.2.0.
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val left = candidate.split(".").map { it.toIntOrNull() ?: 0 }
            val right = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (index in 0 until maxOf(left.size, right.size)) {
                val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
                if (difference != 0) return difference > 0
            }
            return false
        }
    }
}
