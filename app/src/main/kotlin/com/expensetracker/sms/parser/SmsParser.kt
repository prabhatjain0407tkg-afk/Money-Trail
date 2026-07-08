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

    // Transaction-context keywords — presence of any of these means the SMS is a real bank
    // transaction even if it also contains a URL (e.g. Kotak fraud-report link "Not you, https://...")
    // NOTE: Use specific phrases, NOT bare "credited"/"debited" — promotional SMS deliberately
    // write "Rs.X credited!" or "Rs.X debited" to look like real transactions.
    private val TRANSACTION_CONTEXT_KEYWORDS = listOf(
        "upi ref", "imps ref", "neft ref", "rtgs ref",
        "debited from",           // "debited from your account" — real bank phrasing
        "credited to",            // "credited to your account / a/c" — real bank phrasing
        "sent rs", "sent inr", "sent ₹",
        "received rs", "received inr", "received ₹",
        "avl bal", "avail bal",   // available balance — only real bank SMS include this
        "txn id", "ref no",       // transaction reference — promos never include these
        "withdrawn", "deposited",
    )

    // Matches both full URLs (https://...) and bare shortener links (bit.ly/... cutt.ly/... etc.)
    private val URL_PATTERN = Regex(
        """https?://\S+|(?:bit|cutt|goo|tinyurl|ow|rb|is|su|t|shorturl)\.(?:ly|gl|co|me|in)/\S+""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String): ParsedSms? {
        val normalizedSender = sender.trim().uppercase()
        val normalizedBody = body.trim()
        val bodyLower = normalizedBody.lowercase()

        // Route by message type first (see SmsClassifier). This replaces the old
        // scattered reject-lists: statements, OTPs, offers, collect-requests, biller
        // acknowledgements and store-credit are all recognised here up front.
        val messageType = SmsClassifier.classify(normalizedBody)

        // Hard ignore: OTP / collect-request / promo / card-control — never a
        // transaction, so drop before any template even looks at it.
        if (messageType.isHardIgnore) return null

        // Reject SMS that contain a URL but have NO transaction context keywords.
        // Real bank SMS (Kotak, HDFC) may include a fraud-report link alongside the transaction —
        // those are kept because they also contain "debited"/"UPI Ref"/etc.
        // Purely promotional SMS (Zype, loan offers) have a URL and zero transaction keywords.
        if (URL_PATTERN.containsMatchIn(normalizedBody) &&
            TRANSACTION_CONTEXT_KEYWORDS.none { bodyLower.contains(it) }) return null

        val typeHint = messageType.toTxTypeHint()

        // 1. Sender-matched templates first (faster, higher confidence)
        val senderMatched = BankTemplates.ALL.filter { template ->
            template.senderPatterns.any { it.containsMatchIn(normalizedSender) }
        }

        val senderResult = senderMatched
            .firstNotNullOfOrNull { template ->
                tryTemplate(template, normalizedBody, senderMatched = true, typeHint)
            }
        if (senderResult != null) return senderResult

        // 2. Try all templates by body content (sender not recognized)
        val bodyResult = BankTemplates.ALL
            .firstNotNullOfOrNull { template ->
                tryTemplate(template, normalizedBody, senderMatched = false, typeHint)
            }
        if (bodyResult != null) return bodyResult

        // 3. No real bank template matched. Soft ignore — statement / biller-ack /
        //    store-credit — is applied only now, right before the loose generic
        //    fallback, so a genuine transaction (which matched a template above) is
        //    never dropped; only fake-income manufacturing from the fallback is stopped.
        if (messageType.isSoftIgnore) return null

        // 4. Generic heuristic fallback
        return tryTemplate(BankTemplates.GENERIC_FALLBACK, normalizedBody, senderMatched = false, typeHint)
    }

    private fun tryTemplate(
        template: BankTemplate,
        body: String,
        senderMatched: Boolean,
        typeHint: TxType? = null
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

            // Type resolution order: template's own guarantee → the type word the
            // template captured → the classifier's hint → keyword-count inference.
            val type = when {
                template.isAlwaysDebit -> TxType.DEBIT
                template.isAlwaysCredit -> TxType.CREDIT
                else -> match.namedGroup("type")?.toTxType() ?: typeHint ?: inferType(body)
            }

            // Try template-extracted merchant first, then fall back to body scan.
            val merchant = (match.namedGroup("merchant")
                ?.normalizeMerchant()
                ?.takeIf { it.isNotBlank() })
                ?: extractMerchantFromBody(body)

            val acct = match.namedGroup("acct")?.trim()
            // Template <bal>/<ref> groups sit after a lazy .*? at the END of most
            // patterns — a trailing optional group there never participates in the
            // match (the lazy quantifier stops as soon as the mandatory part
            // succeeds). Fall back to a dedicated second-pass scan of the body.
            val bal  = match.namedGroup("bal")?.parseAmount() ?: extractBalanceFromBody(body)
            val ref  = match.namedGroup("ref")?.trim() ?: extractRefFromBody(body)

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
            // "beneficiary is MERCHANT" / "beneficiary: MERCHANT" / "beneficiary MERCHANT" (NEFT salary credit)
            Regex("""beneficiary\s*:?\s*(?:is\s+)?([A-Z0-9][A-Z0-9 &]{2,35}?)(?:\.|,|\s{2}|UPI|\s*$)""", RegexOption.IGNORE_CASE),
            // "NEFT/IMPS/RTGS from MERCHANT" / "NEFT by MERCHANT" (salary / inward remittance)
            Regex("""(?:NEFT|IMPS|RTGS)\s+(?:from|by)\s+([A-Z][A-Z0-9 &]{2,35}?)(?:\.|,|\s{2}|\s*$)""", RegexOption.IGNORE_CASE),
            // "credited by MERCHANT" (some bank NEFT credit formats)
            Regex("""credited\s+by\s+([A-Z][A-Z0-9 &]{2,35}?)(?:\.|,|\s{2}|\s*$)""", RegexOption.IGNORE_CASE),
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

    /** "Avl Bal Rs.12050.00", "Avbl. Bal.: INR596.50", "Current Balance: INR 12,050.00" */
    private val BALANCE_PATTERN = Regex(
        """(?:avl|avbl|avail(?:able)?|current|updated)?\.?\s*\bbal(?:ance)?\.?\s*[:\-]?\s*(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private fun extractBalanceFromBody(body: String): Double? =
        BALANCE_PATTERN.find(body)?.groupValues?.get(1)?.parseAmount()

    /** "UPI Ref. No. is 512345678901", "Ref No.: 123456789012", "IMPS Ref: 987654" */
    private val REF_PATTERN = Regex(
        """(?:upi|imps|neft|rtgs|txn)?\s*\bref(?:erence)?\.?\s*(?:no\.?)?\s*(?:is\s+)?[:\-]?\s*(\d{6,18})""",
        RegexOption.IGNORE_CASE
    )

    private fun extractRefFromBody(body: String): String? =
        REF_PATTERN.find(body)?.groupValues?.get(1)

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
        contains("deducted", ignoreCase = true) ||
        equals("Dr", ignoreCase = true) -> TxType.DEBIT  // PSU bank abbreviation in <type> group

        // Use past-tense "credited" (verb), NOT "credit" (noun — appears in "Credit Card", "Credit Limit")
        contains("credited", ignoreCase = true) ||
        contains("received", ignoreCase = true) ||
        contains("deposited", ignoreCase = true) ||   // salary / cash / cheque deposit = money IN
        contains("refund", ignoreCase = true) ||
        contains("cashback", ignoreCase = true) ||
        equals("Cr", ignoreCase = true) -> TxType.CREDIT  // PSU bank abbreviation in <type> group

        else -> null
    }

    private fun inferType(body: String): TxType {
        val lower = body.lowercase()
        val debitScore = listOf("debited", "deducted", "spent", "paid", "payment", "withdrawn", " dr ")
            .count { lower.contains(it) }
        // "credited" (verb) only — not "credit card", "credit limit", "credit score"
        val creditScore = listOf("credited", "received", "deposited", "refund", "cashback", "salary", " cr ")
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
            .replace(Regex("""^BENEFICIARY\s+""", RegexOption.IGNORE_CASE), "")  // "BENEFICIARY DATAMETICA..." → "DATAMETICA..."
            .replace(Regex("""@[a-z0-9]+"""), "")   // remove VPA domain
            .replace(Regex("""(Pvt|Ltd|LLP|Inc|Corp)\.?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .uppercase()
            .take(40)
    }
}
