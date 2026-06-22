package com.expensetracker

import com.expensetracker.sms.categorizer.SubCategoryRules
import com.expensetracker.sms.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiService {

    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

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

    private fun maskAmounts(text: String): String {
        // Replace currency amounts with XXXXX to avoid sending financial figures to the API
        return text
            .replace(Regex("""(?i)(?:[₹]|Rs\.?\s*|INR\s*)\s*([\d,]+(?:\.\d{1,2})?)""")) { mr ->
                mr.value.replace(mr.groupValues[1], "XXXXX")
            }
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
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()

        val obj       = JSONObject(text)
        val catName   = obj.optString("category", "UNCATEGORIZED")
        val cat       = runCatching { Category.valueOf(catName) }.getOrDefault(Category.UNCATEGORIZED)
        val iconEmoji = obj.optString("iconEmoji").takeIf { it.isNotBlank() && it != "null" }
        val reason    = obj.optString("reason", "AI categorized this transaction")

        return Suggestion(cat, iconEmoji, reason)
    }
}
