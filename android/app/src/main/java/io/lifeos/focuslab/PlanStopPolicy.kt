package io.lifeos.focuslab

/** Whether a stop represents a decision to suppress this absolute task until its end. */
object PlanStopPolicy {
    fun suppresses(code: String, planned: Boolean): Boolean {
        if (!planned && code != "USER_STOP") return false
        return code !in setOf(
            "PLAN_BOUNDARY", "PLAN_REVISED", "SESSION_EXPIRED", "EXPIRED_OR_INVALID", "REPLACED",
            // Enforcement is already fail-open while disconnected; a genuine grant/rebind may resume it.
            "PLAN_PERMISSION_LOST", "WHITELIST_PERMISSION_LOST", "SERVICE_UNBOUND"
        )
    }
}
