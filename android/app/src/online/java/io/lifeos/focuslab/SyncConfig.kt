package io.lifeos.focuslab

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SyncCredentials(val endpoint: String, val token: String)
class SyncConfig(private val context: Context) {
    private val prefs = context.getSharedPreferences("online_secret", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("exchange-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("exchange-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(endpoint: String, token: String) {
        val uri = URI(endpoint.trim().trimEnd('/'))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(token.length in 24..512 && token.none { it.isWhitespace() })
        val sourceChanged = read()?.endpoint != uri.toString()
        if (sourceChanged) PlanStore(context).prefs.edit().putBoolean("usage_upload", false).putBoolean("usage_details_upload", false).commit()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val raw = JSONObject().put("endpoint", uri.toString()).put("token", token).toString().toByteArray()
        val data = cipher.iv + cipher.doFinal(raw)
        check(prefs.edit().putString("sealed", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
        if (sourceChanged) { PlanStore(context).resetSource(); PlanRuntime.reconcile(context, true) }
    }
    fun read(): SyncCredentials? = runCatching {
        val data = Base64.decode(prefs.getString("sealed", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
        val json = JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8))
        SyncCredentials(json.getString("endpoint"), json.getString("token"))
    }.getOrNull()
    fun clear() { prefs.edit().clear().commit() }
}
