package com.msgprioritizer.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ChatExportParserTest {
    private val utc = ZoneOffset.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(utc).toEpochMilli()

    @Test fun androidTwelveHourWithNarrowSpace() {
        val text = """
            25/09/26, 9:41${'\u202f'}pm - Messages and calls are end-to-end encrypted. Tap to learn more.
            25/09/26, 9:41${'\u202f'}pm - Mom: Call me when you're free
            25/09/26, 9:45${'\u202f'}pm - Priya: ok
        """.trimIndent()
        val lines = ChatExportParser.parse(text, utc)
        assertEquals(2, lines.size)
        assertEquals("Mom", lines[0].sender)
        assertEquals("Call me when you're free", lines[0].text)
        assertEquals(at(2026, 9, 25, 21, 41), lines[0].timestamp)
    }

    @Test fun iphoneBracketsWithSeconds() {
        val text = "\u200e[10/4/26, 8:05:09 AM] Ravi: Can you send the rent today?"
        val line = ChatExportParser.parse(text, utc).single()
        assertEquals("Ravi", line.sender)
        // No part is over 12, so it falls back to day-first: 10 April.
        assertEquals(at(2026, 4, 10, 8, 5, 9), line.timestamp)
    }

    @Test fun monthFirstDetectedFromWholeFile() {
        val text = """
            10/4/26, 08:00 - Ravi: first
            10/25/26, 09:00 - Ravi: second
        """.trimIndent()
        val lines = ChatExportParser.parse(text, utc)
        assertEquals(at(2026, 10, 4, 8, 0), lines[0].timestamp)
        assertEquals(at(2026, 10, 25, 9, 0), lines[1].timestamp)
    }

    @Test fun multiLineMessagesAreJoined() {
        val text = """
            25/09/2026, 21:00 - Manager: Deadline moved
            to 5pm, please update
            the doc
        """.trimIndent()
        assertEquals("Deadline moved\nto 5pm, please update\nthe doc", ChatExportParser.parse(text, utc).single().text)
    }

    @Test fun mediaAndDeletedMessagesAreSkipped() {
        val text = """
            25/09/26, 21:00 - Divya: <Media omitted>
            25/09/26, 21:01 - Divya: This message was deleted
            25/09/26, 21:02 - Divya: see you <This message was edited>
        """.trimIndent()
        assertEquals(listOf("see you"), ChatExportParser.parse(text, utc).map { it.text })
    }

    @Test fun twelveAmIsMidnight() {
        val line = ChatExportParser.parse("[25/09/26, 12:15:00 AM] Ravi: late", utc).single()
        assertEquals(at(2026, 9, 25, 0, 15), line.timestamp)
    }

    @Test fun chatNameComesFromFileName() {
        assertEquals("Mom", ChatExportParser.chatNameFromFile("WhatsApp Chat with Mom.txt"))
        assertEquals("College Friends", ChatExportParser.chatNameFromFile("WhatsApp Chat - College Friends.zip"))
        assertNull(ChatExportParser.chatNameFromFile("_chat.txt"))
    }
}
