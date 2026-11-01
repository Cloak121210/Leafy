package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiRuntime
import com.example.data.PowerProfile
import com.example.ui.components.MarkdownBlock
import com.example.ui.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context confirms Leafy branding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Leafy", appName)
        assertEquals("0.9.7", BuildConfig.VERSION_NAME)
        assertEquals(18, BuildConfig.VERSION_CODE)
    }

    @Test
    fun `markdown parser parses headers and code blocks properly`() {
        val markdown = """
            # Header Title
            Here is some text with information.
            ```kotlin
            fun hello() = "world"
            ```
            • Bullet point 1
            • Bullet point 2
            1. Numbered item 1
            2. Numbered item 2
            > A blockquote on energy efficiency
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertTrue(blocks.any { it is MarkdownBlock.Header && it.level == 1 && it.text == "Header Title" })
        assertTrue(blocks.any { it is MarkdownBlock.CodeBlock && it.language == "kotlin" && it.code.contains("fun hello()") })
        assertTrue(blocks.any { it is MarkdownBlock.BulletItem && it.text.contains("Bullet point 1") })
        assertTrue(blocks.any { it is MarkdownBlock.NumberedItem && it.number == "1" })
        assertTrue(blocks.any { it is MarkdownBlock.BlockQuote })
    }

    @Test
    fun `markdown parser handles in-progress streaming code block`() {
        val streamingMarkdown = """
            Here is the code:
            ```python
            print("generating...")
        """.trimIndent()

        val blocks = parseMarkdownBlocks(streamingMarkdown)
        val codeBlock = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().firstOrNull()
        assertTrue(codeBlock != null)
        assertEquals("python", codeBlock?.language)
        assertTrue(codeBlock?.code?.contains("generating") == true)
    }

    @Test
    fun `power profiles adhere to hardware constraints`() {
        val eco = PowerProfile.ECO
        val balanced = PowerProfile.BALANCED
        val turbo = PowerProfile.PERFORMANCE

        assertTrue(eco.cpuThreads <= balanced.cpuThreads)
        assertTrue(balanced.cpuThreads <= turbo.cpuThreads)
        assertTrue(eco.contextLimit <= balanced.contextLimit)
        assertTrue(balanced.contextLimit <= turbo.contextLimit)
    }

    @Test
    fun `all four runtimes are registered with valid extensions and factual bundle flags`() {
        val runtimes = AiRuntime.entries
        assertEquals(4, runtimes.size)
        assertTrue(runtimes.contains(AiRuntime.LLAMA_CPP))
        assertTrue(runtimes.contains(AiRuntime.BITNET_CPP))
        assertTrue(runtimes.contains(AiRuntime.T_MAC))
        assertTrue(runtimes.contains(AiRuntime.EXECUTORCH))

        assertTrue(AiRuntime.LLAMA_CPP.hasBundledNativeLibrary)
        assertTrue(AiRuntime.BITNET_CPP.hasBundledNativeLibrary)
        assertTrue(AiRuntime.EXECUTORCH.hasBundledNativeLibrary)
        assertTrue(AiRuntime.T_MAC.hasBundledNativeLibrary)

        for (rt in runtimes) {
            assertTrue(rt.fileExtensions.isNotEmpty())
            assertTrue(rt.architectureNote.isNotBlank())
        }
    }

    @Test
    fun `rag context returns empty for greetings and matches relevant queries`() {
        val engine = com.example.engine.LocalInferenceEngine()
        assertTrue("No fake pre-populated RAG documents should exist by default", com.example.data.LeafyRepository.defaultRagDocuments().isEmpty())

        val docs = listOf(
            com.example.data.RagDocumentEntity(
                id = 1L,
                title = "Off-Grid Solar & Permaculture Notes.txt",
                category = "Notes",
                content = "Our cabin battery bank uses a 48V 100Ah LiFePO4 battery with 4.8kWh capacity and MPPT solar charge controller.",
                chunkCount = 1,
                tokenEstimate = 24,
                isPinnedToChat = true
            )
        )

        // Greeting should have 0 RAG snippets
        val greetingSnippets = engine.retrieveRagContext("Hi!", docs)
        assertTrue(greetingSnippets.isEmpty())

        // Specific query about solar/battery should match
        val batterySnippets = engine.retrieveRagContext("What is the LiFePO4 battery capacity?", docs)
        assertTrue(batterySnippets.isNotEmpty())
        assertEquals("Off-Grid Solar & Permaculture Notes.txt", batterySnippets.first().documentTitle)
    }

    @Test
    fun `no fake pre-populated models exist by default`() {
        val models = com.example.data.LeafyRepository.defaultLocalModels()
        assertTrue("Default local models must be empty with zero fake downloads", models.isEmpty())
    }

    @Test
    fun `local model manager imports model file from URI into verified local storage`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val manager = com.example.engine.ModelDownloadManager(context)
            val tempSourceFile = java.io.File.createTempFile("local_source_model", ".gguf")
            val validGgufBytes = java.io.ByteArrayOutputStream().apply {
                write("GGUF".toByteArray(Charsets.US_ASCII))
                write(byteArrayOf(3, 0, 0, 0))
                write(byteArrayOf(64, 0, 0, 0, 0, 0, 0, 0))
                write(byteArrayOf(12, 0, 0, 0, 0, 0, 0, 0))
                write(ByteArray(1024) { (it % 127).toByte() })
            }.toByteArray()
            tempSourceFile.writeBytes(validGgufBytes)

            var finalStatus: com.example.engine.LocalModelLoadStatus? = null
            manager.importModelFromUri(android.net.Uri.fromFile(tempSourceFile)).collect { status ->
                finalStatus = status
            }
            assertTrue(finalStatus != null)
            assertTrue(finalStatus!!.isComplete)
            val loadedFile = java.io.File(finalStatus!!.localFilePath)
            assertTrue(loadedFile.exists())
            assertEquals(validGgufBytes.size.toLong(), finalStatus!!.totalBytes)
            assertTrue(finalStatus!!.sha256Short.isNotEmpty())

            tempSourceFile.delete()
        }
    }

    @Test
    fun `inference engine refuses inference when model file is missing on disk`() {
        kotlinx.coroutines.runBlocking {
            val engine = com.example.engine.LocalInferenceEngine()
            val missingModel = com.example.data.LocalModelEntity(
                id = "missing_test_model",
                name = "Missing Test Model",
                family = "GGUF",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                quantization = "Q4_K_M",
                parameters = "135M",
                sizeMb = 100,
                ramRequiredMb = 256,
                contextWindow = 2048,
                expectedTps = 0f,
                ecoRating = 5,
                specialty = "Test model",
                isInstalled = false,
                isUserImported = true,
                localFilePath = "/nonexistent/path/model.gguf",
                sha256Short = "none",
                fileSizeBytes = 0L
            )
            val persona = com.example.data.LeafyRepository.defaultPersonas().first()
            val docs = com.example.data.LeafyRepository.defaultRagDocuments()

            var lastStep: com.example.engine.StreamingStep? = null
            engine.streamResponse(
                userPrompt = "Hi!",
                model = missingModel,
                runtime = AiRuntime.LLAMA_CPP,
                powerProfile = PowerProfile.ECO,
                persona = persona,
                pinnedDocs = docs
            ).collect { step ->
                lastStep = step
            }

            val text = lastStep?.accumulatedText ?: ""
            assertTrue(text.contains("Error: Local AI model weights not found on device"))
        }
    }

    @Test
    fun `inference engine validates authentic GGUF binary files and rejects invalid ones`() {
        kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.engine.LocalInferenceEngine(context)
        val tempModelFile = java.io.File.createTempFile("test_model", ".gguf")

        // First test invalid file (not GGUF magic bytes)
        tempModelFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24))
        val installedModel = com.example.data.LocalModelEntity(
            id = "test_gguf_model",
            name = "Test GGUF Model",
            family = "GGUF",
            runtimeId = AiRuntime.LLAMA_CPP.id,
            quantization = "Q4_K_M",
            parameters = "135M",
            sizeMb = 1,
            ramRequiredMb = 256,
            contextWindow = 2048,
            expectedTps = 0f,
            ecoRating = 5,
            specialty = "Test local model",
            isInstalled = true,
            isUserImported = true,
            localFilePath = tempModelFile.absolutePath,
            sha256Short = "abcd1234",
            fileSizeBytes = tempModelFile.length()
        )
        val persona = com.example.data.LeafyRepository.defaultPersonas().first()
        val docs = com.example.data.LeafyRepository.defaultRagDocuments()

        var lastStep: com.example.engine.StreamingStep? = null
        engine.streamResponse(
            userPrompt = "Hi!",
            model = installedModel,
            runtime = AiRuntime.LLAMA_CPP,
            powerProfile = PowerProfile.ECO,
            persona = persona,
            pinnedDocs = docs
        ).collect { step ->
            lastStep = step
        }
        val textInvalid = lastStep?.accumulatedText ?: ""
        assertTrue(textInvalid.contains("is not a valid GGUF model file"))

        // Now test valid GGUF binary header inspection directly
        val validGgufBytes = java.io.ByteArrayOutputStream().apply {
            write("GGUF".toByteArray(Charsets.US_ASCII))
            // Version = 3 (Int32 LE)
            write(byteArrayOf(3, 0, 0, 0))
            // Tensor count = 120 (Int64 LE)
            write(byteArrayOf(120, 0, 0, 0, 0, 0, 0, 0))
            // Metadata KV count = 15 (Int64 LE)
            write(byteArrayOf(15, 0, 0, 0, 0, 0, 0, 0))
        }.toByteArray()
        tempModelFile.writeBytes(validGgufBytes)

        val header = engine.readGgufHeader(tempModelFile)
        assertTrue(header.isValidGguf)
        assertEquals("GGUF", header.magicString)
        assertEquals(3, header.version)
        assertEquals(120L, header.tensorCount)
        assertEquals(15L, header.metadataKvCount)

        // Test ChatML prompt formatting with multi-turn conversation history
        val history = listOf(
            com.example.data.MessageNodeEntity(
                id = 1L,
                conversationId = 1L,
                parentId = null,
                role = "USER",
                content = "Hello Leafy",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                modelName = "Test",
                quantization = "Q4_0",
                powerProfileId = PowerProfile.ECO.id
            ),
            com.example.data.MessageNodeEntity(
                id = 2L,
                conversationId = 1L,
                parentId = 1L,
                role = "ASSISTANT",
                content = "Hi! How can I help you offline today?",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                modelName = "Test",
                quantization = "Q4_0",
                powerProfileId = PowerProfile.ECO.id
            )
        )
        val formattedPrompt = engine.buildFormattedPrompt(
            userPrompt = "Explain attention mechanism",
            persona = persona,
            ragSnippets = emptyList(),
            conversationHistory = history
        )
        assertTrue(formattedPrompt.contains("<|im_start|>system"))
        assertTrue(formattedPrompt.contains("<|im_start|>user\nHello Leafy"))
        assertTrue(formattedPrompt.contains("<|im_start|>assistant\nHi! How can I help you offline today?"))
        assertTrue(formattedPrompt.contains("<|im_start|>user\nExplain attention mechanism"))
        assertTrue(formattedPrompt.contains("<|im_start|>assistant"))

        tempModelFile.delete()
        }
    }

    @Test
    fun `executorch library is present and available in runtime classpath`() {
        val moduleClass = Class.forName("org.pytorch.executorch.Module", false, javaClass.classLoader)
        assertTrue(moduleClass != null)
        val tensorClass = Class.forName("org.pytorch.executorch.Tensor", false, javaClass.classLoader)
        assertTrue(tensorClass != null)
        val evalueClass = Class.forName("org.pytorch.executorch.EValue", false, javaClass.classLoader)
        assertTrue(evalueClass != null)
    }

    @Test
    fun `inference engine validates PTE and TMAC headers and fails honestly when libtmac is unbundled`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val engine = com.example.engine.LocalInferenceEngine(context)

            // Test PTE header verification
            val tempPte = java.io.File.createTempFile("test_model", ".pte")
            tempPte.writeBytes(byteArrayOf(0, 0, 0, 0, 'E'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte(), '2'.code.toByte()))
            val pteHeader = engine.readPteHeader(tempPte)
            assertTrue(pteHeader.isValidPte)
            tempPte.delete()

            // Test TMAC header verification and honest NotImplementedError reporting
            val tempTmac = java.io.File.createTempFile("test_model", ".tmac")
            tempTmac.writeBytes("TMAC".toByteArray(Charsets.US_ASCII) + ByteArray(512) { (it * 7).toByte() })
            val tmacHeader = engine.readTmacHeader(tempTmac)
            assertTrue(tmacHeader.isValidTmac)
            assertEquals("INT3-LUT", tmacHeader.formatType)

            val tmacModel = com.example.data.LocalModelEntity(
                id = "tmac_test_model",
                name = "test_model.tmac",
                family = "T-MAC",
                runtimeId = AiRuntime.T_MAC.id,
                quantization = "INT3-LUT",
                parameters = "1 MB",
                sizeMb = 1,
                ramRequiredMb = 256,
                contextWindow = 2048,
                expectedTps = 0f,
                ecoRating = 5,
                specialty = "T-MAC test",
                isInstalled = true,
                isUserImported = true,
                localFilePath = tempTmac.absolutePath,
                sha256Short = "12345678",
                fileSizeBytes = tempTmac.length()
            )
            val persona = com.example.data.LeafyRepository.defaultPersonas().first()
            var lastStep: com.example.engine.StreamingStep? = null
            engine.streamResponse(
                userPrompt = "Hello",
                model = tmacModel,
                runtime = AiRuntime.T_MAC,
                powerProfile = PowerProfile.ECO,
                persona = persona,
                pinnedDocs = emptyList()
            ).collect { step ->
                lastStep = step
            }
            assertTrue(lastStep != null)
            assertTrue(lastStep!!.accumulatedText.contains("NotImplementedError"))
            assertTrue(lastStep!!.accumulatedText.contains("libtmac_llama.so"))
            tempTmac.delete()
        }
    }

    @Test
    fun `runtime compatibility rejects cross-engine spoofing between GGUF and ExecuTorch PTE`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val engine = com.example.engine.LocalInferenceEngine(context)
            val tempGguf = java.io.File.createTempFile("lfm_test", ".gguf")
            val validGgufBytes = java.io.ByteArrayOutputStream().apply {
                write("GGUF".toByteArray(Charsets.US_ASCII))
                write(byteArrayOf(3, 0, 0, 0))
                write(byteArrayOf(120, 0, 0, 0, 0, 0, 0, 0))
                write(byteArrayOf(15, 0, 0, 0, 0, 0, 0, 0))
            }.toByteArray()
            tempGguf.writeBytes(validGgufBytes)

            val ggufModel = com.example.data.LocalModelEntity(
                id = "gguf_test_model",
                name = "LFM2.5-1.2B-Instruct-Q4_0",
                family = "GGUF",
                runtimeId = AiRuntime.LLAMA_CPP.id,
                quantization = "Q4_0",
                parameters = "663 MB",
                sizeMb = 663,
                ramRequiredMb = 828,
                contextWindow = 4096,
                expectedTps = 0f,
                ecoRating = 5,
                specialty = "GGUF test model",
                isInstalled = true,
                isUserImported = true,
                localFilePath = tempGguf.absolutePath,
                sha256Short = "1234abcd",
                fileSizeBytes = tempGguf.length()
            )

            // GGUF is compatible with llama.cpp, Bitnet.cpp, and T-MAC, but NOT ExecuTorch
            assertTrue(AiRuntime.LLAMA_CPP.isCompatibleWithModel(ggufModel))
            assertTrue(AiRuntime.BITNET_CPP.isCompatibleWithModel(ggufModel))
            assertTrue(AiRuntime.T_MAC.isCompatibleWithModel(ggufModel))
            assertTrue(!AiRuntime.EXECUTORCH.isCompatibleWithModel(ggufModel))

            val persona = com.example.data.LeafyRepository.defaultPersonas().first()
            var lastStep: com.example.engine.StreamingStep? = null
            engine.streamResponse(
                userPrompt = "Hello",
                model = ggufModel,
                runtime = AiRuntime.EXECUTORCH,
                powerProfile = PowerProfile.ECO,
                persona = persona,
                pinnedDocs = emptyList()
            ).collect { step ->
                lastStep = step
            }

            assertTrue(lastStep != null)
            assertTrue(lastStep!!.accumulatedText.contains("not compatible with ExecuTorch"))
            tempGguf.delete()
        }
    }

    @Test
    fun `live tps telemetry tracker separates prefill TTFT from pure decode TPS`() {
        var fakeClockNs = 1_000_000_000L // 1.0s baseline
        val tracker = com.example.engine.LiveTpsTelemetryTracker(
            promptTokensCount = 60,
            hardwareMeasuredMilliwatts = 0f,
            nanoTimeProvider = { fakeClockNs }
        )

        // 1. Prefill phase at +300ms
        fakeClockNs += 300_000_000L
        val prefillState = tracker.buildPrefillStep(citedRagSnippets = emptyList())
        assertEquals(com.example.engine.InferenceTelemetryPhase.PREFILLING, prefillState.phase)
        assertEquals(300, prefillState.ttftMs)
        assertEquals(0, prefillState.generatedTokens)
        assertEquals(60, prefillState.promptTokensCount)

        // 2. First token arrives at +500ms total elapsed (TTFT = 500ms, prefill = 60 tok / 0.5s = 120 tok/s)
        fakeClockNs += 200_000_000L
        val firstTokenState = tracker.onTokenReceived(
            cleanAccumulatedText = "Hello",
            citedRagSnippets = emptyList(),
            pieceTokenDelta = 1
        )
        assertEquals(com.example.engine.InferenceTelemetryPhase.DECODING, firstTokenState.phase)
        assertEquals(500, firstTokenState.ttftMs)
        assertEquals(1, firstTokenState.generatedTokens)
        assertEquals(120f, firstTokenState.prefillTps, 1.0f)

        // 3. Emit 10 more tokens spaced by 50ms each (20 tok/s pure decode rate, 50 ms/tok ITL)
        val sb = StringBuilder("Hello")
        for (i in 1..10) {
            fakeClockNs += 50_000_000L // +50ms per token
            sb.append(" tok$i")
            tracker.onTokenReceived(
                cleanAccumulatedText = sb.toString(),
                citedRagSnippets = emptyList(),
                pieceTokenDelta = 1
            )
        }

        val finalState = tracker.buildFinalStep(
            cleanAccumulatedText = sb.toString(),
            citedRagSnippets = emptyList()
        )
        assertEquals(com.example.engine.InferenceTelemetryPhase.COMPLETED, finalState.phase)
        assertEquals(11, finalState.generatedTokens)
        assertEquals(500, finalState.ttftMs)
        // Pure decode: 10 decode tokens after first token over 500ms = 20.0 tok/s
        assertEquals(20.0f, finalState.currentTps, 0.5f)
        assertEquals(50.0f, finalState.interTokenLatencyMs, 1.5f)
        assertTrue("Peak TPS should be at least 20 tok/s", finalState.peakTps >= 19.5f)
        assertTrue("Sparkline series should contain recorded samples", finalState.tpsSeries.isNotEmpty())
        assertEquals(-1f, finalState.energyMicroWh, 0.001f)
    }

    @Test
    fun `llama context params include valid non-null Int model_fd to satisfy LlamaContext constructor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.engine.LocalInferenceEngine(context)
        val tempModel = java.io.File(context.cacheDir, "test_fd_check.gguf")
        tempModel.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        try {
            val detachedFd = engine.openDetachedModelFd(tempModel)
            assertTrue("Detached model_fd must be a valid non-negative descriptor", detachedFd >= 0)
            val params = engine.buildLlamaContextParams(
                canonicalPath = tempModel.absolutePath,
                modelFd = detachedFd,
                nCtx = 2048,
                nBatch = 512,
                nThreads = 4,
                useMmap = true
            )
            // Verify exact non-null Int cast required by org.nehuatl.llamacpp.LlamaContext.<init>
            val castModelFd = params["model_fd"] as Int
            assertEquals(detachedFd, castModelFd)
            assertEquals(tempModel.absolutePath, params["model"] as String)
            assertEquals(2048, params["n_ctx"] as Int)
            assertEquals(512, params["n_batch"] as Int)
            assertEquals(4, params["n_threads"] as Int)
            assertEquals(true, params["use_mmap"] as Boolean)
            try {
                android.os.ParcelFileDescriptor.adoptFd(detachedFd).close()
            } catch (_: Throwable) {}
        } finally {
            tempModel.delete()
        }
    }

    @Test
    fun `readGgufHeader extracts general_architecture and general_file_type quantization from GGUF v3 KV metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.engine.LocalInferenceEngine(context)
        val tempGguf = java.io.File(context.cacheDir, "arch_check.gguf")
        try {
            val archKey = "general.architecture".toByteArray(Charsets.UTF_8)
            val archVal = "lfm2".toByteArray(Charsets.UTF_8)
            val ftypeKey = "general.file_type".toByteArray(Charsets.UTF_8)
            val tmplKey = "tokenizer.chat_template".toByteArray(Charsets.UTF_8)
            val tmplVal = "{% for message in messages %}<|start_header_id|>{{ message['role'] }}<|end_header_id|>{% endfor %}".toByteArray(Charsets.UTF_8)
            val ggufBytes = java.io.ByteArrayOutputStream().apply {
                write("GGUF".toByteArray(Charsets.US_ASCII))
                write(byteArrayOf(3, 0, 0, 0)) // version 3
                write(byteArrayOf(64, 0, 0, 0, 0, 0, 0, 0)) // 64 tensors
                write(byteArrayOf(3, 0, 0, 0, 0, 0, 0, 0)) // 3 KV entries
                // KV 1: general.architecture = "lfm2"
                write(byteArrayOf(archKey.size.toByte(), 0, 0, 0, 0, 0, 0, 0))
                write(archKey)
                write(byteArrayOf(8, 0, 0, 0)) // GGUF_TYPE_STRING
                write(byteArrayOf(archVal.size.toByte(), 0, 0, 0, 0, 0, 0, 0))
                write(archVal)
                // KV 2: general.file_type = 2 (LLAMA_FTYPE_MOSTLY_Q4_0 -> Q4_0)
                write(byteArrayOf(ftypeKey.size.toByte(), 0, 0, 0, 0, 0, 0, 0))
                write(ftypeKey)
                write(byteArrayOf(4, 0, 0, 0)) // GGUF_TYPE_UINT32
                write(byteArrayOf(2, 0, 0, 0)) // value = 2 (Q4_0)
                // KV 3: tokenizer.chat_template
                write(byteArrayOf(tmplKey.size.toByte(), 0, 0, 0, 0, 0, 0, 0))
                write(tmplKey)
                write(byteArrayOf(8, 0, 0, 0)) // GGUF_TYPE_STRING
                write(byteArrayOf(tmplVal.size.toByte(), 0, 0, 0, 0, 0, 0, 0))
                write(tmplVal)
            }.toByteArray()
            tempGguf.writeBytes(ggufBytes)

            val header = engine.readGgufHeader(tempGguf)
            assertTrue(header.isValidGguf)
            assertEquals(3, header.version)
            assertEquals(64L, header.tensorCount)
            assertEquals(3L, header.metadataKvCount)
            assertEquals("lfm2", header.architecture)
            assertEquals(2, header.fileType)
            assertEquals("Q4_0", header.quantizationLabel)
            assertTrue(header.chatTemplate?.contains("<|start_header_id|>") == true)
            assertEquals("I2_S (1.58-bit)", com.example.engine.LocalInferenceEngine.mapGgufFileTypeToQuantization(38))
        } finally {
            tempGguf.delete()
        }
    }

    @Test
    fun `defaultPersonas includes Blank preset and buildFormattedPrompt omits system role when blank`() {
        val defaults = com.example.data.LeafyRepository.defaultPersonas()
        val blankPreset = defaults.find { it.name == "Blank" && it.isBuiltIn }
        assertTrue("Default personas must include a built-in Blank preset", blankPreset != null)
        assertEquals("", blankPreset?.systemPrompt)

        val engine = com.example.engine.LocalInferenceEngine()
        val formatted = engine.buildFormattedPrompt(
            userPrompt = "Hello Leafy!",
            persona = blankPreset!!,
            ragSnippets = emptyList(),
            conversationHistory = emptyList(),
            effectiveCtxSize = 2048
        )
        assertTrue("Formatted prompt with Blank preset must not contain a system block", !formatted.contains("<|im_start|>system"))
        assertTrue(formatted.contains("<|im_start|>user\nHello Leafy!"))
    }

    @Test
    fun `importModelFromUri supports keeping model in shared storage vs copying to app storage`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val manager = com.example.engine.ModelDownloadManager(context)
            val externalSimFile = java.io.File(context.cacheDir, "shared_storage_model.gguf")
            try {
                externalSimFile.writeBytes("GGUF\u0003\u0000\u0000\u0000test_weights_payload".toByteArray())
                val fileUri = android.net.Uri.fromFile(externalSimFile)

                // 1. Keep in Internal Shared Storage (copyToAppStorage = false)
                val keepStatuses = mutableListOf<com.example.engine.LocalModelLoadStatus>()
                manager.importModelFromUri(fileUri, copyToAppStorage = false).collect { keepStatuses.add(it) }
                val keepFinal = keepStatuses.last()
                assertTrue(keepFinal.isComplete)
                assertEquals(externalSimFile.absolutePath, keepFinal.localFilePath)

                // 2. Copy to App Storage (copyToAppStorage = true)
                val copyStatuses = mutableListOf<com.example.engine.LocalModelLoadStatus>()
                manager.importModelFromUri(fileUri, copyToAppStorage = true).collect { copyStatuses.add(it) }
                val copyFinal = copyStatuses.last()
                assertTrue(copyFinal.isComplete)
                assertTrue(copyFinal.localFilePath.startsWith(manager.modelsDir.absolutePath))
                java.io.File(copyFinal.localFilePath).delete()
            } finally {
                externalSimFile.delete()
            }
        }
    }

    @Test
    fun `P1_018_runtime_and_power_profile_accent_colors_satisfy_WCAG_AA_contrast_in_light_and_dark_themes`() {
        fun relativeLuminance(color: androidx.compose.ui.graphics.Color): Double {
            fun channel(c: Float): Double {
                val v = c.toDouble()
                return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }

        fun contrastRatio(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color): Double {
            val l1 = relativeLuminance(fg)
            val l2 = relativeLuminance(bg)
            val lighter = maxOf(l1, l2)
            val darker = minOf(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun blendOver(
            fg: androidx.compose.ui.graphics.Color,
            alpha: Float,
            bg: androidx.compose.ui.graphics.Color
        ): androidx.compose.ui.graphics.Color {
            return androidx.compose.ui.graphics.Color(
                red = fg.red * alpha + bg.red * (1f - alpha),
                green = fg.green * alpha + bg.green * (1f - alpha),
                blue = fg.blue * alpha + bg.blue * (1f - alpha),
                alpha = 1f
            )
        }

        val lightSurface = com.example.ui.theme.ElevatedLeafCard
        val darkSurface = com.example.ui.theme.DarkCanopySurface

        for (runtime in com.example.data.AiRuntime.entries) {
            val lightAccent = com.example.ui.components.runtimeAccentColorForTheme(runtime, isDarkTheme = false)
            val darkAccent = com.example.ui.components.runtimeAccentColorForTheme(runtime, isDarkTheme = true)

            val lightDirectRatio = contrastRatio(lightAccent, lightSurface)
            val lightPill14Ratio = contrastRatio(lightAccent, blendOver(lightAccent, 0.14f, lightSurface))
            val lightPill20Ratio = contrastRatio(lightAccent, blendOver(lightAccent, 0.20f, lightSurface))

            val darkDirectRatio = contrastRatio(darkAccent, darkSurface)
            val darkPill14Ratio = contrastRatio(darkAccent, blendOver(darkAccent, 0.14f, darkSurface))
            val darkPill20Ratio = contrastRatio(darkAccent, blendOver(darkAccent, 0.20f, darkSurface))

            assertTrue("Light direct contrast for $runtime ($lightDirectRatio) must be >= 4.5", lightDirectRatio >= 4.5)
            assertTrue("Light 14% pill contrast for $runtime ($lightPill14Ratio) must be >= 4.5", lightPill14Ratio >= 4.5)
            assertTrue("Light 20% pill contrast for $runtime ($lightPill20Ratio) must be >= 4.5", lightPill20Ratio >= 4.5)
            assertTrue("Dark direct contrast for $runtime ($darkDirectRatio) must be >= 4.5", darkDirectRatio >= 4.5)
            assertTrue("Dark 14% pill contrast for $runtime ($darkPill14Ratio) must be >= 4.5", darkPill14Ratio >= 4.5)
            assertTrue("Dark 20% pill contrast for $runtime ($darkPill20Ratio) must be >= 4.5", darkPill20Ratio >= 4.5)
        }

        for (profile in com.example.data.PowerProfile.entries) {
            val lightAccent = com.example.ui.components.powerProfileAccentColorForTheme(profile, isDarkTheme = false)
            val darkAccent = com.example.ui.components.powerProfileAccentColorForTheme(profile, isDarkTheme = true)

            val lightDirectRatio = contrastRatio(lightAccent, lightSurface)
            val lightPill14Ratio = contrastRatio(lightAccent, blendOver(lightAccent, 0.14f, lightSurface))
            val darkDirectRatio = contrastRatio(darkAccent, darkSurface)
            val darkPill14Ratio = contrastRatio(darkAccent, blendOver(darkAccent, 0.14f, darkSurface))

            assertTrue("Light direct contrast for $profile ($lightDirectRatio) must be >= 4.5", lightDirectRatio >= 4.5)
            assertTrue("Light 14% pill contrast for $profile ($lightPill14Ratio) must be >= 4.5", lightPill14Ratio >= 4.5)
            assertTrue("Dark direct contrast for $profile ($darkDirectRatio) must be >= 4.5", darkDirectRatio >= 4.5)
            assertTrue("Dark 14% pill contrast for $profile ($darkPill14Ratio) must be >= 4.5", darkPill14Ratio >= 4.5)
        }
    }

    @Test
    fun `P1_006_font_families_bind_authentic_distinct_weight_and_italic_font_resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fontResIds = listOf(
            R.font.quicksand,
            R.font.quicksand_500,
            R.font.quicksand_600,
            R.font.quicksand_700,
            R.font.nunito,
            R.font.nunito_400italic,
            R.font.nunito_500,
            R.font.nunito_600,
            R.font.nunito_700,
            R.font.nunito_700italic,
            R.font.comic_neue,
            R.font.comic_neue_400italic,
            R.font.comic_neue_700,
            R.font.comic_neue_700italic,
            R.font.jetbrains_mono,
            R.font.jetbrains_mono_400italic,
            R.font.jetbrains_mono_500,
            R.font.jetbrains_mono_600,
            R.font.jetbrains_mono_700
        )
        assertEquals("All 19 font resource IDs must be distinct", 19, fontResIds.toSet().size)
        for (resId in fontResIds) {
            val typeface = androidx.core.content.res.ResourcesCompat.getFont(context, resId)
            assertTrue("Font resource $resId must load a valid non-null Typeface", typeface != null)
        }
    }

    @Test
    fun `P1_021_shouldAutoFollowStreamingTail_follows_when_anchored_and_pauses_when_scrolled_up`() {
        assertTrue(
            "Should auto-follow when user is anchored at the last visible item within threshold",
            com.example.ui.screens.shouldAutoFollowStreamingTail(
                totalItemsCount = 5,
                lastVisibleItemIndex = 4,
                lastVisibleItemOffset = 700,
                lastVisibleItemSize = 350,
                viewportEndOffset = 1000,
                slackPx = 140
            )
        )
        assertTrue(
            "Should NOT auto-follow when user scrolled up to an earlier message item",
            !com.example.ui.screens.shouldAutoFollowStreamingTail(
                totalItemsCount = 5,
                lastVisibleItemIndex = 2,
                lastVisibleItemOffset = 100,
                lastVisibleItemSize = 200,
                viewportEndOffset = 1000,
                slackPx = 140
            )
        )
        assertTrue(
            "Should NOT auto-follow when user scrolled up far inside a tall streaming bubble",
            !com.example.ui.screens.shouldAutoFollowStreamingTail(
                totalItemsCount = 5,
                lastVisibleItemIndex = 4,
                lastVisibleItemOffset = 200,
                lastVisibleItemSize = 1400,
                viewportEndOffset = 1000,
                slackPx = 140
            )
        )
    }

    @Test
    fun `P1_025_mountedModelPathFlow_tracks_native_context_and_clears_on_offload`() {
        kotlinx.coroutines.runBlocking {
            val engine = com.example.engine.LocalInferenceEngine()
            assertEquals(null, engine.mountedModelPathFlow.value)
            engine.offloadModelFromRam()
            assertEquals(null, engine.mountedModelPathFlow.value)
            assertEquals(null, engine.getMountedModelPath())
        }
    }

    @Test
    fun `QF1_buildLeafyTypography_enforces_4_tier_weight_hierarchy`() {
        for (useComic in listOf(false, true)) {
            val typo = com.example.ui.theme.buildLeafyTypography(useComicSans = useComic)
            // Level 1: Screen & Hero Titles -> Bold (700)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, typo.displayLarge.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, typo.displayMedium.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, typo.headlineLarge.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, typo.titleLarge.fontWeight)

            // Level 2: Section & Card Headings -> SemiBold (600)
            assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, typo.headlineMedium.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, typo.titleMedium.fontWeight)

            // Level 3: Sub-headings, Item Titles & Primary Actions -> Medium (500)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, typo.titleSmall.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, typo.labelLarge.fontWeight)

            // Level 4: Badges, Telemetry & Body Copy -> Medium (500) / Normal (400)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, typo.labelMedium.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, typo.labelSmall.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, typo.bodyLarge.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, typo.bodyMedium.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, typo.bodySmall.fontWeight)

            // Verify compact line heights and disabled font padding for vertical space efficiency
            assertTrue("titleLarge lineHeight must be compact (<= 20.sp)", typo.titleLarge.lineHeight.value <= 20.5f)
            assertTrue("bodyMedium lineHeight must be compact (<= 17.sp)", typo.bodyMedium.lineHeight.value <= 17.0f)
            assertEquals(false, typo.bodyMedium.platformStyle?.paragraphStyle?.includeFontPadding)
        }

        // Verify useGradientText attaches a living multi-stop gradient Brush to important heading styles
        val gradTypo = com.example.ui.theme.buildLeafyTypography(
            useComicSans = false,
            useGradientText = true,
            isDarkTheme = false
        )
        assertTrue("displayLarge should have gradient brush when useGradientText=true", gradTypo.displayLarge.brush != null)
        assertTrue("headlineLarge should have gradient brush when useGradientText=true", gradTypo.headlineLarge.brush != null)
        assertTrue("titleLarge should have gradient brush when useGradientText=true", gradTypo.titleLarge.brush != null)
        assertTrue("titleMedium should have gradient brush when useGradientText=true", gradTypo.titleMedium.brush != null)
        assertEquals("bodyMedium should remain solid for readability", null, gradTypo.bodyMedium.brush)
    }
}





