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
     * HARD rejects — SMS that are never a real bank transaction (balance/statement
     * notifications, OTPs, collect-requests, and outright promotional spam).
     * Checked up front, before any parsing: even if such a message superficially
     * resembles a bank template, it must not become a transaction.
     */
    private val HARD_REJECT_PATTERNS = listOf(
        // Balance / statement notifications
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
        // UPI payment-request notifications — money NOT yet moved, pending user approval.
        Regex("""requested\s+money\s+from\s+you""",  RegexOption.IGNORE_CASE),
        Regex("""will\s+be\s+debited.*on\s+approv""",RegexOption.IGNORE_CASE),
        Regex("""collect\s+request""",               RegexOption.IGNORE_CASE),
        Regex("""payment\s+request.*approv""",       RegexOption.IGNORE_CASE),
        Regex("""approv.*payment\s+request""",       RegexOption.IGNORE_CASE),
        Regex("""has\s+requested\s+(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d+)?\s+from\s+you""", RegexOption.IGNORE_CASE),
        Regex("""raised\s+a\s+collect""",            RegexOption.IGNORE_CASE),
        // Promotional / marketing SMS — "up to Rs.X" is an offer, not a real credit
        Regex("""up\s+to\s+(?:rs\.?|inr|₹)\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""(?:credited|received).*bonus""",     RegexOption.IGNORE_CASE),
        Regex("""bonus.*(?:credited|received)""",     RegexOption.IGNORE_CASE),
        Regex("""available\s+for\s+withdrawal""",     RegexOption.IGNORE_CASE),
        Regex("""apply\s+now""",                     RegexOption.IGNORE_CASE),
        Regex("""instant\s+loan""",                  RegexOption.IGNORE_CASE),
        Regex("""personal\s+loan""",                 RegexOption.IGNORE_CASE),
        Regex("""pre.?approved\s+loan""",            RegexOption.IGNORE_CASE),
        Regex("""loan\s+offer""",                    RegexOption.IGNORE_CASE),
        Regex("""click\s+(?:here|to\s+apply)""",     RegexOption.IGNORE_CASE),
        // Hindi / Hinglish promotional phrases common in Indian promo SMS
        Regex("""apply\s+karein""",                  RegexOption.IGNORE_CASE),
        Regex("""abhi\s+apply""",                    RegexOption.IGNORE_CASE),
        Regex("""app\s+(?:se\s+)?download""",        RegexOption.IGNORE_CASE),
        Regex("""(?:payen|paayein|hasil\s+karein)""",RegexOption.IGNORE_CASE),
    )

    /**
     * SOFT rejects — messages that superficially look like a credit/debit but are
     * really merchant-side acknowledgements or store-credit, NOT money moving in or
     * out of the user's bank account (e.g. JioHome "payment received", Bewakoof
     * "credited to your account").
     *
     * These are checked ONLY after every real bank template has already been tried
     * and failed — i.e. right before the loose GENERIC fallback. A genuine bank
     * transaction always matches a specific bank template first and never reaches
     * this list, so these patterns can't wrongly reject a real transaction; they
     * only stop the generic fallback from manufacturing fake income out of an
     * acknowledgement SMS.
     */
    private val MERCHANT_ACK_PATTERNS = listOf(
        // Credit card bill payment acknowledgement — not income to the bank account.
        Regex("""received\s+towards\s+your.*credit\s+card""", RegexOption.IGNORE_CASE),
        Regex("""payment.*received.*credit\s+card""",         RegexOption.IGNORE_CASE),
        // Biller acknowledgements — the USER paid a bill and the biller confirms it,
        // e.g. JioHome: "Payment of Rs.706.82 for your JioHome connection ... has
        // been received". Money left via the bank's own debit SMS (the real record).
        Regex("""payment\s+of\s+(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?\s+(?:for|towards)\s+your[\s\S]{0,150}?receiv""", RegexOption.IGNORE_CASE),
        Regex("""we\s+have\s+received\s+your\s+payment""",    RegexOption.IGNORE_CASE),
        Regex("""your\s+payment[\s\S]{0,80}?(?:has\s+been|was)\s+received""", RegexOption.IGNORE_CASE),
        // Store-credit / brand-wallet credits — "credited to your Bewakoof account".
        // The negative lookahead lets real bank phrasing through ("your account
        // XX1234", "a/c", PPF/NPS/loan interest credits — all genuine income).
        Regex("""(?:credited|added)\s+to\s+your\s+(?!(?:bank|a/?c|account|acct|sb|savings|current|ppf|nps|loan|deposit|demat|salary)\b)[a-z0-9]+\s+(?:account|wallet)""", RegexOption.IGNORE_CASE),
        Regex("""auto-?applied\s+(?:on|at)\s+checkout""",     RegexOption.IGNORE_CASE),
        Regex("""use\s+it\s+to\s+shop""",                     RegexOption.IGNORE_CASE),
    )

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

        // Hard reject: balance/statement/OTP/collect-request/promo — never a transaction.
        if (HARD_REJECT_PATTERNS.any { it.containsMatchIn(normalizedBody) }) return null

        // Reject SMS that contain a URL but have NO transaction context keywords.
        // Real bank SMS (Kotak, HDFC) may include a fraud-report link alongside the transaction —
        // those are kept because they also contain "debited"/"UPI Ref"/etc.
        // Purely promotional SMS (Zype, loan offers) have a URL and zero transaction keywords.
        if (URL_PATTERN.containsMatchIn(normalizedBody) &&
            TRANSACTION_CONTEXT_KEYWORDS.none { bodyLower.contains(it) }) return null

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

        // 3. No real bank template matched. Only now — right before the loose generic
        //    fallback — reject merchant-side acknowledgements / store-credit that would
        //    otherwise be manufactured into fake income. A genuine bank transaction has
        //    already returned above, so this can't drop a real one.
        if (MERCHANT_ACK_PATTERNS.any { it.containsMatchIn(normalizedBody) }) return null

        // 4. Generic heuristic fallback
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
