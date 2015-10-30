/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 */
package at.bitfire.ical4android;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.EntityIterator;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a locally stored calendar, containing AndroidEvents (whose data objects are Events).
 * Communicates with the Android Contacts Provider which uses an SQLite
 * database to store the events.
 */
public abstract class AndroidCalendar {
    private static final String TAG = "ical4android.Calendar";

    final protected Account account;
    final public ContentProviderClient provider;
    final AndroidEventFactory eventFactory;

    @Getter final protected long id;
    @Getter private String name, displayName;
    @Getter private Integer color;
    @Getter private boolean isSynced, isVisible;

    /**
     * Those CalendarContract.Events columns will always be fetched by queryEvents().
     * Must at least contain Events._ID!
     */
    protected String[] eventBaseInfoColumns() {
        return new String[] { Events._ID };
    }


    protected AndroidCalendar(Account account, ContentProviderClient provider, AndroidEventFactory eventFactory, long id) {
        this.account = account;
        this.provider = provider;
        this.eventFactory = eventFactory;
        this.id = id;
    }

	
	/* class methods, constructor */

    /**
     * Acquires a ContentProviderClient for the Android Calendar Contract.
     * @return A ContentProviderClient, or null if calendar storage is not available/accessible
     *         Caller is responsible for calling release()!
     */
    public static ContentProviderClient acquireContentProviderClient(ContentResolver resolver) {
        return resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
    }

	@SuppressLint("InlinedApi")
	public static Uri create(Account account, ContentProviderClient provider, ContentValues info) throws CalendarStorageException {
        info.put(Calendars.ACCOUNT_NAME, account.name);
        info.put(Calendars.ACCOUNT_TYPE, account.type);

        if (android.os.Build.VERSION.SDK_INT >= 15) {
            // these values are generated by ical4android
            info.put(Calendars.ALLOWED_AVAILABILITY, Events.AVAILABILITY_BUSY + "," + Events.AVAILABILITY_FREE + "," + Events.AVAILABILITY_TENTATIVE);
            info.put(Calendars.ALLOWED_ATTENDEE_TYPES, Attendees.TYPE_NONE + "," + Attendees.TYPE_OPTIONAL + "," + Attendees.TYPE_REQUIRED + "," + Attendees.TYPE_RESOURCE);
        }

		Log.i(TAG, "Creating local calendar: " + info.toString());
		try {
			return provider.insert(syncAdapterURI(Calendars.CONTENT_URI, account), info);
		} catch (RemoteException e) {
			throw new CalendarStorageException("Couldn't create calendar", e);
		}
	}

    public static AndroidCalendar findByID(Account account, ContentProviderClient provider, AndroidCalendarFactory factory, long id) throws FileNotFoundException, CalendarStorageException {
        @Cleanup EntityIterator iterCalendars = null;
        try {
            iterCalendars = CalendarContract.CalendarEntity.newEntityIterator(
                    provider.query(syncAdapterURI(ContentUris.withAppendedId(CalendarContract.CalendarEntity.CONTENT_URI, id), account), null, null, null, null)
            );
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendars", e);
        }

        if (iterCalendars.hasNext()) {
            ContentValues values = iterCalendars.next().getEntityValues();

            AndroidCalendar calendar = factory.newInstance(account, provider, values.getAsLong(Calendars._ID));
            calendar.populate(values);
            return calendar;
        }
        throw new FileNotFoundException();
    }

    public static AndroidCalendar[] find(Account account, ContentProviderClient provider, AndroidCalendarFactory factory, String where, String whereArgs[]) throws CalendarStorageException {
        @Cleanup EntityIterator iterCalendars = null;
        try {
            iterCalendars = CalendarContract.CalendarEntity.newEntityIterator(
                    provider.query(syncAdapterURI(CalendarContract.CalendarEntity.CONTENT_URI, account), null, where, whereArgs, null)
            );
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendars", e);
        }

        List<AndroidCalendar> calendars = new LinkedList<>();
        while (iterCalendars.hasNext()) {
            ContentValues values = iterCalendars.next().getEntityValues();

            AndroidCalendar calendar = factory.newInstance(account, provider, values.getAsLong(Calendars._ID));
            calendar.populate(values);
            calendars.add(calendar);
        }
        return calendars.toArray(factory.newArray(calendars.size()));
    }

    protected void populate(ContentValues info) {
        name = info.getAsString(Calendars.NAME);
        displayName = info.getAsString(Calendars.CALENDAR_DISPLAY_NAME);

        if (info.containsKey(Calendars.CALENDAR_COLOR))
            color = info.getAsInteger(Calendars.CALENDAR_COLOR);

        isSynced = info.getAsInteger(Calendars.SYNC_EVENTS) != 0;
        isVisible = info.getAsInteger(Calendars.VISIBLE) != 0;
    }


    public void update(ContentValues info) throws CalendarStorageException {
        try {
            provider.update(syncAdapterURI(calendarSyncURI()), info, null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't update calendar", e);
        }
    }

    public int delete() throws CalendarStorageException {
        try {
            return provider.delete(calendarSyncURI(), null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete calendar", e);
        }
    }


    protected AndroidEvent[] queryEvents(String where, String[] whereArgs) throws CalendarStorageException {
        where = (where == null ? "" : "(" + where + ") AND ") + Events.CALENDAR_ID + "=?";
        whereArgs = ArrayUtils.add(whereArgs, String.valueOf(id));

        @Cleanup Cursor cursor = null;
        try {
            cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    eventBaseInfoColumns(),
                    where, whereArgs, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendar events", e);
        }

        List<AndroidEvent> events = new LinkedList<>();
        while (cursor != null && cursor.moveToNext()) {
            ContentValues baseInfo = new ContentValues(cursor.getColumnCount());
            DatabaseUtils.cursorRowToContentValues(cursor, baseInfo);
            events.add(eventFactory.newInstance(this, cursor.getLong(0), baseInfo));
        }
        return events.toArray(eventFactory.newArray(events.size()));
    }


    public static Uri syncAdapterURI(Uri uri, Account account) {
        return uri.buildUpon()
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public Uri syncAdapterURI(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public Uri calendarSyncURI() {
        return syncAdapterURI(ContentUris.withAppendedId(Calendars.CONTENT_URI, id));
    }

}
