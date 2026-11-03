package com.example

import com.example.data.LeafyRepository
import com.example.data.MessageNodeEntity
import com.example.data.PersonaPresetEntity
import com.example.data.PowerProfile
import com.example.engine.LocalInferenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun leafyV0994_versionAndPowerProfiles_areConfigured() {
    assertEquals("0.9.9.4", BuildConfig.VERSION_NAME)
    assertEquals(25, BuildConfig.VERSION_CODE)

    val eco = PowerProfile.ECO
    val balanced = PowerProfile.BALANCED
    val turbo = PowerProfile.PERFORMANCE

    assertEquals("Eco Leaf", eco.displayName)
    assertEquals("1 big core at its half frequency", eco.description)
    assertEquals(1, eco.cpuThreads)
    assertEquals(0.50f, eco.targetCorePowerFraction, 0.0001f)
    assertEquals(10, eco.androidThreadPriority)
    assertEquals(256, eco.preferredBatchSize)

    assertEquals("Balanced Canopy", balanced.displayName)
    assertEquals("2 big cores at the biggest core's half frequency", balanced.description)
    assertEquals(2, balanced.cpuThreads)
    assertEquals(0.50f, balanced.targetCorePowerFraction, 0.0001f)
    assertEquals(5, balanced.androidThreadPriority)
    assertEquals(384, balanced.preferredBatchSize)

    assertEquals("Sunlit Turbo", turbo.displayName)
    assertEquals("2 big cores at their top frequency", turbo.description)
    assertEquals(2, turbo.cpuThreads)
    assertEquals(1.00f, turbo.targetCorePowerFraction, 0.0001f)
    assertEquals(-8, turbo.androidThreadPriority)
    assertEquals(512, turbo.preferredBatchSize)

    val engine = LocalInferenceEngine()
    val actualStepNanos = 10_000_000L // 10ms
    val ecoTargetNanos = engine.computeAdpfTargetDurationNanos(actualStepNanos, eco.targetCorePowerFraction)
    val balancedTargetNanos = engine.computeAdpfTargetDurationNanos(actualStepNanos, balanced.targetCorePowerFraction)
    val turboTargetNanos = engine.computeAdpfTargetDurationNanos(actualStepNanos, turbo.targetCorePowerFraction)

    // 50% power request sets target duration to 2x actual duration (so actual/target == 0.50)
    assertEquals(20_000_000L, ecoTargetNanos)
    assertEquals(20_000_000L, balancedTargetNanos)
    // 100% top frequency request sets target duration below actual duration (5ms) to trigger full boost
    assertEquals(5_000_000L, turboTargetNanos)
  }

  @Test
  fun leafyV099_defaultPresetsBundleLlamaCppSamplerParameters() {
    val presets = LeafyRepository.defaultPersonas()
    assertTrue(presets.isNotEmpty())
    val defaultPreset = presets.first()
    assertEquals(1L, defaultPreset.id)
    assertEquals("llama.cpp Default", defaultPreset.name)
    assertEquals(0.80f, defaultPreset.temperature, 0.0001f)
    assertEquals(40, defaultPreset.topK)
    assertEquals(0.95f, defaultPreset.topP, 0.0001f)
    assertEquals(0.05f, defaultPreset.minP, 0.0001f)
    assertEquals(0.10f, defaultPreset.xtcThreshold, 0.0001f)
    assertEquals(0.0f, defaultPreset.xtcProbability, 0.0001f)
    assertEquals(1.0f, defaultPreset.typicalP, 0.0001f)
    assertEquals(64, defaultPreset.penaltyLastN)
    assertEquals(1.0f, defaultPreset.repeatPenalty, 0.0001f)
    assertEquals(0.0f, defaultPreset.penaltyFreq, 0.0001f)
    assertEquals(0.0f, defaultPreset.penaltyPresent, 0.0001f)
    assertEquals(0, defaultPreset.mirostat)
    assertEquals(5.0f, defaultPreset.mirostatTau, 0.0001f)
    assertEquals(0.10f, defaultPreset.mirostatEta, 0.0001f)
    assertEquals(-1, defaultPreset.seed)
    assertTrue(defaultPreset.jinjaEnabled)

    // Verify newly created preset has default llama.cpp sampler values
    val newCustomPreset = PersonaPresetEntity(
      name = "Custom Preset",
      tagline = "Custom",
      systemPrompt = "You are helpful."
    )
    assertEquals(0.80f, newCustomPreset.temperature, 0.0001f)
    assertEquals(40, newCustomPreset.topK)
    assertEquals(0.95f, newCustomPreset.topP, 0.0001f)
    assertEquals(0.05f, newCustomPreset.minP, 0.0001f)
    assertEquals(64, newCustomPreset.penaltyLastN)
    assertEquals(1.0f, newCustomPreset.repeatPenalty, 0.0001f)
    assertEquals(-1, newCustomPreset.seed)
    assertTrue(newCustomPreset.jinjaEnabled)
  }

  @Test
  fun leafyV099_includeThinkingInContext_preservesOrStripsPriorThinkingTags() {
    val engine = LocalInferenceEngine()
    val preset = LeafyRepository.defaultPersonas().first()
    val history = listOf(
      MessageNodeEntity(
        id = 1L,
        conversationId = 1L,
        parentId = null,
        role = "user",
        content = "First question"
      ),
      MessageNodeEntity(
        id = 2L,
        conversationId = 1L,
        parentId = 1L,
        role = "assistant",
        content = "<think>Chain of thought reasoning</think>\nFirst answer"
      )
    )

    val strippedPrompt = engine.buildFormattedPrompt(
      userPrompt = "Follow up",
      persona = preset,
      ragSnippets = emptyList(),
      conversationHistory = history,
      effectiveCtxSize = 4096,
      includeThinkingInContext = false
    )
    assertFalse(strippedPrompt.contains("Chain of thought reasoning"))
    assertTrue(strippedPrompt.contains("First answer"))

    val includedPrompt = engine.buildFormattedPrompt(
      userPrompt = "Follow up",
      persona = preset,
      ragSnippets = emptyList(),
      conversationHistory = history,
      effectiveCtxSize = 4096,
      includeThinkingInContext = true
    )
    assertTrue(includedPrompt.contains("<think>Chain of thought reasoning</think>"))
    assertTrue(includedPrompt.contains("First answer"))
  }
}
