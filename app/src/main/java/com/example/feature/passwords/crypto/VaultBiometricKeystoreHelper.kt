package com.example.feature.passwords.crypto

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Protects an optional device-bound copy of the Local Vault DEK inside Android Keystore
 * for Phone Lock (PIN/Pattern/Password) and Biometric (Fingerprint/Face) unlock.
 */
class VaultBiometricKeystoreHelper(
    private val keyAlias: String = BIOMETRIC_KEY_ALIAS,
    private val deviceGateAlias: String = DEVICE_GATE_KEY_ALIAS
) {
    private val keyStore: KeyStore? by lazy {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        }.getOrNull()
    }

    fun isDeviceSecure(context: Context): Boolean = runCatching {
        val kg = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        kg?.isDeviceSecure == true
    }.getOrDefault(false)

    fun isDeviceLockOrBiometricAvailable(context: Context): Boolean = runCatching {
        val manager = BiometricManager.from(context)
        val combined = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (combined == BiometricManager.BIOMETRIC_SUCCESS) return true
        val strongOrWeak = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        strongOrWeak == BiometricManager.BIOMETRIC_SUCCESS || isDeviceSecure(context)
    }.getOrDefault(false)

    fun isBiometricHardwareAvailable(context: Context): Boolean =
        isDeviceLockOrBiometricAvailable(context)

    fun canShowBiometricPrompt(context: Context): Boolean =
        isDeviceLockOrBiometricAvailable(context)

    fun canUseCryptoObjectAuth(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    fun hasDeviceGateKey(): Boolean = runCatching {
        val ks = keyStore ?: return false
        ks.containsAlias(deviceGateAlias)
    }.getOrDefault(false)

    fun prepareEncryptCipher(): Cipher? = runCatching {
        deleteEntry(deviceGateAlias)
        val key = getOrCreateSecretKey(regenerate = false)
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }.getOrElse {
        runCatching {
            val freshKey = getOrCreateSecretKey(regenerate = true)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, freshKey)
            }
        }.getOrNull()
    }

    fun prepareDecryptCipher(nonce: ByteArray): Cipher? = runCatching {
        val key = getExistingSecretKey(keyAlias) ?: return null
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
    }.getOrNull()

    fun wrapDekWithCipher(dek: ByteArray, cipher: Cipher): WrappedKey {
        deleteEntry(deviceGateAlias)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(dek)
        return WrappedKey(encryptedKey = ciphertext, nonce = iv)
    }

    fun unwrapDekWithCipher(wrappedKey: WrappedKey, cipher: Cipher): ByteArray {
        return cipher.doFinal(wrappedKey.encryptedKey)
    }

    fun wrapDekWithDeviceGate(dek: ByteArray): WrappedKey? = runCatching {
        deleteEntry(keyAlias)
        val key = getOrCreateDeviceGateKey(regenerate = false)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        WrappedKey(encryptedKey = cipher.doFinal(dek), nonce = cipher.iv)
    }.getOrElse {
        runCatching {
            val freshKey = getOrCreateDeviceGateKey(regenerate = true)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, freshKey)
            }
            WrappedKey(encryptedKey = cipher.doFinal(dek), nonce = cipher.iv)
        }.getOrNull()
    }

    fun unwrapDekWithDeviceGate(wrappedKey: WrappedKey): ByteArray? = runCatching {
        val key = getExistingSecretKey(deviceGateAlias) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, wrappedKey.nonce))
        }
        cipher.doFinal(wrappedKey.encryptedKey)
    }.getOrNull()

    fun deleteKey() {
        deleteEntry(keyAlias)
        deleteEntry(deviceGateAlias)
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        cipher: Cipher?,
        onSuccess: (Cipher?) -> Unit,
        onError: (String) -> Unit
    ) {
        runCatching {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess(result.cryptoObject?.cipher ?: cipher)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            onError(errString.toString())
                        }
                    }
                }
            )

            val manager = BiometricManager.from(activity)
            val supportsDeviceCredential = isDeviceSecure(activity) ||
                manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS

            if (cipher != null) {
                val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsDeviceCredential) {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()
                } else {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setNegativeButtonText(negativeButtonText)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                }
                prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            } else {
                val promptInfo = if (supportsDeviceCredential) {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()
                } else {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setNegativeButtonText(negativeButtonText)
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        )
                        .build()
                }
                prompt.authenticate(promptInfo)
            }
        }.onFailure { err ->
            onError(err.message ?: "Phone lock & biometric authentication unavailable")
        }
    }

    private fun deleteEntry(alias: String) {
        runCatching {
            val ks = keyStore ?: return
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
            }
        }
    }

    private fun getExistingSecretKey(alias: String): SecretKey? {
        val ks = keyStore ?: return null
        val entry = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    private fun getOrCreateSecretKey(regenerate: Boolean): SecretKey {
        if (regenerate) {
            deleteEntry(keyAlias)
        } else {
            getExistingSecretKey(keyAlias)?.let { return it }
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun getOrCreateDeviceGateKey(regenerate: Boolean): SecretKey {
        if (regenerate) {
            deleteEntry(deviceGateAlias)
        } else {
            getExistingSecretKey(deviceGateAlias)?.let { return it }
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            deviceGateAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "orbit_password_vault_bio_dek_v1"
        private const val DEVICE_GATE_KEY_ALIAS = "orbit_password_vault_device_gate_dek_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
