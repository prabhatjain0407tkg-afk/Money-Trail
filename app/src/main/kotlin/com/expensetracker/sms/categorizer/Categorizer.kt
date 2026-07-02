package com.expensetracker.sms.categorizer

import com.expensetracker.sms.model.Category
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.TxType

/**
 * Assigns a [Category] to a [ParsedSms] using:
 *  1. User merchant overrides — by merchant name (exact then partial)
 *  1a. User keyword overrides — by keyword found in raw SMS body (for null-merchant transactions)
 *  2. CC-payment / CASH / Investment / Utility priority keywords
 *  3. Seed merchant keyword rules ([CategoryRules.MERCHANT_RULES])
 *  4. Fallback body keyword rules ([CategoryRules.BODY_RULES])
 *  5. [Category.INCOME] for CREDIT transactions with no other match
 *  6. [Category.UNCATEGORIZED] as last resort
 *
 * [userOverrides] maps a normalized MERCHANT NAME (uppercase) to a user-chosen category.
 * [keywordOverrides] maps a lowercase KEYWORD to a category; matched against the full SMS body.
 * Both are loaded from SharedPreferences; this class is stateless.
 */
class Categorizer(
    private val userOverrides: Map<String, Category> = emptyMap(),
    private val keywordOverrides: Map<String, Category> = emptyMap()
) {

    fun categorize(sms: ParsedSms): Category {
        val merchant = sms.merchant?.uppercase()?.trim()

        // 1. User merchant override — exact match first, then partial
        if (merchant != null) {
            userOverrides[merchant]?.let { return it }
            userOverrides.entries
                .firstOrNull { (key, _) -> merchant.contains(key, ignoreCase = true) }
                ?.let { return it.value }
        }

        val rawLower = sms.rawText.lowercase()

        // 1a. User keyword override — body search (used when merchant couldn't be extracted)
        if (keywordOverrides.isNotEmpty()) {
            keywordOverrides.entries
                .firstOrNull { (key, _) -> rawLower.contains(key) }
                ?.let { return it.value }
        }

        // 1b. Credit card bill payment — checked first so a CC payment SMS is never
        //     misclassified as BILLS (utility) or left as UNCATEGORIZED.
        if (CategoryRules.CC_PAYMENT_PRIORITY_KEYWORDS.any { rawLower.contains(it) }) {
            return Category.CC_PAYMENT
        }

        // 1c. ATM / cash withdrawal — before investment so a cash advance isn't misclassified.
        if (CategoryRules.CASH_PRIORITY_KEYWORDS.any { rawLower.contains(it) }) {
            return Category.CASH
        }

        // 1d. Investment priority — NACH-MUT, SIP, MF, FD, RD, PPF, NPS, demat, IPO, etc.
        //     Checked before utility so a MF premium SMS doesn't fall into BILLS.
        if (CategoryRules.INVESTMENT_PRIORITY_KEYWORDS.any { rawLower.contains(it) }) {
            return Category.INVESTMENT
        }

        // 1e. Loan EMI — NACH mandates for loans, instalment phrases, NBFC lender names.
        //     Debit-only guard: loan disbursements arrive as CREDITs and must not be rerouted here.
        if (sms.type != TxType.CREDIT &&
            CategoryRules.EMI_PRIORITY_KEYWORDS.any { rawLower.contains(it) }) {
            return Category.BILLS
        }

        // 1f. Utility priority — electricity, gas, water, insurance, DTH, etc.
        if (CategoryRules.UTILITY_PRIORITY_KEYWORDS.any { rawLower.contains(it) }) {
            return Category.BILLS
        }

        // 2. Merchant keyword rules
        if (merchant != null) {
            matchKeywordRules(merchant, CategoryRules.MERCHANT_RULES)
                ?.let { return it }
        }

        // 3. Search the full SMS body against ALL merchant keyword rules.
        //    This catches cases where the merchant wasn't extracted cleanly but
        //    the keyword (e.g. "SWIGGY", "UBER") still appears in the raw text.
        matchKeywordRules(sms.rawText, CategoryRules.MERCHANT_RULES)
            ?.let { return it }

        // 4. Broader body-specific fallback rules
        matchKeywordRules(sms.rawText, CategoryRules.BODY_RULES)
            ?.let { return it }

        // 5. CREDIT with no match → income by default
        if (sms.type == TxType.CREDIT) return Category.INCOME

        return Category.UNCATEGORIZED
    }

    private fun matchKeywordRules(
        text: String,
        rules: List<Pair<List<String>, Category>>
    ): Category? {
        val lower = text.lowercase()
        return rules.firstOrNull { (keywords, _) ->
            keywords.any { lower.contains(it) }
        }?.second
    }
}
