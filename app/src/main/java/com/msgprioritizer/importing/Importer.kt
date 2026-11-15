package com.msgprioritizer.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.msgprioritizer.data.Prefs
import com.msgprioritizer.service.Ranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/** Brings real messages in without notification access: shared or picked chat exports, or pasted text. */
object Importer {
    /** Only the newest messages of a long chat are worth ranking. */
    private const val MAX_MESSAGES = 300
    private const val WHATSAPP = "com.whatsapp"
    const val PASTED = "pasted"

    /** A WhatsApp export shared to us, or a file picked with "Import chat file". */
    suspend fun importUri(context: Context, uri: Uri, subject: String?): String = withContext(Dispatchers.IO) {
        val fileName = displayName(context, uri)
        val text = runCatching { readText(context, uri) }.getOrNull()
            ?: return@withContext "Couldn't open that file. Try exporting the chat again."
        val chatName = ChatExportParser.chatNameFromFile(fileName) ?: ChatExportParser.chatNameFromFile(subject)
        importExport(context, text, chatName)
            ?: "No messages found in that file. In WhatsApp, use Export chat → Without media, then share it here."
    }

    /** Text shared to us or pasted in. Uses the export format if it matches, otherwise it's one message. */
    suspend fun importText(context: Context, text: String, subject: String?): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext "Paste some messages first."
        importExport(context, trimmed, ChatExportParser.chatNameFromFile(subject))?.let { return@withContext it }

        val stored = Ranker.rankAndStore(
            context, PASTED, chat = "Pasted message", sender = "Pasted message",
            text = trimmed, isGroup = false, timestamp = System.currentTimeMillis(),
        )
        if (stored == null) "That message is already in your inbox." else "Added 1 message. It's ranked ${stored.level}."
    }

    /** Returns a summary for the user, or null if the text isn't a chat export. */
    private suspend fun importExport(context: Context, text: String, chatName: String?): String? {
        val lines = ChatExportParser.parse(text).takeLast(MAX_MESSAGES)
        if (lines.isEmpty()) return null

        val senders = lines.map { it.sender }.toSet()
        val me = whoIsMe(senders, chatName, Prefs(context).myName)
        val others = senders - me
        val isGroup = others.size > 1
        val chat = chatName ?: if (isGroup) "Imported chat" else others.firstOrNull() ?: "Imported chat"

        var added = 0
        var high = 0
        for (line in lines) {
            if (line.sender in me) continue
            val stored = Ranker.rankAndStore(context, WHATSAPP, chat, line.sender, line.text, isGroup, line.timestamp)
            if (stored != null) {
                added++
                if (stored.level == "HIGH") high++
            }
        }
        return when {
            added == 0 -> "Those messages from $chat are already in your inbox."
            else -> "Added $added messages from $chat. $high need you now."
        }
    }

    /**
     * Your own messages shouldn't be ranked. Your name from Settings wins; otherwise, in a
     * one-to-one chat the person who isn't the chat's name must be you.
     */
    private fun whoIsMe(senders: Set<String>, chatName: String?, myName: String): Set<String> {
        if (myName.isNotBlank()) return senders.filter { it.equals(myName.trim(), ignoreCase = true) }.toSet()
        if (chatName != null && senders.size == 2 && chatName in senders) return senders - chatName
        return emptySet()
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /** Plain .txt, or the .zip that "Export chat → Include media" (and iPhone) produce. */
    private fun readText(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return bytes.toString(Charsets.UTF_8)
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name.endsWith(".txt", ignoreCase = true)) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return null
    }
}
