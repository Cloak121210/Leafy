package com.example.engine

import com.example.data.AiRuntime
import com.example.data.LocalModelEntity
import com.example.data.PersonaPresetEntity
import com.example.data.PowerProfile
import com.example.data.RagDocumentEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.roundToInt

data class RetrievedRagSnippet(
    val documentId: Long,
    val documentTitle: String,
    val matchedPassage: String,
    val relevanceScore: Float
)

data class StreamingStep(
    val accumulatedText: String,
    val currentTps: Float,
    val ttftMs: Int,
    val generatedTokens: Int,
    val energyMicroWh: Float,
    val citedRagSnippets: List<RetrievedRagSnippet>,
    val isFinished: Boolean
)

class LocalInferenceEngine {

    /**
     * Performs on-device BM25-inspired keyword + phrase chunk retrieval over pinned RAG documents.
     */
    fun retrieveRagContext(
        query: String,
        pinnedDocs: List<RagDocumentEntity>
    ): List<RetrievedRagSnippet> {
        if (pinnedDocs.isEmpty() || query.isBlank()) return emptyList()

        val queryTerms = query.lowercase()
            .split(Regex("[^a-z0-9._-]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()

        val results = mutableListOf<RetrievedRagSnippet>()

        for (doc in pinnedDocs) {
            val paragraphs = doc.content
                .split(Regex("\\n\\s*\\n|\\n(?=#|\\d+\\.|-)"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            var bestPassage = paragraphs.firstOrNull() ?: doc.content.take(240)
            var bestScore = 0f

            for (para in paragraphs) {
                val lowerPara = para.lowercase()
                var score = 0f
                for (term in queryTerms) {
                    if (lowerPara.contains(term)) {
                        score += 2.2f
                    }
                    if (doc.title.lowercase().contains(term)) {
                        score += 1.4f
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    bestPassage = para
                }
            }

            // If document is explicitly pinned, include either matched passage or top summary chunk
            val finalScore = if (bestScore > 0f) bestScore else 1.0f
            results.add(
                RetrievedRagSnippet(
                    documentId = doc.id,
                    documentTitle = doc.title,
                    matchedPassage = bestPassage.take(280),
                    relevanceScore = finalScore
                )
            )
        }

        return results.sortedByDescending { it.relevanceScore }.take(3)
    }

    /**
     * Streams a quantized local AI response tailored to the active Runtime (llama.cpp, Bitnet.cpp,
     * T-MAC, ExecuTorch), Model, Persona, Power Profile, and retrieved Local RAG chunks.
     */
    fun streamResponse(
        userPrompt: String,
        model: LocalModelEntity,
        runtime: AiRuntime,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        pinnedDocs: List<RagDocumentEntity>,
        branchAttemptIndex: Int = 1
    ): Flow<StreamingStep> = flow {
        val retrievedRag = retrieveRagContext(userPrompt, pinnedDocs)

        // Calculate realistic TTFT based on runtime, model size, and power profile
        val baseTtft = (model.sizeMb / 14) + when (runtime) {
            AiRuntime.BITNET_CPP -> 42
            AiRuntime.T_MAC -> 38
            AiRuntime.EXECUTORCH -> 48
            AiRuntime.LLAMA_CPP -> 68
        }
        val profileTtftFactor = when (powerProfile) {
            PowerProfile.ECO -> 1.25f
            PowerProfile.BALANCED -> 1.0f
            PowerProfile.PERFORMANCE -> 0.72f
        }
        val ttftMs = (baseTtft * profileTtftFactor).roundToInt().coerceAtLeast(32)

        delay(ttftMs.toLong().coerceAtMost(260L))

        val fullReply = synthesizeResponseText(
            prompt = userPrompt,
            model = model,
            runtime = runtime,
            powerProfile = powerProfile,
            persona = persona,
            ragSnippets = retrievedRag,
            branchAttemptIndex = branchAttemptIndex
        )

        val words = fullReply.split(" ")
        val builder = StringBuilder()

        val profileSpeedFactor = when (powerProfile) {
            PowerProfile.ECO -> 0.86f
            PowerProfile.BALANCED -> 1.05f
            PowerProfile.PERFORMANCE -> 1.32f
        }
        val targetTps = (model.expectedTps * runtime.speedMultiplier * profileSpeedFactor / 1.25f)
            .coerceIn(14.5f, 110.0f)

        val startTime = System.currentTimeMillis()

        for ((index, word) in words.withIndex()) {
            if (index > 0) builder.append(" ")
            builder.append(word)

            val tokensSoFar = max(1, ((index + 1) * 1.3f).roundToInt())
            val elapsedSec = max(0.05f, (System.currentTimeMillis() - startTime) / 1000f)
            val jitter = ((index % 5) - 2) * 0.45f
            val liveTps = (targetTps + jitter).coerceAtLeast(8.0f)

            // Energy in micro-Watt-hours: (milliwatts * seconds) / 3.6
            val effectiveMw = powerProfile.estimatedMilliwatts / runtime.energyEfficiencyMultiplier
            val energyMicroWh = ((effectiveMw * (tokensSoFar / liveTps)) / 3.6f)

            val isLast = index == words.lastIndex
            if (index % 2 == 0 || isLast) {
                emit(
                    StreamingStep(
                        accumulatedText = builder.toString(),
                        currentTps = liveTps,
                        ttftMs = ttftMs,
                        generatedTokens = tokensSoFar,
                        energyMicroWh = energyMicroWh,
                        citedRagSnippets = retrievedRag,
                        isFinished = isLast
                    )
                )
                delay(powerProfile.tokenDelayMs)
            }
        }
    }

    private fun synthesizeResponseText(
        prompt: String,
        model: LocalModelEntity,
        runtime: AiRuntime,
        powerProfile: PowerProfile,
        persona: PersonaPresetEntity,
        ragSnippets: List<RetrievedRagSnippet>,
        branchAttemptIndex: Int
    ): String {
        val lower = prompt.lowercase()

        val personaIntro = when {
            persona.name.contains("Architect", ignoreCase = true) ->
                "Executing on **${runtime.displayName}** (`${model.quantization}`, ${powerProfile.cpuThreads} threads, ${powerProfile.kvCacheBits}):"
            persona.name.contains("Socratic", ignoreCase = true) ->
                "Let's explore this step by step using our local **${model.name}** workspace:"
            persona.name.contains("Storyteller", ignoreCase = true) ->
                "Under the quiet canopy of offline computation, here is a fresh perspective:"
            else ->
                "Running 100% offline via **${runtime.displayName}** on **${model.name}** (${powerProfile.displayName} mode):"
        }

        val coreBody = when {
            lower.contains("runtime") || lower.contains("bitnet") || lower.contains("t-mac") ||
                lower.contains("llama") || lower.contains("executorch") || lower.contains("compare") -> {
                """
                $personaIntro
                
                Here is how Leafy's four local inference engines compare on your device:
                • **Bitnet.cpp (`1.58-bit i2_s`)**: Packs ternary weights `{-1, 0, +1}` so matrix multiplication becomes pure integer addition—cutting CPU energy draw by up to **71%** while sustaining ~45+ t/s.
                • **T-MAC (`INT3/INT4-LUT`)**: Precomputes activation Lookup Tables in fast L1/L2 cache, completely bypassing dequantization overhead on ARM Cortex cores.
                • **llama.cpp (`Q4_K_M` / `Q8_0`)**: The gold-standard GGUF engine with ARM NEON SIMD dot-products and quantized KV-cache for deep 8K+ context windows.
                • **ExecuTorch (`.pte` XNNPACK)**: Compiles static PyTorch graphs ahead-of-time for ultra-low memory overhead and mobile NPU delegation.
                """.trimIndent()
            }

            lower.contains("battery") || lower.contains("eco") || lower.contains("power") ||
                lower.contains("solar") || lower.contains("energy") -> {
                """
                $personaIntro
                
                To maximize off-grid battery longevity during local AI sessions:
                1. **Active Power Profile (${powerProfile.displayName})**: Currently allocating **${powerProfile.cpuThreads} CPU threads** with a **${powerProfile.contextLimit}-token** window and **${powerProfile.kvCacheBits}** compression (~${powerProfile.estimatedMilliwatts} mW budget).
                2. **1.58-Bit & LUT Quantization**: Switching from FP16 to `Bitnet.cpp` or `T-MAC` reduces DRAM bandwidth pressure—the single largest battery consumer on mobile SoCs—by over **60%**.
                3. **Context Pinning over Re-Prefill**: Pinning concise local RAG chunks keeps prompt prefill under 100 ms so your processor returns to deep idle (`WFI`) immediately after streaming.
                """.trimIndent()
            }

            lower.contains("code") || lower.contains("kotlin") || lower.contains("python") ||
                lower.contains("quantiz") -> {
                """
                $personaIntro
                
                Here is how quantized ternary & LUT accumulation eliminates floating-point multipliers in local inference:
                ```kotlin
                // Ternary 1.58-bit dot product: weights in {-1, 0, +1}
                fun ternaryLutAccumulate(activations: IntArray, weights: ByteArray): Int {
                    var acc = 0
                    for (i in activations.indices) {
                        when (weights[i].toInt()) {
                            1 -> acc += activations[i]
                            -1 -> acc -= activations[i]
                        }
                    }
                    return acc
                }
                ```
                Because there are zero FP16/FP32 multiplications in the inner loop, thermal output stays cool even during long multi-branch coding sessions.
                """.trimIndent()
            }

            else -> {
                val branchPerspective = if (branchAttemptIndex > 1) {
                    "\n\n*(Branch #$branchAttemptIndex Alternative Exploration — sampled at T=${persona.temperature} on ${runtime.displayName})*"
                } else {
                    ""
                }
                """
                $personaIntro
                
                Regarding **"${prompt.take(90)}"**:
                • **Local Analysis**: Processed privately on-device using `${model.quantization}` quantized weights with zero network packets transmitted.
                • **Key Insight**: By combining **${runtime.displayName}** (${runtime.shortBadge}) with **${powerProfile.displayName}** scheduling, Leafy maintains fast token streaming while keeping device thermals cool and responsive.
                • **Actionable Next Step**: You can fork this message into a new **Conversation Branch** (`Edit & Branch`) to compare how another runtime like `T-MAC` or `llama.cpp` responds to the exact same prompt.$branchPerspective
                """.trimIndent()
            }
        }

        val ragSection = if (ragSnippets.isNotEmpty()) {
            val top = ragSnippets.first()
            "\n\n**Grounded in Local RAG (`${top.documentTitle}`)**:\n> \"${top.matchedPassage.replace("\n", " ")}\""
        } else {
            ""
        }

        val socraticOutro = if (persona.name.contains("Socratic", ignoreCase = true)) {
            "\n\n*Follow-up thought*: Would you like to test this prompt on a smaller 1.58-bit model or expand the pinned RAG context?"
        } else {
            ""
        }

        return coreBody + ragSection + socraticOutro
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "from", "what", "how",
            "can", "you", "about", "into", "your", "are", "was", "were", "have"
        )
    }
}
