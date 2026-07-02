package com.expensetracker

import android.content.Context
import com.expensetracker.sms.model.Confidence
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.TxType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the last-parsed transaction list to internal storage as JSON.
 * On next app open the cache is loaded instantly (<50 ms) so the dashboard
 * appears immediately, while a fresh ContentResolver parse runs in the background.
 */
object TransactionCache {

    private const val CACHE_FILE = "tx_cache.json"

    fun save(context: Context, data: List<Pair<ParsedSms, Long>>) {
        try {
            val arr = JSONArray()
            for ((sms, date) in data) {
                arr.put(JSONObject().apply {
                    put("amount",           sms.amount)
                    put("type",             sms.type.name)
                    put("merchant",         sms.merchant ?: JSONObject.NULL)
                    put("accountTail",      sms.accountTail ?: JSONObject.NULL)
                    put("availableBalance", sms.availableBalance ?: JSONObject.NULL)
                    put("referenceNo",      sms.referenceNo ?: JSONObject.NULL)
                    put("bank",             sms.bank)
                    put("rawText",          sms.rawText)
                    put("confidence",       sms.confidence.name)
                    put("foreignCurrency",  sms.foreignCurrency ?: JSONObject.NULL)
                    put("foreignAmount",    sms.foreignAmount ?: JSONObject.NULL)
                    put("date",             date)
                })
            }
            File(context.filesDir, CACHE_FILE).writeText(arr.toString())
        } catch (_: Exception) { /* never crash on cache write */ }
    }

    fun load(context: Context): List<Pair<ParsedSms, Long>> {
        return try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val obj = arr.getJSONObject(i)
                    val sms = ParsedSms(
                        amount           = obj.getDouble("amount"),
                        type             = runCatching { TxType.valueOf(obj.getString("type")) }.getOrDefault(TxType.UNKNOWN),
                        merchant         = obj.optString("merchant").takeIf { it.isNotBlank() && it != "null" },
                        accountTail      = obj.optString("accountTail").takeIf { it.isNotBlank() && it != "null" },
                        availableBalance = obj.optDouble("availableBalance").takeIf { !it.isNaN() },
                        referenceNo      = obj.optString("referenceNo").takeIf { it.isNotBlank() && it != "null" },
                        bank             = obj.getString("bank"),
                        rawText          = obj.getString("rawText"),
                        confidence       = runCatching { Confidence.valueOf(obj.getString("confidence")) }.getOrDefault(Confidence.LOW),
                        foreignCurrency  = obj.optString("foreignCurrency").takeIf { it.isNotBlank() && it != "null" },
                        foreignAmount    = obj.optDouble("foreignAmount").takeIf { !it.isNaN() },
                    )
                    sms to obj.getLong("date")
                }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
