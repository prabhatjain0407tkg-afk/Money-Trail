package com.expensetracker

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.expensetracker.sms.parser.SmsParser
import com.expensetracker.sms.parser.TollParser
import org.json.JSONArray
import org.json.JSONObject

/**
 * Captures bank transaction notifications from messaging apps and stores them so
 * [readAndParseInbox] can merge them alongside regular SMS.
 *
 * This is the only path that reaches RCS / Chat messages — they never appear in
 * content://sms, but they DO generate a system notification which we can read here.
 *
 * Requires one-time user grant: Settings → Notification Access → this app.
 */
class SmsNotificationListener : NotificationListenerService() {

    companion object {
        private val MESSAGING_PACKAGES = setOf(
            "com.samsung.android.messaging",     // Samsung Messages — SMS + RCS
            "com.google.android.apps.messaging", // Google Messages
            "com.android.mms",                   // MIUI / MediaTek stock
            "com.android.messaging",             // AOSP stock
        )

        const val PREFS_NAME   = "notification_cache"
        const val KEY_MESSAGES = "messages"
        private const val MAX_CACHED = 1000

        fun isGranted(context: Context): Boolean =
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )?.contains(context.packageName, ignoreCase = true) == true

        fun loadCaptured(context: Context): List<Triple<String, String, Long>> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr   = runCatching {
                JSONArray(prefs.getString(KEY_MESSAGES, "[]") ?: "[]")
            }.getOrDefault(JSONArray())
            return (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    Triple(o.getString("sender"), o.getString("body"), o.getLong("date"))
                }.getOrNull()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MESSAGING_PACKAGES) return

        val sender = sbn.notification.extras
            .getString(Notification.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() } ?: return

        val body = extractBody(sbn.notification)?.takeIf { it.isNotBlank() } ?: return

        if (!looksFinancial(body)) return
        SmsParser.parse(sender, body) ?: TollParser.parse(sender, body) ?: return  // validate it's a real transaction

        val hash  = body.hashCode().toString()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr   = runCatching {
            JSONArray(prefs.getString(KEY_MESSAGES, "[]") ?: "[]")
        }.getOrDefault(JSONArray())

        // Deduplicate by body hash — same message can arrive via SMS + notification
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("hash") == hash) return
        }

        arr.put(JSONObject().apply {
            put("sender", sender)
            put("body",   body)
            put("date",   sbn.postTime)
            put("hash",   hash)
        })

        val trimmed = if (arr.length() > MAX_CACHED) {
            JSONArray().also { t ->
                val start = arr.length() - MAX_CACHED
                for (i in start until arr.length()) t.put(arr.get(i))
            }
        } else arr

        prefs.edit().putString(KEY_MESSAGES, trimmed.toString()).apply()

        // Signal MainActivity to refresh — same flag the SmsReceiver uses
        getSharedPreferences("sync_state", Context.MODE_PRIVATE)
            .edit().putLong("last_bg_sync", System.currentTimeMillis()).apply()
    }

    private fun extractBody(notification: Notification): String? {
        // MessagingStyle is the richest format; Samsung Messages and Google Messages both use it
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
        if (style != null) {
            return style.messages.lastOrNull()?.text?.toString()
        }
        val extras = notification.extras
        // Expanded BigText (longer messages)
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        // Plain one-liner
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    private fun looksFinancial(body: String): Boolean {
        val l = body.lowercase()
        return (l.contains("debited") || l.contains("credited") ||
                l.contains("spent")   || l.contains("sent") ||
                l.contains("rs.")     || l.contains("inr ")  || l.contains("₹")) &&
               (l.contains("a/c")    || l.contains("account") ||
                l.contains("upi")    || l.contains("card") ||
                l.contains("wallet") || l.contains("bank"))
    }
}
