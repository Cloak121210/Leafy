package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class LocalModelLoadStatus(
    val displayName: String,
    val progress: Float, // 0.0 .. 1.0
    val copiedBytes: Long,
    val totalBytes: Long,
    val localFilePath: String,
    val sha256Short: String = "",
    val isComplete: Boolean,
    val error: String? = null
)

class ModelDownloadManager(private val context: Context) {

    val modelsDir: File
        get() {
            val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun deleteModelFile(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        return try {
            val file = File(filePath)
            // Only delete files inside our app's modelsDir so we never delete a user's external original file if referenced directly
            if (file.exists() && file.absolutePath.startsWith(modelsDir.absolutePath)) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Loads a local model file from a user-selected Android Uri (content:// or file://)
     * into the app's local models directory so native C++ engines (llama.cpp, Bitnet.cpp,
     * ExecuTorch, T-MAC) can memory-map and read it via POSIX file paths.
     */
    fun importModelFromUri(uri: Uri): Flow<LocalModelLoadStatus> = flow {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Not all URI providers support persistable permissions; safe to ignore
            }

            var displayName = "local_model.gguf"
            var totalBytes = 0L

            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val directFile = File(uri.path!!)
                if (directFile.exists() && directFile.length() > 0L) {
                    displayName = directFile.name
                    totalBytes = directFile.length()
                    if (directFile.canRead()) {
                        val sha = computeFileSha256Short(directFile)
                        emit(
                            LocalModelLoadStatus(
                                displayName = displayName,
                                progress = 1.0f,
                                copiedBytes = totalBytes,
                                totalBytes = totalBytes,
                                localFilePath = directFile.absolutePath,
                                sha256Short = sha,
                                isComplete = true
                            )
                        )
                        return@flow
                    }
                }
            }

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            displayName = name
                        }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        totalBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            if (displayName == "local_model.gguf") {
                val lastSeg = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                if (!lastSeg.isNullOrBlank() && lastSeg.contains('.')) {
                    displayName = lastSeg
                }
            }

            val safeFileName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destinationFile = File(modelsDir, safeFileName)
            val tempFile = File(modelsDir, "$safeFileName.importing")

            // If exact same file already exists in modelsDir with same byte size, reuse it
            if (destinationFile.exists() && totalBytes > 0L && destinationFile.length() == totalBytes) {
                val sha = computeFileSha256Short(destinationFile)
                emit(
                    LocalModelLoadStatus(
                        displayName = displayName,
                        progress = 1.0f,
                        copiedBytes = totalBytes,
                        totalBytes = totalBytes,
                        localFilePath = destinationFile.absolutePath,
                        sha256Short = sha,
                        isComplete = true
                    )
                )
                return@flow
            }

            emit(
                LocalModelLoadStatus(
                    displayName = displayName,
                    progress = 0.01f,
                    copiedBytes = 0L,
                    totalBytes = totalBytes,
                    localFilePath = destinationFile.absolutePath,
                    isComplete = false
                )
            )

            var pfd: ParcelFileDescriptor? = null
            val inputStream: InputStream? = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
            }

            if (inputStream == null) {
                emit(
                    LocalModelLoadStatus(
                        displayName = displayName,
                        progress = 0f,
                        copiedBytes = 0L,
                        totalBytes = totalBytes,
                        localFilePath = "",
                        isComplete = false,
                        error = "Could not open read stream for selected file: $displayName"
                    )
                )
                return@flow
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var hashedBytes = 0L
            val maxHashSampleBytes = 2L * 1024L * 1024L // Hash first 2 MB + file length for fast cryptographic ID

            var copied = 0L
            var lastEmitTime = System.currentTimeMillis()

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (!coroutineContext.isActive) {
                            tempFile.delete()
                            return@flow
                        }
                        output.write(buffer, 0, read)
                        if (hashedBytes < maxHashSampleBytes) {
                            val toHash = minOf(read.toLong(), maxHashSampleBytes - hashedBytes).toInt()
                            digest.update(buffer, 0, toHash)
                            hashedBytes += toHash
                        }
                        copied += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 250L) {
                            lastEmitTime = now
                            val effectiveTotal = if (totalBytes > 0L) totalBytes else copied
                            val prog = if (totalBytes > 0L) {
                                (copied.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                            } else {
                                0.5f
                            }
                            emit(
                                LocalModelLoadStatus(
                                    displayName = displayName,
                                    progress = prog,
                                    copiedBytes = copied,
                                    totalBytes = effectiveTotal,
                                    localFilePath = destinationFile.absolutePath,
                                    isComplete = false
                                )
                            )
                        }
                    }
                    output.fd.sync()
                }
            }

            try {
                pfd?.close()
            } catch (_: Exception) {}

            if (copied <= 0L) {
                tempFile.delete()
                emit(
                    LocalModelLoadStatus(
                        displayName = displayName,
                        progress = 0f,
                        copiedBytes = 0L,
                        totalBytes = 0L,
                        localFilePath = "",
                        isComplete = false,
                        error = "Selected file '$displayName' is empty (0 bytes)."
                    )
                )
                return@flow
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            val renamed = tempFile.renameTo(destinationFile)
            if (!renamed && tempFile.exists()) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            digest.update(copied.toString().toByteArray(Charsets.UTF_8))
            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }.take(8)

            emit(
                LocalModelLoadStatus(
                    displayName = displayName,
                    progress = 1.0f,
                    copiedBytes = copied,
                    totalBytes = copied,
                    localFilePath = destinationFile.absolutePath,
                    sha256Short = shaHex,
                    isComplete = true
                )
            )
        } catch (e: Exception) {
            emit(
                LocalModelLoadStatus(
                    displayName = "Model Import",
                    progress = 0f,
                    copiedBytes = 0L,
                    totalBytes = 0L,
                    localFilePath = "",
                    isComplete = false,
                    error = "Failed to load local model file: ${e.message ?: e.javaClass.simpleName}"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Checks whether a file is an ExecuTorch / LLM companion tokenizer binary or vocabulary file
     * rather than a standalone model weight archive.
     */
    fun isCompanionTokenizerFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower == "tokenizer.bin" ||
            lower == "tokenizer.model" ||
            lower == "tokenizer.json" ||
            lower.endsWith(".tokenizer.bin") ||
            lower.endsWith(".tokenizer.model") ||
            lower.endsWith(".tokenizer.json")
    }

    /**
     * Scans local app model directories and standard Download folder (if readable)
     * for real .gguf, .pte, .tmac, .bitnet, or .bin model files on disk.
     */
    fun scanDiskForModelFiles(): List<File> {
        val supportedExts = setOf("gguf", "pte", "tmac", "bitnet", "bin")
        val candidateDirs = mutableListOf<File>()
        candidateDirs.add(modelsDir)
        context.filesDir?.let { candidateDirs.add(File(it, "models")) }
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists() && downloadsDir.canRead()) {
                candidateDirs.add(downloadsDir)
            }
        } catch (_: Exception) {}

        val discovered = mutableMapOf<String, File>()
        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && f.canRead() && f.length() >= 8L && !isCompanionTokenizerFile(f.name)) {
                    val ext = f.extension.lowercase()
                    if (ext in supportedExts) {
                        discovered[f.absolutePath] = f
                    }
                }
            }
        }
        return discovered.values.toList()
    }

    fun computeFileSha256Short(file: File): String {
        if (!file.exists() || file.length() <= 0L) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var totalRead = 0L
                val maxSample = 1024L * 1024L
                var read = 0
                while (totalRead < maxSample && fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            }
            digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
            digest.digest().joinToString("") { "%02x".format(it) }.take(8)
        } catch (_: Exception) {
            ""
        }
    }
}
