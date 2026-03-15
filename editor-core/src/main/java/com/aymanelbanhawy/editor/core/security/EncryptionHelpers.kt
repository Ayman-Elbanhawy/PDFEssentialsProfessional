package com.aymanelbanhawy.editor.core.security

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecureFileCipher {
    fun encryptToFile(plainBytes: ByteArray, destination: File)
    fun decryptFromFile(source: File): ByteArray
}

class AndroidSecureFileCipher(
    context: Context,
    private val keyAlias: String = "enterprise_pdf_secure_store",
) : SecureFileCipher {

    private val appContext = context.applicationContext

    override fun encryptToFile(plainBytes: ByteArray, destination: File) {
        destination.parentFile?.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(plainBytes)
        destination.writeBytes(payload)
    }

    override fun decryptFromFile(source: File): ByteArray {
        val bytes = source.readBytes()
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val cipherBytes = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEY_STORE)
        val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }
}

object PinHasher {
    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
