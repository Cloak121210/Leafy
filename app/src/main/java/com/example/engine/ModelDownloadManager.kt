package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
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
            if (filePath.startsWith("/proc/self/fd/")) {
                Companion.releasePinnedDescriptor(context, filePath)
                return false
            }
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

    private fun cleanupStaleImportingFiles() {
        try {
            val files = modelsDir.listFiles() ?: return
            for (f in files) {
                if (f.isFile && f.name.endsWith(".importing", ignoreCase = true)) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Resolves a readable POSIX File in Internal Shared Storage from a SAF or file Uri if accessible.
     */
    fun resolveDirectSharedStorageFile(uri: Uri, fallbackDisplayName: String? = null): File? {
        val candidates = mutableListOf<File>()

        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            candidates.add(File(uri.path!!))
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fdLink = try {
                    Os.readlink("/proc/self/fd/${pfd.fd}")
                } catch (_: Throwable) {
                    try {
                        File("/proc/self/fd/${pfd.fd}").canonicalPath
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (!fdLink.isNullOrBlank() && !fdLink.startsWith("/proc/")) {
                    candidates.add(File(fdLink))
                    val emulatedRel = when {
                        fdLink.contains("/emulated/0/") -> fdLink.substringAfter("/emulated/0/")
                        fdLink.startsWith("/data/media/0/") -> fdLink.removePrefix("/data/media/0/")
                        else -> null
                    }
                    if (!emulatedRel.isNullOrBlank()) {
                        candidates.add(File("/storage/emulated/0/$emulatedRel"))
                        try {
                            candidates.add(File(Environment.getExternalStorageDirectory(), emulatedRel))
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("raw:")) {
                    candidates.add(File(docId.removePrefix("raw:")))
                } else if (docId.startsWith("primary:")) {
                    val rel = docId.removePrefix("primary:").trimStart('/')
                    candidates.add(File(Environment.getExternalStorageDirectory(), rel))
                    candidates.add(File("/storage/emulated/0/$rel"))
                }
            }
        } catch (_: Throwable) {}

        val rawPath = uri.path.orEmpty()
        if (rawPath.contains("primary:")) {
            val rel = rawPath.substringAfter("primary:").trimStart('/')
            try {
                candidates.add(File(Environment.getExternalStorageDirectory(), rel))
            } catch (_: Throwable) {}
            candidates.add(File("/storage/emulated/0/$rel"))
        }

        if (!fallbackDisplayName.isNullOrBlank()) {
            try {
                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dlDir != null) {
                    candidates.add(File(dlDir, fallbackDisplayName))
                }
            } catch (_: Throwable) {}
            candidates.add(File("/storage/emulated/0/Download/$fallbackDisplayName"))
            candidates.add(File("/storage/emulated/0/Downloads/$fallbackDisplayName"))
        }

        return candidates.firstOrNull { f ->
            try {
                f.exists() && f.isFile && f.canRead() && f.length() > 0L
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Opens and pins a read-only ParcelFileDescriptor for in-place zero-copy model access via `/proc/self/fd/<fd>`
     * when Android Scoped Storage blocks direct filesystem path opening.
     */
    fun openPinnedProcFdFile(uri: Uri, displayName: String, fallbackSizeBytes: Long = 0L): Pair<File, Long>? {
        return Companion.openAndPinUriDescriptor(context, modelsDir, uri, displayName, fallbackSizeBytes)
    }

    fun ensureAccessibleFile(file: File): File =
        Companion.ensureModelFileAccessible(context, modelsDir, file)

    fun isModelFileAvailable(file: File): Boolean =
        Companion.isModelFileAvailable(context, modelsDir, file)

    fun getModelFileLength(file: File): Long =
        Companion.getModelFileLength(context, modelsDir, file)

    /**
     * Loads a local model file from a user-selected Android Uri (content:// or file://).
     * When [copyToAppStorage] is true, copies the file into the app's local models directory.
     * When [copyToAppStorage] is false, keeps the model in internal shared storage and references its path or pinned FD in-place.
     */
    fun importModelFromUri(uri: Uri, copyToAppStorage: Boolean = true): Flow<LocalModelLoadStatus> = flow {
        var tempFile: File? = null
        try {
            cleanupStaleImportingFiles()
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
                    if (!copyToAppStorage || directFile.absolutePath.startsWith(modelsDir.absolutePath)) {
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

            if (!copyToAppStorage) {
                val directSharedFile = resolveDirectSharedStorageFile(uri, displayName)
                if (directSharedFile != null && directSharedFile.exists() && directSharedFile.canRead() && directSharedFile.length() > 0L) {
                    val fileLen = directSharedFile.length()
                    val sha = computeFileSha256Short(directSharedFile)
                    emit(
                        LocalModelLoadStatus(
                            displayName = displayName,
                            progress = 1.0f,
                            copiedBytes = fileLen,
                            totalBytes = fileLen,
                            localFilePath = directSharedFile.absolutePath,
                            sha256Short = sha,
                            isComplete = true
                        )
                    )
                    return@flow
                }

                val pinnedPair = openPinnedProcFdFile(uri, displayName, totalBytes)
                if (pinnedPair != null && pinnedPair.second > 0L) {
                    val (pinnedFile, fileLen) = pinnedPair
                    val sha = computeFileSha256Short(pinnedFile)
                    emit(
                        LocalModelLoadStatus(
                            displayName = displayName,
                            progress = 1.0f,
                            copiedBytes = fileLen,
                            totalBytes = fileLen,
                            localFilePath = pinnedFile.path,
                            sha256Short = sha,
                            isComplete = true
                        )
                    )
                    return@flow
                } else {
                    emit(
                        LocalModelLoadStatus(
                            displayName = displayName,
                            progress = 0f,
                            copiedBytes = 0L,
                            totalBytes = totalBytes,
                            localFilePath = "",
                            isComplete = false,
                            error = "Could not open read descriptor for '$displayName' in shared storage."
                        )
                    )
                    return@flow
                }
            }

            val safeFileName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destinationFile = File(modelsDir, safeFileName)
            val activeTempFile = File(modelsDir, "$safeFileName.importing")
            tempFile = activeTempFile

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

            // Pre-flight disk space verification (require file size + 32 MB safety reserve)
            val usableBytes = modelsDir.usableSpace
            val existingReclaimableBytes = if (destinationFile.exists()) destinationFile.length() else 0L
            val requiredBytes = totalBytes + (32L * 1024L * 1024L)
            if (totalBytes > 0L && usableBytes > 0L && (usableBytes + existingReclaimableBytes) < requiredBytes) {
                val neededMb = requiredBytes / (1024L * 1024L)
                val freeMb = (usableBytes + existingReclaimableBytes) / (1024L * 1024L)
                emit(
                    LocalModelLoadStatus(
                        displayName = displayName,
                        progress = 0f,
                        copiedBytes = 0L,
                        totalBytes = totalBytes,
                        localFilePath = "",
                        isComplete = false,
                        error = "Insufficient free device storage to import '$displayName' (requires $neededMb MB, only $freeMb MB free)."
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
                FileOutputStream(activeTempFile).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (!coroutineContext.isActive) {
                            activeTempFile.delete()
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
                activeTempFile.delete()
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
            val renamed = activeTempFile.renameTo(destinationFile)
            if (!renamed && activeTempFile.exists()) {
                activeTempFile.copyTo(destinationFile, overwrite = true)
                activeTempFile.delete()
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
            try {
                tempFile?.delete()
            } catch (_: Exception) {}
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
        cleanupStaleImportingFiles()
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
        val fileLen = getModelFileLength(file)
        if (!isModelFileAvailable(file) || fileLen <= 0L) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            Companion.openModelInputStream(context, modelsDir, file)?.use { fis ->
                val buffer = ByteArray(64 * 1024)
                var totalRead = 0L
                val maxSample = 1024L * 1024L
                var read = 0
                while (totalRead < maxSample && fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            } ?: return ""
            digest.update(fileLen.toString().toByteArray(Charsets.UTF_8))
            digest.digest().joinToString("") { "%02x".format(it) }.take(8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private data class PinnedDescriptorEntry(
            val pfd: ParcelFileDescriptor,
            val uriString: String,
            val displayName: String,
            val sizeBytes: Long
        )

        private val pinnedLock = Any()
        private val pinnedByProcPath = mutableMapOf<String, PinnedDescriptorEntry>()
        private const val PINNED_URIS_PREFS = "leafy_pinned_shared_uris"

        private fun resolveDescriptorSize(pfd: ParcelFileDescriptor, fallbackSize: Long = 0L): Long {
            val statSize = try { pfd.statSize } catch (_: Throwable) { -1L }
            if (statSize > 0L) return statSize
            val fstatSize = try { Os.fstat(pfd.fileDescriptor).st_size } catch (_: Throwable) { -1L }
            if (fstatSize > 0L) return fstatSize
            return fallbackSize.coerceAtLeast(0L)
        }

        fun openAndPinUriDescriptor(
            context: Context,
            @Suppress("UNUSED_PARAMETER") modelsDir: File?,
            uri: Uri,
            displayName: String,
            fallbackSizeBytes: Long = 0L
        ): Pair<File, Long>? = synchronized(pinnedLock) {
            return try {
                val uriStr = uri.toString()
                // Reuse existing pinned descriptor if same URI is already open and valid
                val existing = pinnedByProcPath.entries.firstOrNull { (_, entry) ->
                    entry.uriString == uriStr && try { entry.pfd.fd >= 0 } catch (_: Throwable) { false }
                }
                if (existing != null) {
                    val len = resolveDescriptorSize(existing.value.pfd, existing.value.sizeBytes.coerceAtLeast(fallbackSizeBytes))
                    if (len > 0L) {
                        return Pair(File(existing.key), len)
                    }
                }

                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                val rawFd = try { pfd.fd } catch (_: Throwable) { -1 }
                if (rawFd < 0) {
                    try { pfd.close() } catch (_: Throwable) {}
                    return null
                }
                val sizeBytes = resolveDescriptorSize(pfd, fallbackSizeBytes)
                val procPath = "/proc/self/fd/$rawFd"
                pinnedByProcPath[procPath] = PinnedDescriptorEntry(
                    pfd = pfd,
                    uriString = uriStr,
                    displayName = displayName,
                    sizeBytes = sizeBytes
                )
                try {
                    context.applicationContext
                        .getSharedPreferences(PINNED_URIS_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString("path:$procPath", uriStr)
                        .putString("name:$displayName", uriStr)
                        .putLong("size:$uriStr", sizeBytes)
                        .apply()
                } catch (_: Throwable) {}
                Pair(File(procPath), sizeBytes)
            } catch (_: Throwable) {
                null
            }
        }

        fun ensureModelFileAccessible(
            context: Context?,
            modelsDir: File? = null,
            file: File,
            displayNameHint: String? = null
        ): File = synchronized(pinnedLock) {
            val path = file.path
            if (!path.startsWith("/proc/self/fd/")) {
                return file
            }
            val existing = pinnedByProcPath[path]
            if (existing != null) {
                val valid = try {
                    existing.pfd.fd >= 0 && Os.fstat(existing.pfd.fileDescriptor).st_size > 0L
                } catch (_: Throwable) {
                    false
                }
                if (valid) return file
                pinnedByProcPath.remove(path)
            }
            if (context != null) {
                try {
                    val prefs = context.applicationContext.getSharedPreferences(PINNED_URIS_PREFS, Context.MODE_PRIVATE)
                    val uriStr = prefs.getString("path:$path", null)
                        ?: displayNameHint?.let { prefs.getString("name:$it", null) }
                    if (!uriStr.isNullOrBlank()) {
                        val savedSize = prefs.getLong("size:$uriStr", 0L)
                        val reopened = openAndPinUriDescriptor(
                            context = context,
                            modelsDir = modelsDir,
                            uri = Uri.parse(uriStr),
                            displayName = displayNameHint ?: file.name,
                            fallbackSizeBytes = savedSize
                        )
                        if (reopened != null) {
                            return reopened.first
                        }
                    }
                } catch (_: Throwable) {}
            }
            return file
        }

        fun getModelFileLength(
            context: Context?,
            modelsDir: File? = null,
            file: File,
            displayNameHint: String? = null
        ): Long = synchronized(pinnedLock) {
            val resolved = ensureModelFileAccessible(context, modelsDir, file, displayNameHint)
            val path = resolved.path
            if (path.startsWith("/proc/self/fd/")) {
                val entry = pinnedByProcPath[path]
                if (entry != null) {
                    return resolveDescriptorSize(entry.pfd, entry.sizeBytes)
                }
                val fdNum = path.removePrefix("/proc/self/fd/").toIntOrNull()
                if (fdNum != null && fdNum >= 0) {
                    try {
                        val dupPfd = ParcelFileDescriptor.fromFd(fdNum)
                        val sz = resolveDescriptorSize(dupPfd, 0L)
                        try { dupPfd.close() } catch (_: Throwable) {}
                        if (sz > 0L) return sz
                    } catch (_: Throwable) {}
                }
            }
            return try {
                resolved.length()
            } catch (_: Throwable) {
                0L
            }
        }

        fun isModelFileAvailable(
            context: Context?,
            modelsDir: File? = null,
            file: File,
            displayNameHint: String? = null
        ): Boolean = synchronized(pinnedLock) {
            val resolved = ensureModelFileAccessible(context, modelsDir, file, displayNameHint)
            val path = resolved.path
            if (path.startsWith("/proc/self/fd/")) {
                return getModelFileLength(context, modelsDir, resolved, displayNameHint) > 0L
            }
            return try {
                resolved.exists() && resolved.length() > 0L
            } catch (_: Throwable) {
                false
            }
        }

        fun openDuplicatedDescriptor(
            context: Context?,
            modelsDir: File? = null,
            file: File,
            displayNameHint: String? = null
        ): ParcelFileDescriptor? = synchronized(pinnedLock) {
            val resolved = ensureModelFileAccessible(context, modelsDir, file, displayNameHint)
            val path = resolved.path
            if (path.startsWith("/proc/self/fd/")) {
                val entry = pinnedByProcPath[path]
                if (entry != null) {
                    return try {
                        val dup = entry.pfd.dup()
                        try { Os.lseek(dup.fileDescriptor, 0L, android.system.OsConstants.SEEK_SET) } catch (_: Throwable) {}
                        dup
                    } catch (_: Throwable) {
                        null
                    }
                }
                val fdNum = path.removePrefix("/proc/self/fd/").toIntOrNull()
                if (fdNum != null && fdNum >= 0) {
                    return try {
                        val dup = ParcelFileDescriptor.fromFd(fdNum)
                        try { Os.lseek(dup.fileDescriptor, 0L, android.system.OsConstants.SEEK_SET) } catch (_: Throwable) {}
                        dup
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            return try {
                ParcelFileDescriptor.open(resolved, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: Throwable) {
                null
            }
        }

        fun openModelInputStream(
            context: Context?,
            modelsDir: File? = null,
            file: File,
            displayNameHint: String? = null
        ): InputStream? {
            val resolved = ensureModelFileAccessible(context, modelsDir, file, displayNameHint)
            if (!resolved.path.startsWith("/proc/self/fd/")) {
                try {
                    if (resolved.exists() && resolved.canRead()) {
                        return FileInputStream(resolved)
                    }
                } catch (_: Throwable) {}
            }
            val dupPfd = openDuplicatedDescriptor(context, modelsDir, resolved, displayNameHint) ?: return null
            return ParcelFileDescriptor.AutoCloseInputStream(dupPfd)
        }

        fun releasePinnedDescriptor(context: Context?, procPath: String) = synchronized(pinnedLock) {
            val removed = pinnedByProcPath.remove(procPath)
            if (removed != null) {
                try { removed.pfd.close() } catch (_: Throwable) {}
            }
            if (context != null) {
                try {
                    context.applicationContext
                        .getSharedPreferences(PINNED_URIS_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .remove("path:$procPath")
                        .apply()
                } catch (_: Throwable) {}
            }
        }
    }
}
