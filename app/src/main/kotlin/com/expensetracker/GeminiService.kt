package com.expensetracker

import android.content.Context
import com.expensetracker.sms.categorizer.SubCategoryRules
import com.expensetracker.sms.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GeminiService {

    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    // ── Rate-limit constants (free Gemini 2.0 Flash tier) ────────────────────
    private const val SPAM_BATCH_SIZE = 20    // SMS per API request
    private const val SPAM_RPM_LIMIT  = 14    // 15/min free tier; leave 1 buffer
    private const val SPAM_RPD_LIMIT  = 1490  // 1500/day free tier; leave 10 buffer

    // Sliding window of request timestamps for per-minute tracking (in-memory only).
    // Resets on app restart which is intentionally conservative.
    private val rpmWindow = ArrayDeque<Long>()

    // ─────────────────────────────────────────────────────────────────────────
    // Existing: single-SMS category suggestion
    // ─────────────────────────────────────────────────────────────────────────

    data class Suggestion(
        val category: Category,
        val iconEmoji: String?,
        val reason: String
    )

    suspend fun suggest(apiKey: String, smsText: String): Result<Suggestion> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = buildRequestBody(smsText)
                val url  = URL("$BASE_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput      = true
                conn.connectTimeout = 15_000
                conn.readTimeout    = 20_000
                conn.outputStream.bufferedWriter().use { it.write(requestBody) }
                val code = conn.responseCode
                val body = if (code == 200)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                conn.disconnect()
                if (code != 200) throw Exception("Gemini API error $code: $body")
                parseResponse(body)
            }
        }

    private fun buildRequestBody(smsText: String): String {
        val prompt = buildPrompt(smsText)
        return JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }.toString()
    }

    private fun maskAmounts(text: String): String =
        text.replace(Regex("""(?i)(?:[₹]|Rs\.?\s*|INR\s*)\s*([\d,]+(?:\.\d{1,2})?)""")) { mr ->
            mr.value.replace(mr.groupValues[1], "XXXXX")
        }

    private fun buildPrompt(smsText: String): String {
        val maskedSms = maskAmounts(smsText)
        val categoryList = """
FOOD - Restaurants, food delivery (Swiggy, Zomato), cafes, street food
GROCERY - Supermarkets, quick-commerce (Blinkit, Zepto, Dunzo), fresh produce
COMMUTE - Cab rides (Uber, Ola), metro/bus pass, fuel, toll/FASTag, parking, EV charging
TRAVEL - Flights, hotels, trains, tourism, travel packages
SHOPPING - Fashion, electronics, home decor, beauty, jewellery, books
BILLS - Electricity, gas, water, broadband/WiFi, mobile recharge, OTT subscriptions, society maintenance
EDUCATION - School/college fees, coaching classes, online courses, exam fees
HEALTH - Medicine, doctor consultation, lab tests, pathology, hospital, dental, health insurance premium
FITNESS - Gym, fitness center, swimming pool, sports court booking (Playo), yoga, cycling, sports club
ENTERTAINMENT - Movies, events, gaming, concerts, amusement parks
INVESTMENT - Mutual fund SIP, FD/RD, PPF, NPS, stocks, demat, gold, insurance premium payment
INCOME - Salary credit, UPI money received, bank transfer received, refund, cashback
CASH - ATM withdrawal, cash advance, micro-ATM
TRANSFER - Self transfer between own accounts, internal fund transfer
CC_PAYMENT - Credit card bill payment (not a purchase on credit card)
UNCATEGORIZED - Cannot determine category with confidence
        """.trimIndent()

        val iconHints = buildString {
            SubCategoryRules.ICON_OPTIONS.entries
                .filter { it.key != Category.UNCATEGORIZED }
                .forEach { (cat, options) ->
                    val opts = options.joinToString(", ") { (e, l) -> "$e=$l" }
                    append("${cat.name}: $opts\n")
                }
        }

        return """
You are a financial transaction categorizer for Indian bank SMS messages.

Available categories:
$categoryList

Icon tag hints per category (suggest ONE if highly confident, or null):
$iconHints
Categorize this bank SMS:
"$maskedSms"

Respond with ONLY valid JSON — no markdown, no explanation, nothing else:
{"category":"CATEGORY_NAME","iconEmoji":"emoji_or_null","reason":"5 to 15 word reason"}

Rules:
- category must be one exact name from the list above
- iconEmoji must be one emoji from the hints for that category, or the JSON null value
- If SMS shows credit card bill payment → CC_PAYMENT (not BILLS)
- If SMS shows money received/credited → INCOME
- If SMS mentions ATM or cash withdrawal → CASH
- If merchant name appears in SMS body (e.g. SWIGGY, UBER, NETFLIX) use it to determine category
        """.trimIndent()
    }

    private fun parseResponse(json: String): Suggestion {
        val text = JSONObject(json)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
        val obj       = JSONObject(text)
        val catName   = obj.optString("category", "UNCATEGORIZED")
        val cat       = runCatching { Category.valueOf(catName) }.getOrDefault(Category.UNCATEGORIZED)
        val iconEmoji = obj.optString("iconEmoji").takeIf { it.isNotBlank() && it != "null" }
        val reason    = obj.optString("reason", "AI categorized this transaction")
        return Suggestion(cat, iconEmoji, reason)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // New: batch spam / promo detection for low-confidence SMS
    // ─────────────────────────────────────────────────────────────────────────

    // PII patterns stripped before sending any SMS text to the API.
    // Real/promo classification depends on language structure, not actual numbers.
    private val PII_PATTERNS = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE) to "[AMT]",
        Regex("""[X*]{2,}\d{4}""")                                                    to "[ACCT]",
        Regex("""\b\d{6,}\b""")                                                       to "[REF]",
        Regex("""\d{1,2}[-/]\w{2,9}[-/]\d{2,4}""")                                  to "[DATE]",
        Regex("""\b\d{10}\b""")                                                       to "[PHONE]",
    )

    fun stripPii(body: String): String {
        var s = body
        for ((pattern, replacement) in PII_PATTERNS) s = pattern.replace(s, replacement)
        return s
    }

    /**
     * Validates [items] (bodyHash → rawBody) for spam/promo content.
     * Only sends stripped (PII-free) text to the API.
     * Enforces free-tier limits: 14 req/min and 1490 req/day.
     * Returns the set of body hashes Gemini identified as PROMOTIONAL.
     */
    suspend fun validateSpam(
        apiKey: String,
        context: Context,
        items: List<Pair<Int, String>>,  // bodyHash → rawBody
    ): Set<Int> = withContext(Dispatchers.IO) {
        val promotional = mutableSetOf<Int>()
        val batches     = items.chunked(SPAM_BATCH_SIZE)

        for (batch in batches) {
            // Stop if daily quota is exhausted
            if (UserPrefsStore.geminiDailyRemaining(context, SPAM_RPD_LIMIT) <= 0) break

            // Enforce per-minute rate limit (waits if needed)
            enforceRpmLimit()

            // Record this request toward the daily budget
            UserPrefsStore.incrementGeminiDailyCount(context)

            val redacted = batch.map { (hash, body) -> hash to stripPii(body) }
            val result   = callSpamCheck(apiKey, redacted) ?: continue

            for ((hash, isPromo) in result) {
                if (isPromo) promotional.add(hash)
            }
        }

        promotional
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    private suspend fun enforceRpmLimit() {
        val now = System.currentTimeMillis()
        // Evict timestamps older than 60 s
        while (rpmWindow.isNotEmpty() && now - rpmWindow.first() > 60_000L) {
            rpmWindow.removeFirst()
        }
        if (rpmWindow.size >= SPAM_RPM_LIMIT) {
            // Wait until the oldest slot expires
            val waitMs = 60_001L - (System.currentTimeMillis() - rpmWindow.first())
            if (waitMs > 0) delay(waitMs)
            rpmWindow.removeFirst()
        }
        rpmWindow.addLast(System.currentTimeMillis())
    }

    // ── HTTP call for spam check ──────────────────────────────────────────────

    private fun callSpamCheck(
        apiKey: String,
        items: List<Pair<Int, String>>,  // hash → PII-stripped body
    ): Map<Int, Boolean>? {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", buildSpamPrompt(items))
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 600)
                put("responseMimeType", "application/json")
            })
        }.toString()

        return try {
            val conn = (URL("$BASE_URL?key=$apiKey").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput       = true
                connectTimeout = 15_000
                readTimeout    = 25_000
            }
            conn.outputStream.use { it.write(requestBody.toByteArray()) }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return null }
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseSpamResponse(text, items)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSpamPrompt(items: List<Pair<Int, String>>): String = buildString {
        append(
            "You are filtering Indian bank SMS for a personal finance app.\n" +
            "Classify each SMS as REAL (actual money movement) or PROMO (marketing / spam).\n\n" +
            "REAL = salary credit, UPI transfer, ATM withdrawal, actual debit/credit with transaction reference\n" +
            "PROMO = loan offer, pre-approved credit, \"up to [AMT]\" promotion, referral bonus, " +
            "bonus credited, cashback offer, download link, investment pitch, fake reward\n\n" +
            "Reply ONLY as a JSON array — no markdown, no explanation:\n" +
            "[{\"i\":0,\"r\":\"REAL\"},{\"i\":1,\"r\":\"PROMO\"},...]\n\nSMS:\n"
        )
        items.forEachIndexed { idx, (_, body) ->
            append("$idx: ${body.take(220)}\n")
        }
    }

    private fun parseSpamResponse(
        responseText: String,
        items: List<Pair<Int, String>>,
    ): Map<Int, Boolean> = try {
        val text = JSONObject(responseText)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
        val arr = JSONArray(text)
        buildMap {
            for (k in 0 until arr.length()) {
                val obj    = arr.getJSONObject(k)
                val idx    = obj.getInt("i")
                val isPromo = obj.getString("r").equals("PROMO", ignoreCase = true)
                if (idx in items.indices) put(items[idx].first, isPromo)
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Daily budget helpers (delegate to UserPrefsStore for persistence)
    // ─────────────────────────────────────────────────────────────────────────

    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    fun todayKey(): String = dayFmt.format(Date())
}
