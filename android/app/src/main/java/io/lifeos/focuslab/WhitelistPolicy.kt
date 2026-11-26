package io.lifeos.focuslab

object WhitelistPolicy {
    fun blocks(active: Boolean, knownApp: Boolean, allowed: Boolean, protected: Boolean, unlocked: Boolean): Boolean =
        active && knownApp && !allowed && !protected && unlocked
}

fun countdownText(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
