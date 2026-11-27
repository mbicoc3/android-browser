package com.example.feature.passwords.domain

import java.security.SecureRandom
import kotlin.math.log2

data class PasswordGeneratorOptions(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = true
)

enum class PasswordStrength(val label: String, val scorePercent: Int) {
    WEAK("Weak", 25),
    FAIR("Fair", 50),
    GOOD("Good", 75),
    STRONG("Strong", 100)
}

data class PasswordStrengthReport(
    val strength: PasswordStrength,
    val entropyBits: Int,
    val reasons: List<String>
)

/**
 * Cryptographically random password generator and local-only password strength estimator.
 */
object PasswordGenerator {

    private val secureRandom = SecureRandom()

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private val AMBIGUOUS = setOf('0', 'O', 'o', '1', 'l', 'I', '|')

    fun generate(options: PasswordGeneratorOptions = PasswordGeneratorOptions()): String {
        val targetLength = options.length.coerceIn(8, 64)
        val pools = mutableListOf<String>()

        if (options.includeUppercase) pools += filterAmbiguous(UPPER, options.excludeAmbiguous)
        if (options.includeLowercase) pools += filterAmbiguous(LOWER, options.excludeAmbiguous)
        if (options.includeDigits) pools += filterAmbiguous(DIGITS, options.excludeAmbiguous)
        if (options.includeSymbols) pools += filterAmbiguous(SYMBOLS, options.excludeAmbiguous)

        if (pools.isEmpty()) {
            pools += filterAmbiguous(LOWER, options.excludeAmbiguous)
            pools += filterAmbiguous(DIGITS, options.excludeAmbiguous)
        }

        val combined = pools.joinToString("")
        val chars = CharArray(targetLength)
        var cursor = 0

        // Guarantee at least one character from each enabled pool
        for (pool in pools) {
            if (cursor < targetLength && pool.isNotEmpty()) {
                chars[cursor++] = pool[secureRandom.nextInt(pool.length)]
            }
        }

        while (cursor < targetLength) {
            chars[cursor++] = combined[secureRandom.nextInt(combined.length)]
        }

        // Fisher-Yates shuffle with SecureRandom
        for (i in chars.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }

        return String(chars)
    }

    fun evaluateStrength(password: String): PasswordStrengthReport {
        if (password.isEmpty()) {
            return PasswordStrengthReport(PasswordStrength.WEAK, 0, listOf("Empty password"))
        }

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var charsetSize = 0
        if (hasLower) charsetSize += 26
        if (hasUpper) charsetSize += 26
        if (hasDigit) charsetSize += 10
        if (hasSymbol) charsetSize += 24
        if (charsetSize == 0) charsetSize = 26

        val uniqueChars = password.toSet().size
        val repetitionPenalty = if (uniqueChars < password.length / 2) 0.72 else 1.0
        val rawEntropy = password.length * log2(charsetSize.toDouble()) * repetitionPenalty
        val entropyBits = rawEntropy.toInt().coerceAtLeast(0)

        val reasons = mutableListOf<String>()
        if (password.length < 10) reasons += "Use at least 10 characters"
        if (!hasUpper || !hasLower) reasons += "Mix uppercase & lowercase"
        if (!hasDigit) reasons += "Add numbers"
        if (!hasSymbol) reasons += "Add symbols"
        if (uniqueChars <= 4 && password.length >= 6) reasons += "Avoid repeated characters"

        val commonWeak = setOf(
            "password", "123456", "12345678", "qwerty", "admin", "letmein",
            "welcome", "111111", "abc123", "iloveyou", "passw0rd"
        )
        val isCommon = password.lowercase() in commonWeak

        val strength = when {
            isCommon || password.length < 8 || entropyBits < 36 -> PasswordStrength.WEAK
            entropyBits < 52 -> PasswordStrength.FAIR
            entropyBits < 70 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }

        return PasswordStrengthReport(
            strength = strength,
            entropyBits = entropyBits,
            reasons = if (strength == PasswordStrength.STRONG) emptyList() else reasons
        )
    }

    private fun filterAmbiguous(source: String, exclude: Boolean): String {
        if (!exclude) return source
        return source.filterNot { it in AMBIGUOUS }
    }
}
