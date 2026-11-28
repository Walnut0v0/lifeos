package io.lifeos.focuslab

import io.lifeos.focuslab.ui.theme.LifeOsTheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleConnectActivity : ComponentActivity() {
    private var message by mutableStateOf("选择Google账号和私有交换表。连接后会同步当前专注模式及白名单包名；全天统计与应用清单仍需单独开启。")
    private var working by mutableStateOf(false)
    private val resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { accept(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)) }
        catch (error: Exception) { failed(error) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LifeOsTheme { Surface { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("连接 Google 交换表", style = MaterialTheme.typography.headlineSmall)
            Text(message)
            Button(enabled = !working, onClick = { authorize() }) { Text(if (working) "正在核对…" else "选择交换表并连接") }
            TextButton(onClick = { finish() }) { Text("返回 LifeOS") }
        } } } }
    }
    private fun authorize() {
        working = true
        PlanStore(this).prefs.edit().putString("google_check", "AUTH_STARTED").apply()
        Identity.getAuthorizationClient(this).authorize(GoogleAuthorization.request(true))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) resolution.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
                else accept(result)
            }.addOnFailureListener { failed(it) }
    }
    private fun accept(result: AuthorizationResult) {
        lifecycleScope.launch {
            try {
                GoogleAuthorization.savePicked(this@GoogleConnectActivity, result)
                val initialized = withContext(Dispatchers.IO) {
                    val credentials = SyncConfig(this@GoogleConnectActivity).read() ?: error("AUTH_REQUIRED")
                    val adapter = GoogleSheetsAdapter(this@GoogleConnectActivity, credentials)
                    adapter.verify()
                    adapter.publishMode()
                    adapter.initialPlan()
                }
                PlanStore(this@GoogleConnectActivity).prefs.edit().putString("google_check", "CONNECTED").putString("sync_status", if (initialized) "已连通，计划尚未发布" else "Google交换表已连通").commit()
                message = if (initialized) "连接成功，已核对交换表并同步当前专注模式。计划尚未发布，手机不会开始专注。" else "连接成功，已核对交换表和专注模式。"
                SyncWork.request(this@GoogleConnectActivity, true)
            } catch (error: Exception) { failed(error) }
            finally { working = false }
        }
    }
    private fun failed(error: Exception) {
        val code = if (error is ApiException) "GOOGLE_AUTH_${error.statusCode}" else when (error.message) {
            "SELECT_ONE_WORKBOOK", "WORKBOOK_SCHEMA", "DIFFERENT_DEVICE", "AUTH_REQUIRED", "GOOGLE_API_DISABLED", "FILE_NOT_AUTHORIZED", "CELL_TOO_LARGE" -> error.message!!
            else -> "GOOGLE_CONNECT_FAILED"
        }
        PlanStore(this).prefs.edit().putString("google_check", code).commit()
        message = when (code) {
            "GOOGLE_AUTH_10" -> "Google应用配置未匹配。请核对Android客户端的软件包名、SHA-1和所属项目。错误码10。"
            "GOOGLE_AUTH_12501", "GOOGLE_AUTH_16" -> "你取消了授权，可以稍后重试。"
            "SELECT_ONE_WORKBOOK" -> "请选择一份LifeOS - Focus Exchange工作簿。"
            "WORKBOOK_SCHEMA" -> "选中的文件与Focus Exchange协议不符，请重新选择交换表。"
            "GOOGLE_API_DISABLED" -> "Google项目中有必需API尚未启用。"
            "FILE_NOT_AUTHORIZED", "AUTH_REQUIRED" -> "Google文件授权未完成，请重新授权并选择交换表。"
            "DIFFERENT_DEVICE" -> "交换表中的模式属于另一个设备，尚未覆盖；需要先确认设备关联。"
            else -> "暂未连通，诊断代码：$code。未开启自动专注或统计上传。"
        }
        working = false
    }
}
