package com.example.feature.passwords.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

data class Argon2Params(
    val memoryKb: Int = DEFAULT_MEMORY_KB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM
) {
    companion object {
        // Responsive mobile baseline (~90-180ms, 16 MiB) preventing OOM on low-RAM Android devices
        const val DEFAULT_MEMORY_KB = 16_384
        const val DEFAULT_ITERATIONS = 3
        const val DEFAULT_PARALLELISM = 2
        const val KEY_LENGTH_BYTES = 32
        const val SALT_LENGTH_BYTES = 16
    }
}

/**
 * Argon2id key derivation helper backed by BouncyCastle's reviewed implementation.
 */
object Argon2Kdf {

    private val secureRandom = SecureRandom()

    fun generateSalt(lengthBytes: Int = Argon2Params.SALT_LENGTH_BYTES): ByteArray {
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun deriveKey(
        masterPassword: CharArray,
        salt: ByteArray,
        params: Argon2Params = Argon2Params(),
        outputLengthBytes: Int = Argon2Params.KEY_LENGTH_BYTES
    ): ByteArray {
        require(masterPassword.isNotEmpty()) { "Master password must not be empty" }
        require(salt.size >= 8) { "Salt must be at least 8 bytes" }
        require(params.memoryKb >= 1024) { "Argon2 memory must be >= 1024 KB" }
        require(params.iterations >= 1) { "Argon2 iterations must be >= 1" }
        require(params.parallelism >= 1) { "Argon2 parallelism must be >= 1" }

        val argon2Params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKb)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .build()

        val output = ByteArray(outputLengthBytes)
        val generator = Argon2BytesGenerator()
        generator.init(argon2Params)
        generator.generateBytes(masterPassword, output)
        return output
    }
}

fun ByteArray.wipe() {
    fill(0)
}

fun CharArray.wipe() {
    fill('\u0000')
}
