package com.expensetracker.sms.parser

import com.expensetracker.sms.model.Confidence
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.TxType

/**
 * Secondary, dedicated parser for FASTag/toll SMS.
 *
 * NETC's standard toll-plaza wording ("toll paid from...", "using...FASTag...done at...")
 * doesn't contain any of the debit/credit signal words [SmsParser] and its bank templates
 * look for, so these messages are otherwise silently dropped as non-financial.
 *
 * Rather than special-case this wording inside the general-purpose bank templates, this
 * parser is kept fully independent and is only ever tried as a fallback — AFTER
 * [SmsParser.parse] has already run and rejected the message:
 *
 *   SmsParser.parse(sender, body) ?: TollParser.parse(sender, body)
 *
 * This keeps toll-specific parsing out of the general-purpose parsing/tagging pipeline
 * entirely; callers only see it as an additional chance to recognize a transaction.
 */
object TollParser {

    private const val AMT = """(?:Rs\.?|INR|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)"""
    private const val BAL = """(?:Av(?:b)?l\.?\s*Bal\.?[:\s]*(?:Rs\.?|INR|₹)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?"""

    private val PATTERNS = listOf(
        // "INR 240 toll paid from IDFC FIRST Bank Tag 3XXX3600 for vehicle no. MH14JH5530
        //  at Talegaon Toll Plaza on 04/07/2026 16:32. Avbl. Bal.: INR596.50."
        // Bank-agnostic — the lazy `.*?` between "from" and "Tag" absorbs any bank name,
        // so this also covers SBI/HDFC/ICICI-issued FASTags using the same NETC wording.
        Regex(
            """$AMT\s+toll\s+paid\s+from\s+.*?Tag\s+[\dX*]+\s+for\s+vehicle\s+no\.?\s*[A-Z0-9]+\s+at\s+(?<merchant>[A-Za-z0-9 &.-]+?Toll\s+Plaza)\s+on.*?$BAL""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ),
        // Non-toll FASTag usage (parking, drive-through, mall payment):
        // "INR 30 using IDFC FIRST Bank FASTag 3XXX3600 done at SeasonMall on 04/11/2025 13:53.
        //  Avbl. Bal.: INR 253.0"
        Regex(
            """$AMT\s+using\s+.*?FASTag\s+[\dX*]+\s+done\s+at\s+(?<merchant>[A-Za-z0-9 &.-]+?)\s+on.*?$BAL""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    )

    fun parse(sender: String, body: String): ParsedSms? {
        val normalizedBody = body.trim()

        for (pattern in PATTERNS) {
            val match = pattern.find(normalizedBody) ?: continue

            val amount = match.groups["amount"]?.value
                ?.replace(",", "")?.toDoubleOrNull()
                ?.takeIf { it > 0 } ?: continue

            val merchant = match.groups["merchant"]?.value
                ?.trim()?.uppercase()?.take(40)

            val bal = match.groups["bal"]?.value?.replace(",", "")?.toDoubleOrNull()

            return ParsedSms(
                amount = amount,
                type = TxType.DEBIT,
                merchant = merchant,
                accountTail = null,
                availableBalance = bal,
                referenceNo = null,
                bank = "FASTAG",
                rawText = normalizedBody,
                confidence = if (merchant != null) Confidence.MEDIUM else Confidence.LOW
            )
        }
        return null
    }
}
