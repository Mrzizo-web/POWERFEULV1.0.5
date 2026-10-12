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
        val text = body.trim()
        return text.startsWith("اضيف ") || text.startsWith("خصم ")
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        val text = body.trim()

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
        body.trim().startsWith("استلمت حوالة من ")

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        val match = incoming.matcher(body.trim())
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

    private val purchases = Pattern.compile(
        """^لقد استلمت\s+(?<currency>\S+)\s+(?<amount>\d+)\s+كقيمة مشتريات بمرجع\s+(?<reference>\d+)\s+من\s+(?<sender>.+)$"""
    )
    private val directReceipt = Pattern.compile(
        """^استلمت مبلغ\s+(?<currency>\S+)\s+(?<amount>\d+)\s+من\s+(?<senderId>\d+)\s+رصيدك هو\s+(?<balance>\d+)$"""
    )

    override fun canParse(sender: String, body: String): Boolean {
        val text = body.trim()
        return text.startsWith("لقد استلمت ") || text.startsWith("استلمت مبلغ ")
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        val text = body.trim()

        purchases.matcher(text).takeIf { it.matches() }?.let { match ->
            val amount = match.group("amount")!!.toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return ParsedWalletSms(walletCode, amount, match.group("reference")!!, match.group("sender")!!.trim(), true, WalletDirection.INCOMING)
        }

        directReceipt.matcher(text).takeIf { it.matches() }?.let { match ->
            val amount = match.group("amount")!!.toDoubleOrNull() ?: return null
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
