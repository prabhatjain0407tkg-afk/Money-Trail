package com.expensetracker.sms.model

/**
 * Explainable evidence score for "is this SMS a real bank transaction?".
 *
 * Rather than a single yes/no rule, [com.expensetracker.sms.parser.TransactionDetector]
 * accumulates weighted signals — a trusted bank sender, a masked card/account number,
 * a transaction reference, an available balance, a financial verb, an amount — and
 * subtracts promotional/store-credit signals. The result is:
 *
 *  - [score]        the raw weighted total (higher = more transaction-like)
 *  - [confidence]   score mapped to 0.0..1.0 (a smooth sigmoid)
 *  - [reasons]      the human-readable signals that fired (for debugging/audit)
 *  - [hasFingerprint] whether at least one STRUCTURAL fingerprint was present
 *                   (trusted sender / card-account / reference / balance). Real bank
 *                   SMS always carry one; promos/notifications don't — so this gates
 *                   the loose generic fallback and forms a natural, brand-agnostic
 *                   defence against fake transactions.
 */
data class DetectionResult(
    val confidence: Double,
    val score: Int,
    val reasons: List<String>,
    val hasFingerprint: Boolean,
)
