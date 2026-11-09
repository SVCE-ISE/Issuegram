package com.svce.issuegram.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.svce.issuegram.data.model.AuthSession
import com.svce.issuegram.data.model.UserProfileDto
import com.svce.issuegram.data.remote.SessionPersistence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureSessionStore by preferencesDataStore(name = "issuegram_identity_v2")

/** Username-only legacy sessions are not imported: they prove no ownership. */
class SessionStore(private val context: Context) : SessionPersistence {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionKey = stringPreferencesKey("encrypted_session")
    private val profileKey = stringPreferencesKey("profile")
    val currentUserFlow = context.secureSessionStore.data.map { prefs ->
        prefs[profileKey]?.let { json.decodeFromString<UserProfileDto>(it) }
    }

    override suspend fun read(): AuthSession? {
        val encrypted = context.secureSessionStore.data.first()[sessionKey] ?: return null
        return json.decodeFromString(decrypt(encrypted))
    }

    override suspend fun save(session: AuthSession) {
        val encrypted = encrypt(json.encodeToString(session))
        context.secureSessionStore.edit { it[sessionKey] = encrypted }
    }

    suspend fun saveUser(profile: UserProfileDto) {
        check(read()?.user?.id == profile.id) { "This profile does not belong to your session." }
        context.secureSessionStore.edit { it[profileKey] = json.encodeToString(profile) }
    }

    override suspend fun clear() { context.secureSessionStore.edit { it.clear() } }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("issuegram.session.v2", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("issuegram.session.v2", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
