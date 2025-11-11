package com.forensic.agent;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.provider.CallLog;
import android.provider.CalendarContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;

public class DataProvider extends ContentProvider {
    public static final String AUTHORITY = "com.forensic.agent.provider";
    public static final String[] COLUMNS = new String[]{"name", "size", "modified"};
    private File baseDir;

    @Override
    public boolean onCreate() {
        // Use app-accessible external storage (same as ExtractionService)
        baseDir = new File(getContext().getExternalFilesDir(null), "extracted");
        if (!baseDir.exists()) baseDir.mkdirs();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String last = uri.getLastPathSegment();
        if (last == null) return null;
        
        // File listing fallback
        if ("list".equals(last)) {
            MatrixCursor cursor = new MatrixCursor(COLUMNS);
            File[] files = baseDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    cursor.addRow(new Object[]{f.getName(), f.length(), new Date(f.lastModified()).getTime()});
                }
            }
            return cursor;
        }
        
        // Structured endpoints similar to Oxygen agent
        if ("contacts".equals(last)) {
            MatrixCursor mc = new MatrixCursor(new String[]{"id", "name", "phone"});
            Cursor c = getContext().getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    }, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));
                        String name = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        String phone = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        mc.addRow(new Object[]{id, name, phone});
                    }
                } finally { c.close(); }
            }
            return mc;
        } else if ("sms".equals(last)) {
            MatrixCursor mc = new MatrixCursor(new String[]{"address", "body", "date", "type"});
            Cursor c = getContext().getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE},
                    null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        mc.addRow(new Object[]{
                                c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                                c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                                c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                                c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                        });
                    }
                } finally { c.close(); }
            }
            return mc;
        } else if ("calls".equals(last)) {
            MatrixCursor mc = new MatrixCursor(new String[]{"number", "date", "duration", "type"});
            Cursor c = getContext().getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE},
                    null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        mc.addRow(new Object[]{
                                c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)),
                                c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE)),
                                c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)),
                                c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                        });
                    }
                } finally { c.close(); }
            }
            return mc;
        } else if ("calendar".equals(last)) {
            MatrixCursor mc = new MatrixCursor(new String[]{"id", "title", "dtstart", "dtend"});
            Cursor c = getContext().getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND},
                    null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        mc.addRow(new Object[]{
                                c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events._ID)),
                                c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)),
                                c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
                                c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                        });
                    }
                } finally { c.close(); }
            }
            return mc;
        }
        
        return null;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        String ext = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "json";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // content://com.forensic.agent.provider/contacts.json → open specific file
        String name = uri.getLastPathSegment();
        File target = new File(baseDir, name);
        if (!target.exists()) throw new FileNotFoundException(name);
        int fileMode = ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(target, fileMode);
    }
}
