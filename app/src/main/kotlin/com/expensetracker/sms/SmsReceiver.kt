package com.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.sms.parser.SmsParser

/**
 * Listens for incoming SMS and enqueues any financial messages for parsing.
 * Registered in AndroidManifest.xml with RECEIVE_SMS permission.
 *
 * Kept intentionally thin — heavy work (DB write, categorization) is handed
 * off to WorkManager so this receiver returns quickly.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue

            // Quick pre-filter — only bother parsing if it looks financial
            if (!looksFinancial(body)) continue

            SmsParser.parse(sender, body) ?: continue

            // Signal MainActivity to refresh — ContentObserver catches this while the app
            // is in the foreground; the flag below ensures the reload also happens on
            // the next resume if the app was backgrounded when the SMS arrived.
            context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_bg_sync", System.currentTimeMillis())
                .apply()
        }
    }

    private fun looksFinancial(body: String): Boolean {
        val lower = body.lowercase()
        return (lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("sent") ||
                lower.contains("rs.") || lower.contains("inr ") ||
                lower.contains("₹")) &&
               (lower.contains("a/c") || lower.contains("account") ||
                lower.contains("upi") || lower.contains("card") ||
                lower.contains("wallet") || lower.contains("bank"))
    }
}
