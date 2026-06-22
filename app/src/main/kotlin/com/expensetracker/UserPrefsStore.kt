package com.expensetracker

import android.content.Context
import com.expensetracker.sms.model.Category
import com.expensetracker.sms.model.SubCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent key-value store for user-defined category overrides.
 *
 * Two buckets:
 *  - merchantOverrides  → keyed by MERCHANT NAME (uppercase).
 *    Matched against the extracted merchant field of a ParsedSms.
 *  - keywordOverrides   → keyed by a body KEYWORD (lowercase).
 *    Matched against the full raw SMS body.
 *    Used when merchant is null and user provides a keyword to remember.
 */
object UserPrefsStore {

    private const val MERCHANT_PREFS    = "merchant_overrides"
    private const val KEYWORD_PREFS     = "keyword_overrides"
    private const val SUBCATEGORY_PREFS = "subcategory_overrides"
    private const val ONETIME_PREFS     = "onetime_overrides"

    // ── Main category overrides ────────────────────────────────────────────────

    fun saveMerchant(context: Context, merchant: String, category: Category) {
        context.getSharedPreferences(MERCHANT_PREFS, Context.MODE_PRIVATE)
            .edit().putString(merchant.uppercase().trim(), category.name).apply()
    }

    fun saveKeyword(context: Context, keyword: String, category: Category) {
        context.getSharedPreferences(KEYWORD_PREFS, Context.MODE_PRIVATE)
            .edit().putString(keyword.lowercase().trim(), category.name).apply()
    }

    fun loadMerchants(context: Context): Map<String, Category> =
        loadCategory(context, MERCHANT_PREFS)

    fun loadKeywords(context: Context): Map<String, Category> =
        loadCategory(context, KEYWORD_PREFS)

    fun removeMerchant(context: Context, merchant: String) {
        context.getSharedPreferences(MERCHANT_PREFS, Context.MODE_PRIVATE)
            .edit().remove(merchant.uppercase().trim()).apply()
    }

    fun removeKeyword(context: Context, keyword: String) {
        context.getSharedPreferences(KEYWORD_PREFS, Context.MODE_PRIVATE)
            .edit().remove(keyword.lowercase().trim()).apply()
    }

    // ── Subcategory overrides ──────────────────────────────────────────────────
    // Key: lowercase merchant or keyword → Value: SubCategory.id string

    fun saveSubCategory(context: Context, key: String, subCategoryId: String) {
        context.getSharedPreferences(SUBCATEGORY_PREFS, Context.MODE_PRIVATE)
            .edit().putString(key.lowercase().trim(), subCategoryId).apply()
    }

    fun loadSubCategories(context: Context): Map<String, String> =
        context.getSharedPreferences(SUBCATEGORY_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
            .toMap()

    fun removeSubCategory(context: Context, key: String) {
        context.getSharedPreferences(SUBCATEGORY_PREFS, Context.MODE_PRIVATE)
            .edit().remove(key.lowercase().trim()).apply()
    }

    // ── One-time per-transaction overrides ────────────────────────────────────
    // Key: "${date}_${rawText.hashCode()}"  →  Value: "CATEGORY" or "CATEGORY|subCatId"

    fun saveOneTime(context: Context, txKey: String, category: Category, subCategoryId: String?) {
        val value = if (subCategoryId != null) "${category.name}|$subCategoryId" else category.name
        context.getSharedPreferences(ONETIME_PREFS, Context.MODE_PRIVATE)
            .edit().putString(txKey, value).apply()
    }

    fun loadOneTime(context: Context): Map<String, Pair<Category, String?>> =
        context.getSharedPreferences(ONETIME_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) ->
                val str   = v as? String ?: return@mapNotNull null
                val parts = str.split("|")
                try {
                    val cat      = Category.valueOf(parts[0])
                    val subCatId = parts.getOrNull(1)
                    k to Pair(cat, subCatId)
                } catch (_: Exception) { null }
            }
            .toMap()

    fun removeOneTime(context: Context, txKey: String) {
        context.getSharedPreferences(ONETIME_PREFS, Context.MODE_PRIVATE)
            .edit().remove(txKey).apply()
    }

    // ── UPI ID-based rules ────────────────────────────────────────────────────
    // Key: lowercase UPI VPA (e.g. "vegetables@okicici")  →  Value: "CATEGORY" or "CATEGORY|subCatId"

    private const val UPI_PREFS = "upi_overrides"

    fun saveUpiRule(context: Context, upiId: String, category: Category, subCategoryId: String?) {
        val value = if (subCategoryId != null) "${category.name}|$subCategoryId" else category.name
        context.getSharedPreferences(UPI_PREFS, Context.MODE_PRIVATE)
            .edit().putString(upiId.lowercase().trim(), value).apply()
    }

    fun loadUpiRules(context: Context): Map<String, Pair<Category, String?>> =
        context.getSharedPreferences(UPI_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) ->
                val str   = v as? String ?: return@mapNotNull null
                val parts = str.split("|")
                try {
                    val cat      = Category.valueOf(parts[0])
                    val subCatId = parts.getOrNull(1)
                    k to Pair(cat, subCatId)
                } catch (_: Exception) { null }
            }
            .toMap()

    fun removeUpiRule(context: Context, upiId: String) {
        context.getSharedPreferences(UPI_PREFS, Context.MODE_PRIVATE)
            .edit().remove(upiId.lowercase().trim()).apply()
    }

    // ── Custom category taxonomy ──────────────────────────────────────────────
    // Each custom category is stored as a SubCategory with id "CCAT_{timestamp}".
    // Persisted as JSON array in SharedPreferences "custom_taxonomy".

    private const val CUSTOM_TAXONOMY_PREFS = "custom_taxonomy"
    private const val CUSTOM_CATS_KEY       = "cats"

    fun saveCustomCategory(context: Context, subCat: SubCategory) {
        val prefs = context.getSharedPreferences(CUSTOM_TAXONOMY_PREFS, Context.MODE_PRIVATE)
        val arr   = runCatching {
            JSONArray(prefs.getString(CUSTOM_CATS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") == subCat.id) {
                arr.put(i, subCatToJson(subCat))
                prefs.edit().putString(CUSTOM_CATS_KEY, arr.toString()).apply()
                return
            }
        }
        arr.put(subCatToJson(subCat))
        prefs.edit().putString(CUSTOM_CATS_KEY, arr.toString()).apply()
    }

    fun loadCustomCategories(context: Context): List<SubCategory> {
        val prefs = context.getSharedPreferences(CUSTOM_TAXONOMY_PREFS, Context.MODE_PRIVATE)
        val arr   = runCatching {
            JSONArray(prefs.getString(CUSTOM_CATS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                SubCategory(
                    id          = o.getString("id"),
                    displayName = o.getString("displayName"),
                    icon        = o.optString("icon", "📁"),
                    colorValue  = o.getLong("colorValue")
                )
            }.getOrNull()
        }
    }

    fun deleteCustomCategory(context: Context, id: String) {
        val prefs    = context.getSharedPreferences(CUSTOM_TAXONOMY_PREFS, Context.MODE_PRIVATE)
        val arr      = runCatching {
            JSONArray(prefs.getString(CUSTOM_CATS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") != id) filtered.put(arr.getJSONObject(i))
        }
        prefs.edit().putString(CUSTOM_CATS_KEY, filtered.toString()).apply()
    }

    private fun subCatToJson(s: SubCategory) = JSONObject().apply {
        put("id", s.id); put("displayName", s.displayName)
        put("icon", s.icon); put("colorValue", s.colorValue)
    }

    // ── User profile ──────────────────────────────────────────────────────────
    private const val PROFILE_PREFS = "user_profile"

    fun saveProfile(context: Context, name: String, phone: String, avatarPath: String?) {
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", name)
            .putString("phone", phone)
            .putString("avatar_path", avatarPath)
            .apply()
    }

    fun loadProfile(context: Context): Triple<String, String, String?> {
        val p = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        return Triple(
            p.getString("name", "") ?: "",
            p.getString("phone", "") ?: "",
            p.getString("avatar_path", null)
        )
    }

    fun isProfileSetup(context: Context): Boolean =
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .getString("name", null)?.isNotBlank() == true

    // ── Settlement links ──────────────────────────────────────────────────────
    // Key  : creditTxKey  (the incoming UPI/NEFT transaction)
    // Value: "debitTxKey|description|creditAmountLong"
    //        description = merchant name of the outgoing expense (human-readable)
    // Annotation-only — never mutates expense totals.

    private const val SETTLEMENT_PREFS = "settlements"

    fun saveSettlement(
        context: Context,
        creditTxKey: String,
        debitTxKey: String,
        description: String,
        creditAmount: Double
    ) {
        context.getSharedPreferences(SETTLEMENT_PREFS, Context.MODE_PRIVATE)
            .edit().putString(creditTxKey, "$debitTxKey|$description|${creditAmount.toLong()}").apply()
    }

    /** Returns map of creditTxKey → Triple(debitTxKey, description, creditAmount) */
    fun loadSettlements(context: Context): Map<String, Triple<String, String, Double>> =
        context.getSharedPreferences(SETTLEMENT_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) ->
                val str   = v as? String ?: return@mapNotNull null
                val parts = str.split("|", limit = 3)
                if (parts.size < 3) return@mapNotNull null
                runCatching { k to Triple(parts[0], parts[1], parts[2].toDouble()) }.getOrNull()
            }.toMap()

    fun removeSettlement(context: Context, creditTxKey: String) {
        context.getSharedPreferences(SETTLEMENT_PREFS, Context.MODE_PRIVATE)
            .edit().remove(creditTxKey).apply()
    }

    // ── Custom subcategories (per parent Category) ────────────────────────────
    // Stored separately from top-level custom categories.
    // ID format: "CSUB_${parentCategory.name}_${timestamp}"

    private const val CUSTOM_SUBCATS_PREFS = "custom_subcategories"
    private const val CUSTOM_SUBCATS_KEY   = "list"

    fun saveCustomSubCategory(context: Context, parentCategory: Category, subCat: SubCategory) {
        val prefs = context.getSharedPreferences(CUSTOM_SUBCATS_PREFS, Context.MODE_PRIVATE)
        val arr   = runCatching {
            JSONArray(prefs.getString(CUSTOM_SUBCATS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") == subCat.id) {
                arr.put(i, customSubCatToJson(parentCategory, subCat))
                prefs.edit().putString(CUSTOM_SUBCATS_KEY, arr.toString()).apply()
                return
            }
        }
        arr.put(customSubCatToJson(parentCategory, subCat))
        prefs.edit().putString(CUSTOM_SUBCATS_KEY, arr.toString()).apply()
    }

    fun loadCustomSubCategoriesByParent(context: Context): Map<Category, List<SubCategory>> {
        val prefs = context.getSharedPreferences(CUSTOM_SUBCATS_PREFS, Context.MODE_PRIVATE)
        val arr   = runCatching {
            JSONArray(prefs.getString(CUSTOM_SUBCATS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        val result = mutableMapOf<Category, MutableList<SubCategory>>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o   = arr.getJSONObject(i)
                val cat = Category.valueOf(o.getString("parentCategory"))
                val sub = SubCategory(
                    id          = o.getString("id"),
                    displayName = o.getString("displayName"),
                    icon        = o.optString("icon", "📁"),
                    colorValue  = o.getLong("colorValue")
                )
                result.getOrPut(cat) { mutableListOf() }.add(sub)
            }
        }
        return result
    }

    fun deleteCustomSubCategory(context: Context, id: String) {
        val prefs    = context.getSharedPreferences(CUSTOM_SUBCATS_PREFS, Context.MODE_PRIVATE)
        val arr      = runCatching {
            JSONArray(prefs.getString(CUSTOM_SUBCATS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") != id) filtered.put(arr.getJSONObject(i))
        }
        prefs.edit().putString(CUSTOM_SUBCATS_KEY, filtered.toString()).apply()
    }

    private fun customSubCatToJson(parentCategory: Category, s: SubCategory) = JSONObject().apply {
        put("parentCategory", parentCategory.name)
        put("id",          s.id)
        put("displayName", s.displayName)
        put("icon",        s.icon)
        put("colorValue",  s.colorValue)
    }

    // ── Voided (excluded) transactions ───────────────────────────────────────
    // Stored as a StringSet of txKey values. Voided transactions are excluded
    // from all totals but remain visible in lists (dimmed).

    private const val VOIDED_PREFS = "voided_transactions"
    private const val VOIDED_KEY   = "keys"

    fun voidTransaction(context: Context, txKey: String) {
        val prefs    = context.getSharedPreferences(VOIDED_PREFS, Context.MODE_PRIVATE)
        val existing = HashSet(prefs.getStringSet(VOIDED_KEY, emptySet()) ?: emptySet())
        existing.add(txKey)
        prefs.edit().putStringSet(VOIDED_KEY, existing).apply()
    }

    fun restoreTransaction(context: Context, txKey: String) {
        val prefs    = context.getSharedPreferences(VOIDED_PREFS, Context.MODE_PRIVATE)
        val existing = HashSet(prefs.getStringSet(VOIDED_KEY, emptySet()) ?: emptySet())
        existing.remove(txKey)
        prefs.edit().putStringSet(VOIDED_KEY, existing).apply()
    }

    fun loadVoidedTransactions(context: Context): Set<String> =
        HashSet(context.getSharedPreferences(VOIDED_PREFS, Context.MODE_PRIVATE)
            .getStringSet(VOIDED_KEY, emptySet()) ?: emptySet())

    // ── App theme ─────────────────────────────────────────────────────────────
    private const val THEME_PREFS = "app_theme"

    fun saveTheme(context: Context, theme: com.expensetracker.AppTheme) {
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .edit().putString("theme", theme.name).apply()
    }

    fun loadTheme(context: Context): com.expensetracker.AppTheme {
        val name = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getString("theme", null) ?: return com.expensetracker.AppTheme.BLACK
        return runCatching { com.expensetracker.AppTheme.valueOf(name) }
            .getOrDefault(com.expensetracker.AppTheme.BLACK)
    }

    // ── AI settings ───────────────────────────────────────────────────────────
    private const val AI_PREFS = "ai_settings"

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)
            .edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun loadApiKey(context: Context): String? =
        context.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }

    fun setAiEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("ai_enabled", enabled).apply()
    }

    fun isAiEnabled(context: Context): Boolean =
        context.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)
            .getBoolean("ai_enabled", false)

    // ── Custom payee names ────────────────────────────────────────────────────
    // Key: upiId (lowercase) if available, else txKey
    // Lets users label "Unknown" transactions with a human-readable shop/person name

    private const val PAYEE_NAMES_PREFS = "payee_names"

    fun savePayeeName(context: Context, key: String, name: String) {
        context.getSharedPreferences(PAYEE_NAMES_PREFS, Context.MODE_PRIVATE)
            .edit().putString(key.lowercase().trim(), name.trim()).apply()
    }

    fun loadPayeeNames(context: Context): Map<String, String> =
        context.getSharedPreferences(PAYEE_NAMES_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }
            .toMap()

    // ── Transaction notes ─────────────────────────────────────────────────────
    // Key: txKey  →  Value: free-text note written by the user

    private const val NOTES_PREFS = "tx_notes"

    fun saveNote(context: Context, txKey: String, note: String) {
        val prefs = context.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
        if (note.isBlank()) prefs.edit().remove(txKey).apply()
        else prefs.edit().putString(txKey, note.trim()).apply()
    }

    fun loadNotes(context: Context): Map<String, String> =
        context.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }
            .toMap()

    // ── Category budgets (monthly) ────────────────────────────────────────────
    // Key: Category.name  →  Value: budget amount as Long (paise-free, whole rupees)

    private const val BUDGET_PREFS = "category_budgets"

    fun saveBudget(context: Context, category: Category, amountRs: Double) {
        val prefs = context.getSharedPreferences(BUDGET_PREFS, Context.MODE_PRIVATE)
        if (amountRs <= 0) prefs.edit().remove(category.name).apply()
        else prefs.edit().putLong(category.name, amountRs.toLong()).apply()
    }

    fun loadBudgets(context: Context): Map<Category, Double> =
        context.getSharedPreferences(BUDGET_PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) ->
                runCatching {
                    val cat = Category.valueOf(k)
                    val amt = (v as? Long)?.toDouble() ?: return@mapNotNull null
                    cat to amt
                }.getOrNull()
            }.toMap()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadCategory(context: Context, prefs: String): Map<String, Category> =
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) ->
                try { k to Category.valueOf(v as String) }
                catch (_: Exception) { null }
            }.toMap()
}
