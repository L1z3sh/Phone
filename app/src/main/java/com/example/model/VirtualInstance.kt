package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class InstanceProfileMode(val label: String, val badge: String) {
    GAME_AFK("AFK Game Node", "AFK"),
    WEB_FARM("Web Automation", "BOT"),
    SECURE_BROWSE("Private Sandbox", "SEC"),
    DOWNLOAD_NODE("Fetch Node", "DL");

    companion object {
        fun fromRaw(raw: String): InstanceProfileMode =
            entries.firstOrNull { it.name == raw } ?: GAME_AFK
    }
}

enum class ResolutionPreset(val label: String, val widthPx: Int, val heightPx: Int) {
    HD_720("720x1280 (Smooth)", 720, 1280),
    FHD_1080("1080x1920 (Crisp)", 1080, 1920),
    QHD_1440("1440x2560 (Ultra)", 1440, 2560),
    DESKTOP_16_9("1280x720 (Wide)", 1280, 720);

    companion object {
        fun fromRaw(raw: String): ResolutionPreset =
            entries.firstOrNull { it.name == raw } ?: FHD_1080
    }
}

enum class UserAgentMode(val label: String, val userAgentString: String) {
    ANDROID_MOBILE(
        "Android 15 Mobile",
        "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    ),
    DESKTOP_CHROME(
        "Desktop Chrome",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    ),
    TABLET_PIXEL(
        "Android Tablet",
        "Mozilla/5.0 (Linux; Android 15; Pixel Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    );

    companion object {
        fun fromRaw(raw: String): UserAgentMode =
            entries.firstOrNull { it.name == raw } ?: ANDROID_MOBILE
    }
}

enum class InstanceStatus(val label: String) {
    STOPPED("Standby"),
    RUNNING("Active"),
    AFK_BACKGROUND("AFK Background");

    companion object {
        fun fromRaw(raw: String): InstanceStatus =
            entries.firstOrNull { it.name == raw } ?: STOPPED
    }
}

@Keep
@IgnoreExtraProperties
data class VirtualInstance(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val profileMode: String = InstanceProfileMode.GAME_AFK.name,
    val resolutionPreset: String = ResolutionPreset.FHD_1080.name,
    val userAgentMode: String = UserAgentMode.ANDROID_MOBILE.name,
    val targetUrl: String = "https://example.org",
    val status: String = InstanceStatus.STOPPED.name,
    val assignedMacroId: String = "",
    val keepAwake: Boolean = true,
    val adShieldEnabled: Boolean = true,
    val httpsOnly: Boolean = true,
    val totalRunSeconds: Long = 0L,
    val cycleCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun profileEnum(): InstanceProfileMode = InstanceProfileMode.fromRaw(profileMode)
    fun resolutionEnum(): ResolutionPreset = ResolutionPreset.fromRaw(resolutionPreset)
    fun userAgentEnum(): UserAgentMode = UserAgentMode.fromRaw(userAgentMode)
    fun statusEnum(): InstanceStatus = InstanceStatus.fromRaw(status)
}
