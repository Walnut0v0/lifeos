package io.lifeos.focuslab

import android.content.Context
import org.json.JSONObject

/** Private metadata/rules only. Manual categories and app exceptions survive rescans. */
class AppRules(private val context: Context) {
    private val prefs = context.getSharedPreferences("lab", Context.MODE_PRIVATE)
    init {
        if (!prefs.contains("app_overrides")) {
            val migrated = JSONObject()
            prefs.getStringSet("whitelist", emptySet())!!.forEach { migrated.put(it, true) }
            prefs.edit().putString("app_overrides", migrated.toString()).commit()
        }
    }
    private fun json(key: String): JSONObject = try { JSONObject(prefs.getString(key, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    private fun category(value: String) = AppCategory.entries.firstOrNull { it.name == value } ?: AppCategory.UNKNOWN
    val userCategories: Map<String, AppCategory> get() = json("user_categories").let { j -> j.keys().asSequence().associateWith { category(j.optString(it)) } }
    val appOverrides: Map<String, Boolean> get() = json("app_overrides").let { j -> j.keys().asSequence().filter { j.opt(it) is Boolean }.associateWith { j.getBoolean(it) } }
    val allowedCategories: Set<AppCategory> get() = prefs.getStringSet("allowed_categories", emptySet())!!.map { category(it) }.toSet()
    val knownCategories: Map<String, AppCategory> get() = json("known_categories").let { j -> j.keys().asSequence().associateWith { category(j.optString(it)) } }
    private fun editable() { val store = LabStore(context); check(!store.isWhitelistSession || store.phase() != Phase.ACTIVE) { "请先结束专注，再调整分类和白名单" } }
    fun classification(app: LabApp): Classification = AppClassifier.classify(app.packageName, app.name, app.category, userCategories[app.packageName])
    fun refresh(apps: List<LabApp>) {
        val user = userCategories
        val previous = knownCategories
        val overrides = json("app_overrides")
        if (prefs.contains("known_categories")) {
            apps.filter { it.packageName !in previous && !overrides.has(it.packageName) }
                .forEach { overrides.put(it.packageName, false) }
        }
        val known = JSONObject()
        apps.forEach { app -> known.put(app.packageName, AppClassifier.classify(app.packageName, app.name, app.category, user[app.packageName]).category.name) }
        prefs.edit().putString("known_categories", known.toString()).putString("app_overrides", overrides.toString()).commit()
        val store = LabStore(context)
        if (!store.isWhitelistSession || store.phase() != Phase.ACTIVE) recompute()
    }
    private fun recompute() {
        val categories = allowedCategories
        val overrides = appOverrides
        val allowed = knownCategories.filter { (pkg, category) -> CategoryAccessPolicy.allows(false, overrides[pkg], category in categories) }.keys
        prefs.edit().putStringSet("whitelist", allowed.toSet()).commit()
    }
    fun setUserCategory(app: LabApp, value: AppCategory?) {
        editable()
        val user = json("user_categories")
        if (value == null) user.remove(app.packageName) else user.put(app.packageName, value.name)
        val known = json("known_categories")
        known.put(app.packageName, AppClassifier.classify(app.packageName, app.name, app.category, value).category.name)
        prefs.edit().putString("user_categories", user.toString()).putString("known_categories", known.toString()).commit()
        recompute()
    }
    fun setCategoryAllowed(value: AppCategory, allowed: Boolean) {
        editable()
        val next = if (allowed) allowedCategories + value else allowedCategories - value
        prefs.edit().putStringSet("allowed_categories", next.map { it.name }.toSet()).commit()
        recompute()
    }
    fun setAppOverride(packageName: String, value: Boolean?) {
        editable()
        val overrides = json("app_overrides")
        if (value == null) overrides.remove(packageName) else overrides.put(packageName, value)
        prefs.edit().putString("app_overrides", overrides.toString()).commit()
        recompute()
    }
    fun replaceEffectiveSet(value: Set<String>) {
        editable()
        val previous = prefs.getStringSet("whitelist", emptySet())!!.toSet()
        val overrides = json("app_overrides")
        (value - previous).forEach { overrides.put(it, true) }
        (previous - value).forEach { overrides.put(it, false) }
        prefs.edit().putString("app_overrides", overrides.toString()).putStringSet("whitelist", value.toSet()).commit()
    }
}
