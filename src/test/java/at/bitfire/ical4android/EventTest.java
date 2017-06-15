/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.ical4android;

import net.fortuna.ical4j.util.TimeZones;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import lombok.Cleanup;
import lombok.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventTest {
    private static final String TAG = "ical4android.EventTest";


    /* public interface tests */

    @Test
    public void testCalendarProperties() throws IOException, InvalidCalendarException {
        @Cleanup InputStream is = getClass().getClassLoader().getResourceAsStream("events/multiple.ics");
        assertNotNull(is);
        Map<String, String> properties = new HashMap<>();
        Event.fromStream(is, null, properties);
        assertEquals(1, properties.size());
        assertEquals("Test-Kalender", properties.get(Event.CALENDAR_NAME));
    }

    @Test
    public void testCharsets() throws IOException, InvalidCalendarException {
        Event e = parseCalendar("latin1.ics", Charset.forName("ISO-8859-1"))[0];
        assertEquals("äöüß", e.getSummary());

        e = parseCalendar("utf8.ics", null)[0];
        assertEquals("© äö — üß", e.getSummary());
        assertEquals("中华人民共和国", e.getLocation());
    }

    @Test
    public void testGrouping() throws IOException, InvalidCalendarException {
        Event[] events = parseCalendar("multiple.ics", null);
        assertEquals(3, events.length);

        Event e = findEvent(events, "multiple-0@ical4android.EventTest");
        assertEquals("Event 0", e.getSummary());
        assertEquals(0, e.getExceptions().size());

        e = findEvent(events, "multiple-1@ical4android.EventTest");
        assertEquals("Event 1", e.getSummary());
        assertEquals(1, e.getExceptions().size());
        assertEquals("Event 1 Exception", e.getExceptions().get(0).getSummary());

        e = findEvent(events, "multiple-2@ical4android.EventTest");
        assertEquals("Event 2", e.getSummary());
        assertEquals(2, e.getExceptions().size());
        assertTrue("Event 2 Updated Exception 1".equals(e.getExceptions().get(0).getSummary()) || "Event 2 Updated Exception 1".equals(e.getExceptions().get(1).getSummary()));
        assertTrue("Event 2 Exception 2".equals(e.getExceptions().get(0).getSummary()) || "Event 2 Exception 2".equals(e.getExceptions().get(1).getSummary()));
    }

    @Test
    public void testRecurringWithException() throws IOException, InvalidCalendarException {
        Event event = parseCalendar("recurring-with-exception1.ics", null)[0];
        assertTrue(event.isAllDay());

        assertEquals(1, event.getExceptions().size());
        Event exception = event.getExceptions().get(0);
        assertEquals("20150503", exception.getRecurrenceId().getValue());
        assertEquals("Another summary for the third day", exception.getSummary());
    }

    @Test
    public void testStartEndTimes() throws IOException, InvalidCalendarException {
        // event with start+end date-time
        Event eViennaEvolution = parseCalendar("vienna-evolution.ics", null)[0];
        assertEquals(1381330800000L, eViennaEvolution.getDtStart().getDate().getTime());
        assertEquals("Europe/Vienna", eViennaEvolution.getDtStartTzID());
        assertEquals(1381334400000L, eViennaEvolution.getDtEnd().getDate().getTime());
        assertEquals("Europe/Vienna", eViennaEvolution.getDtEndTzID());
    }

    @Test
    public void testStartEndTimesAllDay() throws IOException, InvalidCalendarException {
        // event with start date only
        Event eOnThatDay = parseCalendar("event-on-that-day.ics", null)[0];
        assertEquals(868838400000L, eOnThatDay.getDtStart().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eOnThatDay.getDtStartTzID());

        // event with start+end date for all-day event (one day)
        Event eAllDay1Day = parseCalendar("all-day-1day.ics", null)[0];
        assertEquals(868838400000L, eAllDay1Day.getDtStart().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtStartTzID());
        assertEquals(868838400000L + 86400000, eAllDay1Day.getDtEnd().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtEndTzID());

        // event with start+end date for all-day event (ten days)
        Event eAllDay10Days = parseCalendar("all-day-10days.ics", null)[0];
        assertEquals(868838400000L, eAllDay10Days.getDtStart().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtStartTzID());
        assertEquals(868838400000L + 10 * 86400000, eAllDay10Days.getDtEnd().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtEndTzID());

        // event with start+end date on some day (0 sec-event)
        Event eAllDay0Sec = parseCalendar("all-day-0sec.ics", null)[0];
        assertEquals(868838400000L, eAllDay0Sec.getDtStart().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtStartTzID());
        // DTEND is same as DTSTART which is not valid for Android – but this will be handled by AndroidEvent, not Event
        assertEquals(868838400000L, eAllDay0Sec.getDtEnd().getDate().getTime());
        assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtEndTzID());
    }

    @Test
    public void testUnfolding() throws IOException, InvalidCalendarException {
        Event e = parseCalendar("two-line-description-without-crlf.ics", null)[0];
        assertEquals("http://www.tgbornheim.de/index.php?sessionid=&page=&id=&sportcentergroup=&day=6", e.getDescription());
    }


    /* internal tests */

    @Test
    public void testFindMasterEventsAndExceptions() throws ParseException, IOException, InvalidCalendarException {
        Event[] events;

        // two single events
        events = parseCalendar("two-events-without-exceptions.ics", null);
        assertEquals(2, events.length);
        for (Event event : events)
            assertTrue(event.getExceptions().isEmpty());

        // one event with exception, another single event
        events = parseCalendar("one-event-with-exception-one-without.ics", null);
        assertEquals(2, events.length);
        for (Event event : events) {
            String uid = event.getUid();
            if ("event1".equals(uid))
                assertEquals(1, event.getExceptions().size());
            else
                assertTrue(event.getExceptions().isEmpty());
        }

        // one event two exceptions (thereof one updated two times) and updated exception, another single event
        events = parseCalendar("one-event-with-multiple-exceptions-one-without.ics", null);
        assertEquals(2, events.length);
        for (Event event : events) {
            String uid = event.getUid();
            if ("event1".equals(uid)) {
                assertEquals(2, event.getExceptions().size());
                for (Event exception : event.getExceptions())
                    if ("20150503".equals(exception.getRecurrenceId().getValue())) {
                        assertEquals(2, (int)exception.getSequence());
                        assertEquals("Final summary", exception.getSummary());
                    }
            } else
                assertTrue(event.getExceptions().isEmpty());
        }
    }


    // helpers

    private Event findEvent(@NonNull Event[] events, @NonNull String uid) throws FileNotFoundException {
        for (Event event : events)
            if (uid.equals(event.getUid()))
                return event;
        throw new FileNotFoundException();
    }

    private Event[] parseCalendar(String fname, Charset charset) throws IOException, InvalidCalendarException {
        fname = "events/" + fname;
        System.err.println("Loading event file " + fname);
        @Cleanup InputStream is = getClass().getClassLoader().getResourceAsStream(fname);
        assertNotNull(is);
        return Event.fromStream(is, charset);
    }

}
