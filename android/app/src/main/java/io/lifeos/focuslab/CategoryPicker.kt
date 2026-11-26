package io.lifeos.focuslab

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable fun ColumnScope.CategoryPicker(apps: List<LabApp>, rules: AppRules, selected: Set<String>, revision: Int, onChanged: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var menuPackage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val user = remember(revision, apps) { rules.userCategories }
    val overrides = remember(revision, apps) { rules.appOverrides }
    val categories = remember(revision, apps) { rules.allowedCategories }
    val classified = apps.associateWith { AppClassifier.classify(it.packageName, it.name, it.category, user[it.packageName]) }
    fun edit(action: () -> Unit) {
        try { action(); onChanged(); message = "已保存" }
        catch (_: Exception) { message = "请结束专注后再调整" }
    }
    OutlinedTextField(query, { query = it }, label = { Text("搜索应用名称或包名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("可以整类允许，再对单个应用设例外。你手动改过的分类，刷新后仍会保留。", style = MaterialTheme.typography.bodySmall)
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        AppCategory.entries.forEach { category ->
            val group = apps.filter { classified[it]?.category == category && (it.name.contains(query, true) || it.packageName.contains(query, true)) }
            if (group.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(category in categories, onCheckedChange = { checked -> edit { rules.setCategoryAllowed(category, checked) } })
                    Text("${category.title} · ${group.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("整类允许", style = MaterialTheme.typography.labelSmall)
                }
                group.forEach { app ->
                    val classification = classified.getValue(app)
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(app.packageName in selected, onCheckedChange = { checked -> edit { rules.setAppOverride(app.packageName, checked) } })
                        Image(remember(app.packageName) { app.icon.toBitmap(64, 64).asImageBitmap() }, null, Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(app.name)
                            Text(classification.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val override = overrides[app.packageName]
                            if (override != null) Text(if (override) "单独允许" else "单独关闭", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Box {
                                TextButton(onClick = { menuPackage = app.packageName }) { Text("改分类") }
                                DropdownMenu(expanded = menuPackage == app.packageName, onDismissRequest = { menuPackage = null }) {
                                    AppCategory.entries.forEach { next -> DropdownMenuItem(text = { Text(next.title) }, onClick = { menuPackage = null; edit { rules.setUserCategory(app, next) } }) }
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text("恢复自动分类") }, onClick = { menuPackage = null; edit { rules.setUserCategory(app, null) } })
                                }
                            }
                            if (app.packageName in overrides) TextButton(onClick = { edit { rules.setAppOverride(app.packageName, null) } }) { Text("跟随类别") }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
