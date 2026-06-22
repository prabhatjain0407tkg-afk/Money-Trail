package com.expensetracker.sms.parser

import com.expensetracker.sms.model.Confidence
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.TxType

/**
 * Parses a raw bank/transaction SMS into a [ParsedSms].
 *
 * Algorithm:
 * 1. Find templates whose sender patterns match [sender].
 * 2. For each matching template, try its body patterns in order.
 * 3. If no sender match, try all templates' body patterns in order.
 * 4. If nothing matched, try the generic fallback.
 * 5. Return null if the generic fallback also fails (not a financial SMS).
 *
 * This class is pure Kotlin — no Android SDK dependency — so it can be
 * unit-tested on the JVM directly.
 */
object SmsParser {

    /**
     * SMS patterns that indicate a balance/statement notification rather than a
     * real debit/credit transaction. These are rejected before any parsing.
     * Add more patterns here as you discover false positives.
     */
    private val BALANCE_STATEMENT_PATTERNS = listOf(
        Regex("""passbook\s+balance""",              RegexOption.IGNORE_CASE),
        Regex("""balance\s+against""",               RegexOption.IGNORE_CASE),
        Regex("""your\s+(?:account\s+)?balance\s+(?:is|as\s+of)""", RegexOption.IGNORE_CASE),
        Regex("""account\s+balance\s+(?:is|:)""",   RegexOption.IGNORE_CASE),
        Regex("""closing\s+balance""",               RegexOption.IGNORE_CASE),
        Regex("""mini\s+statement""",                RegexOption.IGNORE_CASE),
        Regex("""account\s+statement""",             RegexOption.IGNORE_CASE),
        Regex("""monthly\s+statement""",             RegexOption.IGNORE_CASE),
        Regex("""credit\s+card\s+statement""",       RegexOption.IGNORE_CASE),
        Regex("""credit\s+(?:score|report)""",       RegexOption.IGNORE_CASE),
        Regex("""your\s+credit\s+limit\s+is""",      RegexOption.IGNORE_CASE),
        Regex("""otp\s+(?:is|for)\s+\d""",           RegexOption.IGNORE_CASE),
    )

    fun parse(sender: String, body: String): ParsedSms? {
        val normalizedSender = sender.trim().uppercase()
        val normalizedBody = body.trim()

        // Reject balance/statement/OTP SMS immediately — not transactions.
        if (BALANCE_STATEMENT_PATTERNS.any { it.containsMatchIn(normalizedBody) }) return null

        // 1. Sender-matched templates first (faster, higher confidence)
        val senderMatched = BankTemplates.ALL.filter { template ->
            template.senderPatterns.any { it.containsMatchIn(normalizedSender) }
        }

        val senderResult = senderMatched
            .firstNotNullOfOrNull { template ->
                tryTemplate(template, normalizedBody, senderMatched = true)
            }
        if (senderResult != null) return senderResult

        // 2. Try all templates by body content (sender not recognized)
        val bodyResult = BankTemplates.ALL
            .firstNotNullOfOrNull { template ->
                tryTemplate(template, normalizedBody, senderMatched = false)
            }
        if (bodyResult != null) return bodyResult

        // 3. Generic heuristic fallback
        return tryTemplate(BankTemplates.GENERIC_FALLBACK, normalizedBody, senderMatched = false)
    }

    private fun tryTemplate(
        template: BankTemplate,
        body: String,
        senderMatched: Boolean
    ): ParsedSms? {
        for (pattern in template.bodyPatterns) {
            val match = pattern.find(body) ?: continue

            val rawAmount = match.namedGroup("amount")?.parseAmount() ?: continue

            // Detect foreign currency (e.g. USD, EUR) via optional <ccy> group.
            // INR / RS are treated as domestic and not flagged.
            val ccyGroup = match.namedGroup("ccy")?.uppercase()
            val isForeign = ccyGroup != null
                && ccyGroup != "INR"
                && ccyGroup != "RS"
                && CurrencyRates.isKnown(ccyGroup)

            val finalAmount   = if (isForeign) CurrencyRates.toInr(rawAmount, ccyGroup!!) else rawAmount
            val foreignCcy    = if (isForeign) ccyGroup else null
            val foreignAmt    = if (isForeign) rawAmount else null

            val type = when {
                template.isAlwaysDebit -> TxType.DEBIT
                template.isAlwaysCredit -> TxType.CREDIT
                else -> match.namedGroup("type")?.toTxType() ?: inferType(body)
            }

            // Try template-extracted merchant first, then fall back to body scan.
            val merchant = (match.namedGroup("merchant")
                ?.normalizeMerchant()
                ?.takeIf { it.isNotBlank() })
                ?: extractMerchantFromBody(body)

            val acct = match.namedGroup("acct")?.trim()
            val bal  = match.namedGroup("bal")?.parseAmount()
            val ref  = match.namedGroup("ref")?.trim()

            val confidence = when {
                senderMatched && merchant != null -> Confidence.HIGH
                merchant != null                 -> Confidence.MEDIUM
                else                             -> Confidence.LOW
            }

            return ParsedSms(
                amount = finalAmount,
                type = type,
                merchant = merchant,
                accountTail = acct,
                availableBalance = bal,
                referenceNo = ref,
                bank = template.bankName,
                rawText = body,
                confidence = confidence,
                foreignCurrency = foreignCcy,
                foreignAmount = foreignAmt
            )
        }
        return null
    }

    /**
     * Second-pass merchant extraction for SMS where the bank template regex
     * did not capture a merchant group. Tries common Indian UPI / bank SMS patterns.
     */
    private fun extractMerchantFromBody(body: String): String? {
        val patterns = listOf(
            // "sent to MERCHANT via UPI" / "paid to MERCHANT" / "payment to VPA name@upi"
            Regex("""(?:sent|paid|payment|transfer)\s+to\s+(?:VPA\s+)?([A-Za-z][A-Za-z0-9 &]{1,30}?)(?:@\w+)?(?:\s+(?:via|Ref|on\b)|\s*[.,])""", RegexOption.IGNORE_CASE),
            // "for payment to MERCHANT" (Axis Bank style)
            Regex("""for\s+payment\s+to\s+(?:VPA\s+)?([A-Za-z][A-Za-z0-9 &]{1,30}?)(?:@\w+)?(?:\s*[.,]|$)""", RegexOption.IGNORE_CASE),
            // "to MERCHANT Ref" / "to MERCHANT UPI" / "to MERCHANT on"
            Regex("""to\s+([A-Z][A-Z0-9 &]{2,30}?)\s+(?:Ref|UPI|via|on\s+\d)""", RegexOption.IGNORE_CASE),
            // "beneficiary is MERCHANT" / "beneficiary MERCHANT"
            Regex("""beneficiary\s+(?:is\s+)?([A-Z0-9][A-Z0-9 &]{2,30}?)(?:\.|,|\s{2}|UPI|\s*$)""", RegexOption.IGNORE_CASE),
            // "Info: UPI/refno/MERCHANTNAME"
            Regex("""Info:\s*UPI/[^/]+/([A-Z][A-Z0-9 ]{2,25})""", RegexOption.IGNORE_CASE),
            // VPA "name@okicici" → extract name before @
            Regex("""(?:to|for)\s+([a-z][a-z0-9]{2,20})@[a-z]+""", RegexOption.IGNORE_CASE),
            // Subscription/streaming: "for Netflix subscription" / "for Hotstar monthly"
            Regex("""for\s+([A-Za-z][A-Za-z0-9+]{2,20})\s+(?:subscription|renewal|monthly|annual|membership|premium|plan)""", RegexOption.IGNORE_CASE),
            // "at MERCHANT on <date>" — cinema / POS terminal style
            Regex("""at\s+([A-Z][A-Z0-9& ]{2,25}?)\s+on\s+\d""", RegexOption.IGNORE_CASE),
            // "MERCHANT charges" / "MERCHANT membership"
            Regex("""([A-Z][A-Z0-9]{2,15})\s+(?:charges|membership|subscription|renewal)""", RegexOption.IGNORE_CASE),
            // "towards MERCHANT" (EMI / insurance SMS style)
            Regex("""towards\s+([A-Za-z][A-Za-z0-9 &]{2,30}?)(?:\.|,|\s{2}|$)""", RegexOption.IGNORE_CASE),
        )
        val stopWords = setOf(
            "YOUR", "YOU", "THE", "THIS", "BANK", "ACCOUNT", "ACCT",
            "CARD", "UPI", "NEFT", "IMPS", "REF", "INR", "AMOUNT",
            "CHARGES", "PAYMENT", "TRANSACTION", "ACCOUNT"
        )
        for (pattern in patterns) {
            val m = pattern.find(body)?.groupValues?.getOrNull(1) ?: continue
            val normalized = m.normalizeMerchant()
            if (normalized.length >= 2 && normalized.uppercase() !in stopWords) return normalized
        }
        return null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun MatchResult.namedGroup(name: String): String? =
        try { groups[name]?.value?.trim()?.takeIf { it.isNotEmpty() } }
        catch (_: IllegalArgumentException) { null }

    private fun String.parseAmount(): Double? {
        val cleaned = replace(",", "").trim()
        return cleaned.toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun String.toTxType(): TxType? = when {
        contains("debited", ignoreCase = true) ||
        contains("spent", ignoreCase = true) ||
        contains("sent", ignoreCase = true) ||
        contains("withdrawn", ignoreCase = true) ||
        contains("deducted", ignoreCase = true) -> TxType.DEBIT

        // Use past-tense "credited" (verb), NOT "credit" (noun — appears in "Credit Card", "Credit Limit")
        contains("credited", ignoreCase = true) ||
        contains("received", ignoreCase = true) ||
        contains("refund", ignoreCase = true) ||
        contains("cashback", ignoreCase = true) -> TxType.CREDIT

        else -> null
    }

    private fun inferType(body: String): TxType {
        val lower = body.lowercase()
        val debitScore = listOf("debited", "deducted", "spent", "paid", "payment", "withdrawn")
            .count { lower.contains(it) }
        // "credited" (verb) only — not "credit card", "credit limit", "credit score"
        val creditScore = listOf("credited", "received", "refund", "cashback", "salary")
            .count { lower.contains(it) }
        return when {
            debitScore > creditScore -> TxType.DEBIT
            creditScore > debitScore -> TxType.CREDIT
            else -> TxType.UNKNOWN
        }
    }

    /**
     * Normalize a raw merchant string extracted from SMS:
     *  - Strip VPA suffixes (@upi, @okicici, etc.)
     *  - Strip trailing noise words
     *  - Title-case
     */
    private fun String.normalizeMerchant(): String {
        return this
            .replace(Regex("""@[a-z0-9]+"""), "")   // remove VPA domain
            .replace(Regex("""(Pvt|Ltd|LLP|Inc|Corp)\.?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .uppercase()
            .take(40)
    }
}
