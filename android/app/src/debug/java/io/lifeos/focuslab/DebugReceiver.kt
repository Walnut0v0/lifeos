package io.lifeos.focuslab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bounded, ADB-only reproduction of the user's already-configured test. No new targets/permissions. */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LabStore(context)
        try {
            when (intent.getStringExtra("operation")) {
                "ui_order_self_test" -> {
                    // Fixed invented labels only: no app inventory, settings or schedule mutations.
                    val names = listOf("微信", "Zoom", "百度", "支付宝", "移动交通大学", "哈啰", "相机", "知乎", "爱读书", "Bing")
                    resultData = try {
                        if (AppDisplayOrder.ordered(names, { it }, { it }) == listOf("相机", "支付宝", "移动交通大学", "哈啰", "爱读书", "百度", "Bing", "微信", "知乎", "Zoom")) "UI_PINNING_AND_PINYIN_PASSED"
                        else "UI_PINNING_AND_PINYIN_FAILED"
                    } catch (_: Exception) { "UI_PINNING_AND_PINYIN_UNAVAILABLE" }
                }
                "classification_self_test" -> {
                    require(store.phase() != Phase.ACTIVE)
                    val prefs = context.getSharedPreferences("lab", Context.MODE_PRIVATE)
                    val keys = listOf("whitelist", "app_overrides", "user_categories", "allowed_categories", "known_categories")
                    val backup = prefs.all.filterKeys { it in keys }
                    try {
                        val apps = AppCatalog(context).apps()
                        val app = apps.first { it.packageName in store.targets }
                        val rules = AppRules(context)
                        rules.refresh(apps)
                        rules.setUserCategory(app, AppCategory.RESEARCH)
                        rules.refresh(apps)
                        check(AppRules(context).classification(app).source == ClassificationSource.USER)
                        check(AppRules(context).classification(app).category == AppCategory.RESEARCH)
                        rules.setAppOverride(app.packageName, null)
                        rules.setCategoryAllowed(AppCategory.RESEARCH, true)
                        check(app.packageName in store.whitelist)
                        rules.setAppOverride(app.packageName, false)
                        check(app.packageName !in store.whitelist)
                        rules.setCategoryAllowed(AppCategory.RESEARCH, false)
                        rules.setAppOverride(app.packageName, true)
                        check(app.packageName in store.whitelist)
                        rules.setAppOverride(app.packageName, null)
                        check(app.packageName !in store.whitelist)
                        rules.setUserCategory(app, null)
                        check(rules.classification(app).source != ClassificationSource.USER)
                        resultData = "CLASSIFICATION_PERSISTENCE_AND_PRECEDENCE_PASSED"
                    } finally {
                        val editor = prefs.edit()
                        keys.forEach { editor.remove(it) }
                        backup.forEach { (key, value) ->
                            when (value) {
                                is String -> editor.putString(key, value)
                                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                            }
                        }
                        check(editor.commit())
                    }
                }
                "start_whitelist" -> {
                    val seconds = intent.getIntExtra("seconds", 30)
                    require(seconds in 1..60)
                    store.startWhitelist(seconds * 1000L)
                    resultData = "STARTED_WHITELIST_TEST"
                }
                "allow_selected_test_app" -> {
                    require(!store.isWhitelistSession || store.phase() != Phase.ACTIVE)
                    require(store.targets.size == 1)
                    val selected = store.targets.first()
                    store.whitelist = if (intent.getBooleanExtra("allowed", false)) store.whitelist + selected else store.whitelist - selected
                    resultData = "TEST_APP_WHITELIST_UPDATED"
                }
                "launch_first_allowed" -> resultData = if (store.whitelist.firstOrNull()?.let { FocusActions.openAllowed(context, it) } == true) "ALLOWED_APP_LAUNCH_REQUESTED" else "ALLOWED_APP_LAUNCH_FAILED"
                "start" -> {
                    val seconds = intent.getIntExtra("seconds", 30)
                    require(seconds in 1..60)
                    require(FocusAccessibilityService.live != null)
                    require(store.targets.isNotEmpty() && store.targets.none { AppCatalog(context).protected(it) })
                    val mode = intent.getStringExtra("mode") ?: "OVERLAY"
                    require(mode == "HOME" || mode == "OVERLAY")
                    val assist = intent.getBooleanExtra("usage_assist", store.usageAssistOptIn)
                    require(!assist || usageGranted(context))
                    store.mode = mode
                    store.start(System.currentTimeMillis(), seconds * 1000L, true)
                    if (assist) {
                        ForegroundProbe.enableForTest(seconds.toLong())
                    }
                    store.log(if (assist) "USAGE_ASSIST_ENABLED" else "USAGE_ASSIST_DISABLED")
                    resultData = "STARTED_BOUNDED_TEST"
                }
                "stop" -> { store.stop(); resultData = "STOPPED" }
                "snapshot" -> resultData = store.report()
                else -> resultData = IntegrationHooks.debug(context, intent) ?: "UNSUPPORTED_OPERATION"
            }
        } catch (_: Exception) { store.stop("DEBUG_COMMAND_FAILED"); resultData = "TEST_NOT_STARTED_CHECK_CONNECTION_TARGETS_AND_PERMISSIONS" }
    }
}
