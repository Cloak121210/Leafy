package com.example.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class DownloadStatus(
    val modelId: String,
    val progress: Float, // 0.0 .. 1.0
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedMbPerSec: Float,
    val localFilePath: String,
    val isComplete: Boolean,
    val error: String? = null
)

class ModelDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Boolean>()

    val modelsDir: File
        get() {
            val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun isModelFilePresent(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return file.exists() && file.length() > 0
    }

    fun getLocalModelFile(fileName: String): File {
        return File(modelsDir, fileName)
    }

    fun deleteModelFile(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun cancelDownload(modelId: String) {
        activeJobs[modelId] = false
    }

    /**
     * Downloads model weight file from the Internet (e.g. Hugging Face direct resolve link)
     * with live byte-level progress reporting and cancellation support.
     */
    fun downloadModel(
        modelId: String,
        url: String,
        targetFileName: String,
        fallbackTotalBytes: Long
    ): Flow<DownloadStatus> = flow {
        activeJobs[modelId] = true

        val destinationFile = File(modelsDir, targetFileName)
        val tempFile = File(modelsDir, "$targetFileName.part")

        var requestSuccess = false
        var totalBytes = fallbackTotalBytes

        // Attempt real network download
        if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Leafy-Android-AI/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        requestSuccess = true
                        val body = response.body!!
                        val contentLength = body.contentLength()
                        if (contentLength > 0) {
                            totalBytes = contentLength
                        }

                        var downloaded = 0L
                        var lastSampleTime = System.currentTimeMillis()
                        var lastSampleBytes = 0L
                        var currentSpeed = 0f

                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int

                                while (input.read(buffer).also { read = it } != -1) {
                                    if (activeJobs[modelId] == false || !coroutineContext.isActive) {
                                        tempFile.delete()
                                        emit(
                                            DownloadStatus(
                                                modelId = modelId,
                                                progress = 0f,
                                                downloadedBytes = 0L,
                                                totalBytes = totalBytes,
                                                speedMbPerSec = 0f,
                                                localFilePath = "",
                                                isComplete = false,
                                                error = "Download cancelled by user."
                                            )
                                        )
                                        return@flow
                                    }

                                    output.write(buffer, 0, read)
                                    downloaded += read

                                    val now = System.currentTimeMillis()
                                    val deltaMs = now - lastSampleTime
                                    if (deltaMs >= 400L) {
                                        val deltaBytes = downloaded - lastSampleBytes
                                        currentSpeed = (deltaBytes / (1024f * 1024f)) / (deltaMs / 1000f)
                                        lastSampleTime = now
                                        lastSampleBytes = downloaded

                                        val prog = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                        emit(
                                            DownloadStatus(
                                                modelId = modelId,
                                                progress = prog,
                                                downloadedBytes = downloaded,
                                                totalBytes = totalBytes,
                                                speedMbPerSec = currentSpeed,
                                                localFilePath = destinationFile.absolutePath,
                                                isComplete = false
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (destinationFile.exists()) {
                            destinationFile.delete()
                        }
                        val renamed = tempFile.renameTo(destinationFile)
                        if (!renamed && tempFile.exists()) {
                            tempFile.copyTo(destinationFile, overwrite = true)
                            tempFile.delete()
                        }
                        emit(
                            DownloadStatus(
                                modelId = modelId,
                                progress = 1.0f,
                                downloadedBytes = totalBytes,
                                totalBytes = totalBytes,
                                speedMbPerSec = 0f,
                                localFilePath = destinationFile.absolutePath,
                                isComplete = true
                            )
                        )
                        return@flow
                    } else {
                        emit(
                            DownloadStatus(
                                modelId = modelId,
                                progress = 0f,
                                downloadedBytes = 0L,
                                totalBytes = totalBytes,
                                speedMbPerSec = 0f,
                                localFilePath = "",
                                isComplete = false,
                                error = "Download failed: HTTP ${response.code} ${response.message}"
                            )
                        )
                        return@flow
                    }
                }
            } catch (e: Exception) {
                tempFile.delete()
                emit(
                    DownloadStatus(
                        modelId = modelId,
                        progress = 0f,
                        downloadedBytes = 0L,
                        totalBytes = totalBytes,
                        speedMbPerSec = 0f,
                        localFilePath = "",
                        isComplete = false,
                        error = "Download failed: ${e.message ?: "Network error"}. Please check your internet connection."
                    )
                )
                return@flow
            }
        } else {
            emit(
                DownloadStatus(
                    modelId = modelId,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = totalBytes,
                    speedMbPerSec = 0f,
                    localFilePath = "",
                    isComplete = false,
                    error = "Invalid model download URL provided."
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}

