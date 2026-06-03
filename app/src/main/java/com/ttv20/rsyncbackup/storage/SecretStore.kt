package com.ttv20.rsyncbackup.storage

import android.content.Context
import android.util.Base64
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun put(alias: String, bytes: ByteArray)
    fun get(alias: String): ByteArray?
    fun delete(alias: String)
}

class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("encrypted-secrets", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun put(alias: String, bytes: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(alias))
        val payload = EncryptedSecretPayload(
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP),
        )
        prefs.edit()
            .putString(alias, ExportCodecForSecrets.encode(payload))
            .apply()
    }

    override fun get(alias: String): ByteArray? {
        val encoded = prefs.getString(alias, null) ?: return null
        val payload = ExportCodecForSecrets.decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyFor(alias),
            GCMParameterSpec(128, Base64.decode(payload.iv, Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(payload.ciphertext, Base64.NO_WRAP))
    }

    override fun delete(alias: String) {
        prefs.edit().remove(alias).apply()
        runCatching { keyStore.deleteEntry(alias) }
    }

    private fun keyFor(alias: String): SecretKey {
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance("AES", KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_DECRYPT or
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

@Serializable
private data class EncryptedSecretPayload(
    val iv: String,
    val ciphertext: String,
)

private object ExportCodecForSecrets {
    fun encode(payload: EncryptedSecretPayload): String =
        com.ttv20.rsyncbackup.model.ExportCodec.json.encodeToString(
            EncryptedSecretPayload.serializer(),
            payload,
        )

    fun decode(text: String): EncryptedSecretPayload =
        com.ttv20.rsyncbackup.model.ExportCodec.json.decodeFromString(
            EncryptedSecretPayload.serializer(),
            text,
        )
}
