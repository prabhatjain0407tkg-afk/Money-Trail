package com.expensetracker.sms.parser

import com.expensetracker.sms.model.DetectionResult
import kotlin.math.exp

/**
 * Scores how strongly an SMS looks like a REAL bank transaction, producing an
 * explainable [DetectionResult] (confidence / score / reasons / hasFingerprint).
 *
 * This is the "positive-evidence" defence: instead of maintaining an endless
 * blocklist of promo brands (Bewakoof, Jio, Pantaloons, …), we require genuine
 * transaction structure. Real Indian bank SMS are compliance-bound to carry a
 * masked account/card, a reference number, or an available balance; promotional
 * and notification SMS carry none. So [DetectionResult.hasFingerprint] gates the
 * loose generic fallback and catches the entire class of fake "Rs.X credited"
 * messages, present and future, with no per-brand code.
 */
object TransactionDetector {

    // ── Structural fingerprints (each also flips hasFingerprint = true) ─────────
    private val MASKED_NUMBER = Regex("""[X*x]{2,}\s?\d{3,4}\b""")            // XX1234, ****1234, XXXXXXXX8438
    private val CARD_ENDING   = Regex("""ending\s+\d{3,4}""", RegexOption.IGNORE_CASE)
    private val REFERENCE = Regex(
        """(?:upi\s*ref|imps\s*ref|neft\s*ref|rtgs\s*ref|ref\s*no|ref\.|reference\s*no|txn\s*id|transaction\s*id|utr)[\s:.\-]{0,6}\w*\d{6,}""",
        RegexOption.IGNORE_CASE
    )
    private val BALANCE = Regex(
        """\b(?:avl|avbl|avbi|avail(?:able)?|current)\.?\s*bal(?:ance)?""",
        RegexOption.IGNORE_CASE
    )

    // ── Supporting signals (raise the score but are not fingerprints) ───────────
    private val AMOUNT = Regex("""(?:INR|Rs\.?|₹)\s*[\d,]+(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val FINANCIAL_VERBS = listOf(
        "spent", "debited", "credited", "deposited", "withdrawn",
        "deducted", "paid", "sent", "received", "purchase",
    )

    // ── Negative signals (lower the score / confidence) ─────────────────────────
    private val PROMO = listOf(
        Regex("""apply\s+now""",            RegexOption.IGNORE_CASE),
        Regex("""use\s+it\s+to\s+shop""",   RegexOption.IGNORE_CASE),
        Regex("""shop\s+now""",             RegexOption.IGNORE_CASE),
        Regex("""up\s+to\s+(?:rs|inr|₹)""", RegexOption.IGNORE_CASE),
        Regex("""reward\s+points?""",       RegexOption.IGNORE_CASE),
    )
    private val STORE_CREDIT = Regex(
        """(?:credited|added)\s+to\s+your\s+(?!(?:bank|a/?c|account|acct|sb|savings|current|ppf|nps|loan|deposit|demat|salary)\b)[a-z0-9]+\s+(?:account|wallet)""",
        RegexOption.IGNORE_CASE
    )

    // Signal weights.
    private const val W_SENDER    = 10
    private const val W_ACCOUNT   = 8
    private const val W_REFERENCE = 7
    private const val W_BALANCE   = 6
    private const val W_VERB      = 5
    private const val W_AMOUNT    = 3
    private const val W_PROMO     = -12
    private const val W_STORE     = -12

    fun detect(sender: String, body: String): DetectionResult {
        var score = 0
        val reasons = mutableListOf<String>()
        var fingerprint = false

        if (isTrustedBankSender(sender)) {
            score += W_SENDER; reasons += "Trusted bank sender"; fingerprint = true
        }
        if (MASKED_NUMBER.containsMatchIn(body) || CARD_ENDING.containsMatchIn(body)) {
            score += W_ACCOUNT; reasons += "Card/account number found"; fingerprint = true
        }
        if (REFERENCE.containsMatchIn(body)) {
            score += W_REFERENCE; reasons += "Transaction reference found"; fingerprint = true
        }
        if (BALANCE.containsMatchIn(body)) {
            score += W_BALANCE; reasons += "Available balance present"; fingerprint = true
        }
        FINANCIAL_VERBS.firstOrNull { body.contains(it, ignoreCase = true) }?.let { verb ->
            score += W_VERB; reasons += "Financial verb '$verb'"
        }
        if (AMOUNT.containsMatchIn(body)) {
            score += W_AMOUNT; reasons += "Amount present"
        }
        if (PROMO.any { it.containsMatchIn(body) }) {
            score += W_PROMO; reasons += "Promotional language present"
        }
        if (STORE_CREDIT.containsMatchIn(body)) {
            score += W_STORE; reasons += "Store-credit phrasing present"
        }

        // Smooth 0..1 confidence: ~0.5 at a bare amount+verb, saturating near 1.0
        // once several fingerprints stack up.
        val raw = 1.0 / (1.0 + exp(-0.13 * (score - 6.5)))
        val confidence = (Math.round(raw * 100.0) / 100.0).coerceIn(0.0, 1.0)

        return DetectionResult(
            confidence = confidence,
            score = score,
            reasons = reasons,
            hasFingerprint = fingerprint,
        )
    }

    /** True when the sender matches any known bank/UPI DLT header from BankTemplates. */
    private fun isTrustedBankSender(sender: String): Boolean {
        val s = sender.uppercase()
        return BankTemplates.ALL.any { t -> t.senderPatterns.any { it.containsMatchIn(s) } }
    }
}
