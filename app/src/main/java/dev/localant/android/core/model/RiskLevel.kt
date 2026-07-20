package dev.localant.android.core.model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class RiskLevel(val value: Int) {
    init {
        require(value in 0..4) {
            "Risk level must be 0-4, got $value"
        }
    }
}
