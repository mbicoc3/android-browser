package com.example.feature.browser.model

import com.example.core.navigation.BrowserRoutes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class OmniboxSmartAnswer(
    val title: String,
    val value: String,
    val subtitle: String
)

enum class OmniboxCommandAction {
    OPEN_ROUTE,
    NEW_INCOGNITO_TAB,
    TOGGLE_DESKTOP_MODE,
    FIND_IN_PAGE,
    OPEN_TABS
}

data class OmniboxSlashCommand(
    val trigger: String,
    val label: String,
    val description: String,
    val action: OmniboxCommandAction,
    val route: String? = null
)

/**
 * Pure Kotlin multipurpose omnibox intelligence:
 * - Instant arithmetic & percentage calculator
 * - Exact unit converter (data size, length, mass, temperature)
 * - Browser slash commands (/downloads, /sitedata, /shield, /bookmarks, etc.)
 * - Image dimension/ratio/megapixel formatting
 */
object OmniboxSmartHelper {

    private val allCommands = listOf(
        OmniboxSlashCommand("/downloads", "Downloads", "Open high-speed download manager", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.DOWNLOADS),
        OmniboxSlashCommand("/sitedata", "Site Data", "Inspect cookies, storage & cache", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.SITE_DATA),
        OmniboxSlashCommand("/shield", "Ad & Tracker Shield", "Manage filter lists & blocking", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.SHIELD),
        OmniboxSlashCommand("/bookmarks", "Bookmarks", "View saved bookmarks", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.BOOKMARKS),
        OmniboxSlashCommand("/history", "History", "View browsing history", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.HISTORY),
        OmniboxSlashCommand("/offline", "Offline Pages", "View saved offline web pages", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.OFFLINE_PAGES),
        OmniboxSlashCommand("/vault", "Privacy Vault", "Open Keystore-encrypted vault", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.PRIVACY_VAULT),
        OmniboxSlashCommand("/settings", "Settings", "Browser & engine preferences", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.SETTINGS),
        OmniboxSlashCommand("/tools", "All Tools", "Open Orbit Tools Hub", OmniboxCommandAction.OPEN_ROUTE, BrowserRoutes.TOOLS_HUB),
        OmniboxSlashCommand("/tabs", "Tabs Switcher", "Switch or manage open tabs", OmniboxCommandAction.OPEN_TABS),
        OmniboxSlashCommand("/private", "Private Tab", "Open a new incognito tab", OmniboxCommandAction.NEW_INCOGNITO_TAB),
        OmniboxSlashCommand("/desktop", "Desktop Mode", "Toggle desktop user-agent for this tab", OmniboxCommandAction.TOGGLE_DESKTOP_MODE),
        OmniboxSlashCommand("/find", "Find in Page", "Search text inside the current page", OmniboxCommandAction.FIND_IN_PAGE)
    )

    fun matchCommands(rawQuery: String, isPageActive: Boolean): List<OmniboxSlashCommand> {
        val trimmed = rawQuery.trim().lowercase(Locale.US)
        if (!trimmed.startsWith("/")) return emptyList()
        val token = trimmed.removePrefix("/")
        return allCommands
            .filter { cmd ->
                val available = isPageActive ||
                    (cmd.action != OmniboxCommandAction.TOGGLE_DESKTOP_MODE && cmd.action != OmniboxCommandAction.FIND_IN_PAGE)
                available && (token.isEmpty() ||
                    cmd.trigger.removePrefix("/").contains(token) ||
                    cmd.label.lowercase(Locale.US).contains(token))
            }
            .take(5)
    }

    fun formatImageSummary(info: DirectImageInfo): String {
        if (!info.hasValidDimensions) return formatMimeLabel(info.mimeType)
        val g = gcd(info.width, info.height)
        val rw = info.width / g
        val rh = info.height / g
        val ratioText = if (rw <= 32 && rh <= 32) {
            "$rw:$rh"
        } else {
            String.format(Locale.US, "%.2f:1", info.width.toDouble() / info.height.toDouble())
        }
        val mp = (info.width.toLong() * info.height.toLong()) / 1_000_000.0
        val mpText = if (mp >= 0.1) String.format(Locale.US, "%.1f MP", mp) else "${info.width * info.height} px"
        return "${info.width} × ${info.height} px · $ratioText · $mpText · ${formatMimeLabel(info.mimeType)}"
    }

    fun formatMimeLabel(mimeType: String): String {
        val sub = mimeType.substringAfter('/', "").substringBefore(';').trim().uppercase(Locale.US)
        return when (sub) {
            "JPEG" -> "JPG"
            "SVG+XML" -> "SVG"
            "" -> "IMAGE"
            else -> sub
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x.coerceAtLeast(1)
    }

    fun evaluateSmartQuery(rawQuery: String): OmniboxSmartAnswer? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("/")) {
            return null
        }

        evaluateUnitConversion(trimmed)?.let { return it }

        val exprInput = if (trimmed.startsWith("=")) trimmed.drop(1).trim() else trimmed
        evaluatePercentagePhrase(exprInput)?.let { return it }

        val hasOperator = exprInput.any { it in charArrayOf('+', '-', '*', '/', '×', '÷', '^', '%') } ||
            exprInput.startsWith("sqrt(", ignoreCase = true)
        if (!hasOperator) return null
        if (exprInput.count { it == '.' } > 0 && !exprInput.any { it in charArrayOf('+', '-', '*', '/', '×', '÷', '^', '%') }) {
            return null
        }

        val normalized = exprInput
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")

        val value = runCatching { ExpressionParser(normalized).parse() }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        val formatted = formatNumber(value)
        if (formatted == normalized) return null
        return OmniboxSmartAnswer(
            title = "Calculator",
            value = formatted,
            subtitle = "$exprInput = $formatted (Tap to copy)"
        )
    }

    private fun evaluatePercentagePhrase(input: String): OmniboxSmartAnswer? {
        val regex = Regex("""^(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(input) ?: return null
        val pct = match.groupValues[1].toDoubleOrNull() ?: return null
        val base = match.groupValues[2].toDoubleOrNull() ?: return null
        val result = (pct / 100.0) * base
        val formatted = formatNumber(result)
        return OmniboxSmartAnswer(
            title = "Calculator",
            value = formatted,
            subtitle = "$pct% of ${formatNumber(base)} = $formatted"
        )
    }

    private fun evaluateUnitConversion(input: String): OmniboxSmartAnswer? {
        val regex = Regex(
            """^([-+]?\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s+(?:to|in)\s+([a-zA-Z]+)$""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.matchEntire(input) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val from = match.groupValues[2].lowercase(Locale.US)
        val to = match.groupValues[3].lowercase(Locale.US)
        if (from == to) return null

        val converted = convertTemperature(amount, from, to)
            ?: convertLinear(amount, from, to, DATA_UNITS)
            ?: convertLinear(amount, from, to, LENGTH_UNITS)
            ?: convertLinear(amount, from, to, MASS_UNITS)
            ?: return null

        val formatted = formatNumber(converted)
        val unitDisplay = to.uppercase(Locale.US)
        return OmniboxSmartAnswer(
            title = "Unit Converter",
            value = "$formatted $unitDisplay",
            subtitle = "${formatNumber(amount)} ${from.uppercase(Locale.US)} = $formatted $unitDisplay"
        )
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double? {
        val fNorm = when (from) {
            "c", "celsius" -> "c"
            "f", "fahrenheit" -> "f"
            "k", "kelvin" -> "k"
            else -> return null
        }
        val tNorm = when (to) {
            "c", "celsius" -> "c"
            "f", "fahrenheit" -> "f"
            "k", "kelvin" -> "k"
            else -> return null
        }
        val celsius = when (fNorm) {
            "c" -> value
            "f" -> (value - 32.0) * 5.0 / 9.0
            else -> value - 273.15
        }
        return when (tNorm) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32.0
            else -> celsius + 273.15
        }
    }

    private fun convertLinear(value: Double, from: String, to: String, table: Map<String, Double>): Double? {
        val fFactor = table[from] ?: return null
        val tFactor = table[to] ?: return null
        return (value * fFactor) / tFactor
    }

    private fun formatNumber(value: Double): String {
        val rounded = value.roundToLong()
        return if (abs(value - rounded) < 1e-9) {
            rounded.toString()
        } else {
            String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }
    }

    private val DATA_UNITS = mapOf(
        "b" to 1.0,
        "kb" to 1024.0,
        "mb" to 1024.0 * 1024.0,
        "gb" to 1024.0 * 1024.0 * 1024.0,
        "tb" to 1024.0 * 1024.0 * 1024.0 * 1024.0
    )

    private val LENGTH_UNITS = mapOf(
        "mm" to 0.001,
        "cm" to 0.01,
        "m" to 1.0,
        "km" to 1000.0,
        "in" to 0.0254,
        "ft" to 0.3048,
        "yd" to 0.9144,
        "mi" to 1609.344
    )

    private val MASS_UNITS = mapOf(
        "mg" to 0.001,
        "g" to 1.0,
        "kg" to 1000.0,
        "oz" to 28.349523125,
        "lb" to 453.59237,
        "lbs" to 453.59237
    )

    private class ExpressionParser(private val src: String) {
        private var pos = -1
        private var ch = -1

        private fun nextChar() {
            ch = if (++pos < src.length) src[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            require(pos >= src.length) { "Unexpected char" }
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parseFactor()
                    eat('/'.code) -> {
                        val d = parseFactor()
                        require(d != 0.0) { "Division by zero" }
                        x / d
                    }
                    eat('%'.code) -> x % parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                require(eat(')'.code)) { "Missing closing parenthesis" }
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = src.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val func = src.substring(startPos, pos).lowercase(Locale.US)
                require(eat('('.code)) { "Expected (" }
                val arg = parseExpression()
                require(eat(')'.code)) { "Expected )" }
                x = when (func) {
                    "sqrt" -> sqrt(arg)
                    else -> throw IllegalArgumentException("Unknown function: $func")
                }
            } else {
                throw IllegalArgumentException("Unexpected token")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
