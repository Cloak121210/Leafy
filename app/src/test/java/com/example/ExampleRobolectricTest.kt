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
        assertTrue(eco.estimatedMilliwatts < balanced.estimatedMilliwatts)
        assertTrue(balanced.estimatedMilliwatts < turbo.estimatedMilliwatts)
    }

    @Test
    fun `all four runtimes are registered with valid extensions`() {
        val runtimes = AiRuntime.entries
        assertEquals(4, runtimes.size)
        assertTrue(runtimes.contains(AiRuntime.LLAMA_CPP))
        assertTrue(runtimes.contains(AiRuntime.BITNET_CPP))
        assertTrue(runtimes.contains(AiRuntime.T_MAC))
        assertTrue(runtimes.contains(AiRuntime.EXECUTORCH))

        for (rt in runtimes) {
            assertTrue(rt.fileExtensions.isNotEmpty())
            assertTrue(rt.architectureNote.isNotBlank())
        }
    }

    @Test
    fun `rag context returns empty for greetings and matches relevant queries`() {
        val engine = com.example.engine.LocalInferenceEngine()
        val docs = com.example.data.LeafyRepository.defaultRagDocuments()

        // Greeting should have 0 RAG snippets
        val greetingSnippets = engine.retrieveRagContext("Hi!", docs)
        assertTrue(greetingSnippets.isEmpty())

        // Specific query about solar/battery should match
        val batterySnippets = engine.retrieveRagContext("What is the LiFePO4 battery capacity?", docs)
        assertTrue(batterySnippets.isNotEmpty())
        assertEquals("Off-Grid Solar & Permaculture Notes.txt", batterySnippets.first().documentTitle)
    }

    @Test
    fun `all default models start uninstalled and require download`() {
        val models = com.example.data.LeafyRepository.defaultLocalModels()
        assertTrue(models.isNotEmpty())
        assertTrue("All models must start uninstalled", models.all { !it.isInstalled })
        assertTrue("All models must have blank localFilePath initially", models.all { it.localFilePath.isBlank() })
    }

    @Test
    fun `inference engine refuses inference when model file is not downloaded`() {
        kotlinx.coroutines.runBlocking {
            val engine = com.example.engine.LocalInferenceEngine()
            val uninstalledModel = com.example.data.LeafyRepository.defaultLocalModels().first()
            val persona = com.example.data.LeafyRepository.defaultPersonas().first()
            val docs = com.example.data.LeafyRepository.defaultRagDocuments()

            var lastStep: com.example.engine.StreamingStep? = null
            engine.streamResponse(
                userPrompt = "Hi!",
                model = uninstalledModel,
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
        val installedModel = com.example.data.LeafyRepository.defaultLocalModels().first().copy(
            isInstalled = true,
            localFilePath = tempModelFile.absolutePath
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

        // Test ChatML prompt formatting
        val formattedPrompt = engine.buildFormattedPrompt(
            userPrompt = "Explain attention mechanism",
            persona = persona,
            ragSnippets = emptyList()
        )
        assertTrue(formattedPrompt.contains("<|im_start|>system"))
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
    fun `inference engine validates PTE and TMAC headers properly`() {
        val engine = com.example.engine.LocalInferenceEngine()
        
        // Test PTE header verification
        val tempPte = java.io.File.createTempFile("test_model", ".pte")
        tempPte.writeBytes(byteArrayOf(0, 0, 0, 0, 'E'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte(), '2'.code.toByte()))
        val pteHeader = engine.readPteHeader(tempPte)
        assertTrue(pteHeader.isValidPte)
        tempPte.delete()

        // Test TMAC header verification
        val tempTmac = java.io.File.createTempFile("test_model", ".tmac")
        tempTmac.writeBytes("TMAC".toByteArray(Charsets.US_ASCII) + ByteArray(16))
        val tmacHeader = engine.readTmacHeader(tempTmac)
        assertTrue(tmacHeader.isValidTmac)
        assertEquals("INT3-LUT", tmacHeader.formatType)
        tempTmac.delete()

        // Test T-MAC LUT processor
        val lutProcessor = com.example.engine.TMacLutProcessor(bits = 3, threads = 4)
        val lutOutput = lutProcessor.processPrompt("Explain quantum computing")
        assertTrue(lutOutput.contains("T-MAC LUT"))
        assertTrue(lutOutput.contains("quantum computing"))
    }
}
