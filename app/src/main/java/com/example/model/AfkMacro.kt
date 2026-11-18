package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MacroActionType(val label: String, val hint: String) {
    TAP("Tap Screen", "x,y normalized (e.g. 0.50,0.65)"),
    WAIT("Wait Delay", "Milliseconds (e.g. 2500)"),
    SCROLL("Scroll Viewport", "Vertical pixels (e.g. 320)"),
    RELOAD("Reload Page", "0"),
    NAV("Navigate URL", "https://..."),
    JS("Run Script", "JS snippet inside sandbox"),
    PING("HTTP Health Ping", "https://... keep-alive GET");

    companion object {
        fun fromRaw(raw: String): MacroActionType =
            entries.firstOrNull { it.name == raw } ?: WAIT
    }
}

data class MacroStep(
    val type: MacroActionType,
    val arg: String,
    val label: String
) {
    fun encode(): String {
        val safeArg = arg.replace("\n", " ").take(180)
        val safeLabel = label.replace("\n", " ").replace(":", "-").take(60)
        return "${type.name}:$safeArg:$safeLabel"
    }

    companion object {
        fun decode(raw: String): MacroStep {
            val firstColon = raw.indexOf(':')
            if (firstColon <= 0) {
                return MacroStep(MacroActionType.WAIT, "1000", raw.ifBlank { "Wait 1s" })
            }
            val typeStr = raw.substring(0, firstColon)
            val rest = raw.substring(firstColon + 1)
            val lastColon = rest.lastIndexOf(':')
            val (arg, label) = if (lastColon >= 0) {
                rest.substring(0, lastColon) to rest.substring(lastColon + 1)
            } else {
                rest to typeStr
            }
            return MacroStep(
                type = MacroActionType.fromRaw(typeStr),
                arg = arg,
                label = label.ifBlank { typeStr }
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AfkMacro(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val loopForever: Boolean = true,
    val maxLoops: Long = 100L,
    val intervalMs: Long = 3000L,
    val steps: List<String> = emptyList(),
    val runCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun decodedSteps(): List<MacroStep> = steps.map { MacroStep.decode(it) }
}
