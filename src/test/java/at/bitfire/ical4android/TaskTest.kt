/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Dur
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class TaskTest {

    /* public interface tests */

    @Test
    fun testCharsets() {
        var t = parseCalendar("latin1.ics", Charsets.ISO_8859_1)
        assertEquals("äöüß", t.summary)

        t = parseCalendar("utf8.ics")
        assertEquals("© äö — üß", t.summary)
        assertEquals("中华人民共和国", t.location)
    }

    @Test
    fun testDueBeforeDtStart() {
        val t = parseCalendar("due-before-dtstart.ics")
        assertEquals(t.summary, "DUE before DTSTART")
        // invalid tasks with DUE before DTSTART: DTSTART should be set to null
        assertNull(t.dtStart)
    }

    @Test
    fun testSamples() {
        val t = regenerate(parseCalendar("rfc5545-sample1.ics"))
        assertEquals(2, t.sequence)
        assertEquals("uid4@example.com", t.uid)
        assertEquals("mailto:unclesam@example.com", t.organizer!!.value)
        assertEquals(Due("19980415T000000"), t.due)
        assertFalse(t.isAllDay())
        assertEquals(Status.VTODO_NEEDS_ACTION, t.status)
        assertEquals("Submit Income Taxes", t.summary)
    }

    @Test
    fun testAllFields() {
        // 1. parse the VTODO file
        // 2. generate a new VTODO file from the parsed code
        // 3. parse it again – so we can test parsing and generating at once
        var t = regenerate(parseCalendar("most-fields1.ics"))
        assertEquals(1, t.sequence)
        assertEquals("most-fields1@example.com", t.uid)
        assertEquals("Conference Room - F123, Bldg. 002", t.location)
        assertEquals("37.386013", t.geoPosition!!.latitude.toPlainString())
        assertEquals("-122.082932", t.geoPosition!!.longitude.toPlainString())
        assertEquals("Meeting to provide technical review for \"Phoenix\" design.\nHappy Face Conference Room. Phoenix design team MUST attend this meeting.\nRSVP to team leader.", t.description)
        assertEquals("http://example.com/principals/jsmith", t.organizer!!.value)
        assertEquals("http://example.com/pub/calendars/jsmith/mytime.ics", t.url)
        assertEquals(1, t.priority)
        assertEquals(Clazz.CONFIDENTIAL, t.classification)
        assertEquals(Status.VTODO_IN_PROCESS, t.status)
        assertEquals(25, t.percentComplete)
        assertEquals(DtStart(Date("20100101")), t.dtStart)
        assertEquals(Due(Date("20101001")), t.due)
        assertTrue(t.isAllDay())

        assertEquals(RRule("FREQ=YEARLY;INTERVAL=2"), t.rRule)
        assertEquals(2, t.exDates.size)
        assertTrue(t.exDates.contains(ExDate(DateList("20120101", Value.DATE))))
        assertTrue(t.exDates.contains(ExDate(DateList("20140101,20180101", Value.DATE))))
        assertEquals(2, t.rDates.size)
        assertTrue(t.rDates.contains(RDate(DateList("20100310,20100315", Value.DATE))))
        assertTrue(t.rDates.contains(RDate(DateList("20100810", Value.DATE))))

        assertEquals(828106200000L, t.createdAt)
        assertEquals(840288600000L, t.lastModified)

        t = regenerate(parseCalendar("most-fields2.ics"))
        assertEquals("most-fields2@example.com", t.uid)
        assertEquals(DtStart(DateTime("20100101T101010Z")), t.dtStart)
        assertEquals(Duration(Dur(4, 3, 2, 1)), t.duration)
    }


    /* helpers */

    private fun parseCalendar(fname: String, charset: Charset = Charsets.UTF_8): Task {
        javaClass.classLoader!!.getResourceAsStream("tasks/$fname").use { stream ->
            return Task.fromReader(InputStreamReader(stream, charset)).first()
        }
    }

    private fun regenerate(t: Task): Task {
        val os = ByteArrayOutputStream()
        t.write(os)
        return Task.fromReader(InputStreamReader(ByteArrayInputStream(os.toByteArray()), Charsets.UTF_8)).first()
    }
    
}
