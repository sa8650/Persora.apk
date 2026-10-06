package app.persora.android.core.util

/** Card-number helpers shared by the wallet editor and tiles. Mirrors detectCardNetwork / formatExpiryInput in VaultDialogs.tsx. */
object Cards {
    val networks = listOf("Visa", "Mastercard", "American Express", "UnionPay", "Discover", "Other")
    const val MAX_DIGITS = 19

    fun digits(value: String): String = value.filter { it.isDigit() }.take(MAX_DIGITS)

    /** "4111111111111111" → "4111 1111 1111 1111" (Amex groups 4-6-5). */
    fun formatNumber(value: String): String {
        val d = digits(value)
        val groups = if (detectNetwork(d) == "American Express") listOf(4, 6, 5) else listOf(4, 4, 4, 4, 3)
        val out = StringBuilder(); var i = 0
        for (g in groups) { if (i >= d.length) break; if (out.isNotEmpty()) out.append(' '); out.append(d, i, minOf(d.length, i + g)); i += g }
        return out.toString()
    }

    fun detectNetwork(number: String): String {
        val d = number.filter { it.isDigit() }
        val two = d.take(2).toIntOrNull() ?: -1
        val four = d.take(4).toIntOrNull() ?: -1
        val six = d.take(6).toIntOrNull() ?: -1
        return when {
            d.isEmpty() -> ""
            d.startsWith("4") -> "Visa"
            d.startsWith("34") || d.startsWith("37") -> "American Express"
            two in 51..55 || four in 2221..2720 -> "Mastercard"
            d.startsWith("6011") || d.startsWith("65") || d.take(3).toIntOrNull() in 644..649 || (d.startsWith("622") && six in 622126..622925) -> "Discover"
            d.startsWith("62") -> "UnionPay"
            d.length >= 2 -> "Other"
            else -> ""
        }
    }

    fun passesLuhn(number: String): Boolean {
        val d = number.filter { it.isDigit() }
        if (d.length !in 13..19) return false
        var sum = 0; var double = false
        for (i in d.indices.reversed()) { var n = d[i] - '0'; if (double) { n *= 2; if (n > 9) n -= 9 }; sum += n; double = !double }
        return sum % 10 == 0
    }

    fun lastFour(number: String): String = number.filter { it.isDigit() }.takeLast(4)

    /** Keeps typed expiry as MM/YY: digits only, max 4, slash inserted after the month; "1" + "3" → "01/3". */
    fun formatExpiry(value: String): String {
        var d = value.filter { it.isDigit() }.take(4)
        if (d.length == 1 && d[0] in '2'..'9') d = "0$d"
        if (d.length >= 2) { val mm = d.take(2).toInt(); if (mm == 0) d = "01" + d.drop(2) else if (mm > 12) d = "0" + d[0] + d.drop(1).take(2) }
        return if (d.length > 2) d.take(2) + "/" + d.drop(2) else d
    }

    /** Splits "MM/YY" (or MM/YYYY) into (MM, YYYY). */
    fun splitExpiry(expiry: String): Pair<String, String>? {
        val m = Regex("^(0[1-9]|1[0-2])\\s*/\\s*(\\d{2}|\\d{4})$").find(expiry.trim()) ?: return null
        val month = m.groupValues[1]; val year = m.groupValues[2].let { if (it.length == 2) "20$it" else it }
        return month to year
    }

    fun savedExpiry(month: String?, year: String?): String = listOfNotNull(month?.takeIf { it.isNotBlank() }, year?.takeIf { it.isNotBlank() }?.takeLast(2)).joinToString("/")
}
