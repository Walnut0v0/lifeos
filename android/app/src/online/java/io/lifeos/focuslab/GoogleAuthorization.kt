package io.lifeos.focuslab

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

object GoogleAuthorization {
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val PREFIX = "https://sheets.googleapis.com/v4/spreadsheets/"
    fun isGoogle(credentials: SyncCredentials?) = credentials?.endpoint?.startsWith(PREFIX) == true && credentials.endpoint.removePrefix(PREFIX).matches(Regex("[A-Za-z0-9_-]{10,200}"))
    fun request(picker: Boolean): AuthorizationRequest {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE))).setOptOutIncludingGrantedScopes(true)
        if (picker) request.setPrompt(AuthorizationRequest.Prompt.CONSENT or AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
            .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_MULTIPLE, "false")
            .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES, "application/vnd.google-apps.spreadsheet")
        return request.build()
    }
    fun savePicked(context: Context, result: AuthorizationResult) {
        require(result.grantedScopes.contains(DRIVE_FILE)) { "AUTH_REQUIRED" }
        val ids = result.tokenResponseParams?.getString("picked_file_ids")?.split(',') ?: emptyList()
        require(ids.size == 1 && ids.single().matches(Regex("[A-Za-z0-9_-]{10,200}"))) { "SELECT_ONE_WORKBOOK" }
        val token = result.accessToken ?: error("AUTH_REQUIRED")
        SyncConfig(context).save(PREFIX + ids.single(), token)
        PlanStore(context).prefs.edit().putBoolean("modes_sync", true).putString("sync_status", "已选择私有表，正在核对协议").commit()
    }
    fun refreshed(context: Context, credentials: SyncCredentials): SyncCredentials {
        if (!isGoogle(credentials)) return credentials
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request(false)), 20, TimeUnit.SECONDS)
        require(!result.hasResolution()) { "AUTH_REQUIRED" }
        val token = result.accessToken ?: error("AUTH_REQUIRED")
        require(SyncConfig(context).read()?.endpoint == credentials.endpoint) { "SOURCE_CHANGED" }
        SyncConfig(context).save(credentials.endpoint, token)
        return SyncCredentials(credentials.endpoint, token)
    }
}
