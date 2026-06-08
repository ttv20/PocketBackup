package com.ttv20.rsyncbackup.model

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedSshPrivateKeyExport(
    val version: Int = 1,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val cipher: String = "AES-256-GCM",
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)

@Serializable
data class SshPrivateKeyExportPayload(
    val publicKey: String? = null,
    val privateKeyPem: String,
    val passphrase: String? = null,
)

object SshPrivateKeyExportCrypto {
    private const val VERSION = 1
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_NAME = "AES-256-GCM"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val DEFAULT_ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private val aad = "pocketbackup-ssh-private-key-export-v1".toByteArray(Charsets.UTF_8)

    fun encrypt(
        payload: SshPrivateKeyExportPayload,
        password: String,
        secureRandom: SecureRandom = SecureRandom(),
        iterations: Int = DEFAULT_ITERATIONS,
    ): EncryptedSshPrivateKeyExport {
        require(password.isNotBlank()) { "Private key export password is required" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported private key export KDF cost" }

        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        val plaintext = ExportCodec.json.encodeToString(
            SshPrivateKeyExportPayload.serializer(),
            payload,
        ).toByteArray(Charsets.UTF_8)
        return EncryptedSshPrivateKeyExport(
            version = VERSION,
            kdf = KDF_ALGORITHM,
            cipher = CIPHER_NAME,
            iterations = iterations,
            salt = base64(salt),
            iv = base64(iv),
            ciphertext = base64(cipher.doFinal(plaintext)),
        )
    }

    fun decrypt(export: EncryptedSshPrivateKeyExport, password: String): SshPrivateKeyExportPayload {
        require(password.isNotBlank()) { "Private key export password is required" }
        require(export.version == VERSION) { "Unsupported private key export version" }
        require(export.kdf == KDF_ALGORITHM) { "Unsupported private key export KDF" }
        require(export.cipher == CIPHER_NAME) { "Unsupported private key export cipher" }
        require(export.iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported private key export KDF cost" }

        val salt = Base64.getDecoder().decode(export.salt)
        val iv = Base64.getDecoder().decode(export.iv)
        val ciphertext = Base64.getDecoder().decode(export.ciphertext)
        val key = deriveKey(password, salt, export.iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            ExportCodec.json.decodeFromString(SshPrivateKeyExportPayload.serializer(), plaintext)
        } catch (error: Exception) {
            throw IllegalArgumentException("Private key export password is incorrect or the export is corrupted", error)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF_ALGORITHM)
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, KEY_ALGORITHM).also {
                Arrays.fill(bytes, 0)
            }
        } finally {
            spec.clearPassword()
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    private fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)
}
