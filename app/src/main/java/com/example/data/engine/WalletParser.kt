package com.example.data.engine

import java.util.regex.Pattern

data class ParsedWalletSms(
    val walletCode: String,
    val amount: Double,
    val transactionId: String,
    val sender: String,
    val success: Boolean,
    val direction: WalletDirection = WalletDirection.INCOMING
)

enum class WalletDirection { INCOMING, OUTGOING }

private fun normalizeSmsDigits(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        append(
            when (ch) {
                in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                else -> ch
            }
        )
    }
}

interface WalletParser {
    val walletCode: String
    val walletName: String
    fun canParse(sender: String, body: String): Boolean
    fun parse(sender: String, body: String): ParsedWalletSms?
}

class JaibParser : WalletParser {
    override val walletCode = "JEEB"
    override val walletName = "جيب"

    private val incoming = Pattern.compile(
        """^اضيف\s+(?<amount>\d+(?:\.\d+)?)\s+(?<currency>\S+)\s+(?<type>.+?)\s+رص:(?<balance>\d+(?:\.\d+)?)\S+\s+من\s+(?<senderName>.+?)-(?<senderId>\d+)$"""
    )
    private val outgoing = Pattern.compile(
        """^خصم\s+(?<amount>\d+(?:\.\d+)?)\s+(?<currency>\S+)\s+(?<description>.+?)\s+رص:(?<balance>\d+(?:\.\d+)?)\S+\s+الى\s+(?<recipientId>\d+)\s+(?<recipientName>.+)$"""
    )

    override fun canParse(sender: String, body: String): Boolean {
        val text = normalizeSmsDigits(body.trim())
        return text.startsWith("اضيف ") || text.startsWith("خصم ")
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        val text = normalizeSmsDigits(body.trim())

        incoming.matcher(text).takeIf { it.matches() }?.let { match ->
            val amount = match.group("amount")!!.toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return ParsedWalletSms(walletCode, amount, "", match.group("senderName")!!.trim(), true, WalletDirection.INCOMING)
        }

        outgoing.matcher(text).takeIf { it.matches() }?.let { match ->
            val amount = match.group("amount")!!.toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return ParsedWalletSms(walletCode, amount, "", match.group("recipientName")!!.trim(), true, WalletDirection.OUTGOING)
        }

        return null
    }
}

class FloosakParser : WalletParser {
    override val walletCode = "FLOOSAK"
    override val walletName = "فلوسك"

    private val incoming = Pattern.compile(
        """^استلمت حوالة من (?<senderName>.+?) بمبلغ (?<amount>\d+(?:\.\d+)?) (?<currency>\S+) رصيدك (?<balance>\d+(?:\.\d+)?) (?<balanceCurrency>\S+)$"""
    )

    override fun canParse(sender: String, body: String): Boolean =
        normalizeSmsDigits(body.trim()).startsWith("استلمت حوالة من ")

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        val match = incoming.matcher(normalizeSmsDigits(body.trim()))
        if (!match.matches()) return null

        val amount = match.group("amount")!!.toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        if (match.group("currency") != match.group("balanceCurrency")) return null

        return ParsedWalletSms(walletCode, amount, "", match.group("senderName")!!.trim(), true, WalletDirection.INCOMING)
    }
}

class JawaliParser : WalletParser {
    override val walletCode = "JAWALI"
    override val walletName = "جوالي"

    // Some operator templates begin with "لقد استلمت" and others with
    // "استلمت"; support both actual forms without loosening the transaction fields.
    private val purchases = Pattern.compile(
        """^(?:لقد\\s+)?استلمت\\s+(?<currency>YER|ر\\.ي)\\s+(?<amount>\\d+(?:[.,]\\d+)?)\\s+كقيمة مشتريات بمرجع\\s+(?<reference>\\d+)\\s+من\\s+(?<sender>.+)$""",
        Pattern.CASE_INSENSITIVE
    )
    private val directReceipt = Pattern.compile(
        """^استلمت مبلغ\\s+(?<currency>YER|ر\\.ي)\\s+(?<amount>\\d+(?:[.,]\\d+)?)\\s+من\\s+(?<senderId>\\d+)\\s+رصيدك هو\\s+(?<balance>\\d+(?:[.,]\\d+)?)$""",
        Pattern.CASE_INSENSITIVE
    )

    override fun canParse(sender: String, body: String): Boolean {
        val text = normalizeSmsDigits(body.trim())
        return text.startsWith("لقد استلمت ") ||
            text.startsWith("استلمت YER ", ignoreCase = true) ||
            text.startsWith("استلمت ر.ي ") ||
            text.startsWith("استلمت مبلغ ")
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        val text = normalizeSmsDigits(body.trim())

        purchases.matcher(text).takeIf { it.matches() }?.let { match ->
            val amount = match.group("amount")!!.replace(',', '.').toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return ParsedWalletSms(walletCode, amount, match.group("reference")!!, match.group("sender")!!.trim(), true, WalletDirection.INCOMING)
        }

        directReceipt.matcher(text).takeIf { it.matches() }?.let { match ->
            val amount = match.group("amount")!!.replace(',', '.').toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return ParsedWalletSms(walletCode, amount, "", match.group("senderId")!!, true, WalletDirection.INCOMING)
        }

        return null
    }
}

object WalletParserRegistry {
    private val parsers: List<WalletParser> = listOf(
        JaibParser(),
        FloosakParser(),
        JawaliParser()
    )

    fun parseSms(sender: String, body: String): ParsedWalletSms? {
        val parser = parsers.firstOrNull { it.canParse(sender, body) } ?: return null
        return parser.parse(sender, body)
    }
}
