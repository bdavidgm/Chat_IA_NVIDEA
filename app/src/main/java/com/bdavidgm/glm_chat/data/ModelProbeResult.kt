package com.bdavidgm.glm_chat.data

/** Result of a minimal /chat/completions probe against the user's API key. */
sealed class ModelProbeResult {
    data object Available : ModelProbeResult()
    data object Unavailable : ModelProbeResult()
    data object Unreachable : ModelProbeResult()
}
