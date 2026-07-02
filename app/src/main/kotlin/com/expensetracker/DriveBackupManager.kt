package com.expensetracker

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DriveBackupManager {

    private const val FILENAME      = "moneytrail_backup.json"
    private const val FILES_URL     = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL    = "https://www.googleapis.com/upload/drive/v3/files"
    const val DRIVE_SCOPE           = "https://www.googleapis.com/auth/drive.appdata"
    private const val APPDATA_SPACE = "appDataFolder"

    fun isConnected(context: Context): Boolean {
        val acct = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(acct, Scope(DRIVE_SCOPE))
    }

    fun signedInEmail(context: Context): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    private suspend fun token(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val acct = GoogleSignIn.getLastSignedInAccount(context)?.account
                ?: return@runCatching null
            com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context, acct, "oauth2:$DRIVE_SCOPE"
            )
        }.getOrNull()
    }

    suspend fun upload(context: Context, json: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val tok = token(context) ?: return@withContext false
        val body = json.toString(2).toByteArray(Charsets.UTF_8)
        runCatching {
            val existId = findFileId(tok)
            if (existId != null) patchFile(tok, existId, body) else createFile(tok, body)
        }.getOrDefault(false)
    }

    suspend fun download(context: Context): JSONObject? = withContext(Dispatchers.IO) {
        val tok = token(context) ?: return@withContext null
        runCatching {
            val id = findFileId(tok) ?: return@runCatching null
            val conn = (URL("$FILES_URL/$id?alt=media").openConnection() as HttpURLConnection)
                .also { it.setRequestProperty("Authorization", "Bearer $tok") }
            val txt = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
            JSONObject(txt)
        }.getOrNull()
    }

    private fun findFileId(tok: String): String? {
        val url = URL("$FILES_URL?spaces=$APPDATA_SPACE&q=name+%3D+'$FILENAME'&fields=files(id)&pageSize=1")
        val conn = (url.openConnection() as HttpURLConnection)
            .also { it.setRequestProperty("Authorization", "Bearer $tok") }
        return runCatching {
            val txt = conn.inputStream.bufferedReader().readText()
            JSONObject(txt).optJSONArray("files")
                ?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
        }.getOrNull().also { conn.disconnect() }
    }

    private fun createFile(tok: String, body: ByteArray): Boolean {
        val boundary = "mtbnd"
        val meta = """{"name":"$FILENAME","parents":["$APPDATA_SPACE"]}"""
        val multipart =
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray() +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray() +
            body +
            "\r\n--$boundary--".toByteArray()
        val conn = (URL("$UPLOAD_URL?uploadType=multipart").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $tok")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
            outputStream.write(multipart)
        }
        val code = conn.responseCode.also { conn.disconnect() }
        return code in 200..299
    }

    private fun patchFile(tok: String, fileId: String, body: ByteArray): Boolean {
        val conn = (URL("$UPLOAD_URL/$fileId?uploadType=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Authorization", "Bearer $tok")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.write(body)
        }
        val code = conn.responseCode.also { conn.disconnect() }
        return code in 200..299
    }
}
