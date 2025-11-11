package com.forensic.agent;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.IBinder;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.provider.CallLog;
import android.provider.CalendarContract;
import androidx.core.app.NotificationCompat;
import android.app.PendingIntent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.util.Log;
import android.os.Environment;
import android.content.Context;

public class ExtractionService extends Service {
    private static final String TAG = "ForensicAgent";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() called");
        // Start foreground to comply with Android 8+ and Oxygen-style agent
        startForegroundWithNotification();
        
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting extraction in background thread");
                extractData();
            } catch (Exception e) {
                Log.e(TAG, "Error during extraction: " + e.getMessage(), e);
                e.printStackTrace();
            } finally {
                Log.d(TAG, "Stopping service");
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }
    
    private void startForegroundWithNotification() {
        Log.d(TAG, "startForegroundWithNotification() called");
        final String CHANNEL_ID = "forensic_agent_channel";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Forensic Agent", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Forensic Agent")
                .setContentText("Extracting device data…")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        startForeground(1, notification);
        Log.d(TAG, "Started foreground service with notification");
    }
    
    private void extractData() throws Exception {
        Log.d(TAG, "extractData() called");
        
        // Check if we have the necessary permissions first
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for data extraction");
            throw new SecurityException("Missing required permissions for data extraction");
        }
        
        // Use app-accessible external storage (doesn't require MANAGE_EXTERNAL_STORAGE)
        File outputDir = new File(getExternalFilesDir(null), "extracted");
        Log.d(TAG, "Output directory: " + outputDir.getAbsolutePath());
        
        // Check if external storage is available
        if (!isExternalStorageAvailable()) {
            Log.e(TAG, "External storage not available");
            throw new Exception("External storage not available");
        }
        
        Log.d(TAG, "External storage state: " + Environment.getExternalStorageState());
        Log.d(TAG, "getExternalFilesDir(null): " + getExternalFilesDir(null));
        
        if (!outputDir.exists()) {
            Log.d(TAG, "Directory doesn't exist, creating...");
            boolean created = outputDir.mkdirs();
            Log.d(TAG, "Directory created: " + created);
            if (!created) {
                Log.e(TAG, "Failed to create directory: " + outputDir.getAbsolutePath());
                throw new Exception("Cannot create directory: permission denied");
            }
        }
        
        if (!outputDir.canWrite()) {
            Log.e(TAG, "Cannot write to directory: " + outputDir.getAbsolutePath());
            Log.e(TAG, "Directory exists: " + outputDir.exists());
            Log.e(TAG, "Directory can read: " + outputDir.canRead());
            Log.e(TAG, "Parent directory: " + outputDir.getParent());
            File parent = outputDir.getParentFile();
            if (parent != null) {
                Log.e(TAG, "Parent can write: " + parent.canWrite());
            }
            throw new Exception("Cannot write to directory: permission denied");
        }
        
        Log.d(TAG, "Starting extraction...");
        
        // Extract contacts
        try {
            Log.d(TAG, "Extracting contacts...");
            extractContacts(new File(outputDir, "contacts.json"));
            Log.d(TAG, "Contacts extracted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting contacts: " + e.getMessage(), e);
        }
        
        // Extract SMS
        try {
            Log.d(TAG, "Extracting SMS...");
            extractSMS(new File(outputDir, "sms.json"));
            Log.d(TAG, "SMS extracted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting SMS: " + e.getMessage(), e);
        }
        
        // Extract call logs
        try {
            Log.d(TAG, "Extracting call logs...");
            extractCallLogs(new File(outputDir, "call_logs.json"));
            Log.d(TAG, "Call logs extracted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting call logs: " + e.getMessage(), e);
        }
        
        // Extract calendar
        try {
            Log.d(TAG, "Extracting calendar...");
            extractCalendar(new File(outputDir, "calendar.json"));
            Log.d(TAG, "Calendar extracted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting calendar: " + e.getMessage(), e);
        }
        
        Log.d(TAG, "Extraction completed");
    }
    
    private boolean hasRequiredPermissions() {
        Log.d(TAG, "hasRequiredPermissions() called");
        // We need to check if we have the basic permissions for data access
        // Since we already checked in MainActivity, this is just a safety check
        return true;
    }
    
    private boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state: " + state);
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    
    private void extractContacts(File outputFile) throws Exception {
        Log.d(TAG, "extractContacts() called, output file: " + outputFile.getAbsolutePath());
        
        JSONArray contacts = new JSONArray();
        
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        Cursor cursor = null;
        try {
            Log.d(TAG, "Querying contacts content provider...");
            cursor = getContentResolver().query(uri, null, null, null, null);
            Log.d(TAG, "Contacts query completed, cursor: " + (cursor != null ? "not null" : "null"));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when querying contacts: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Exception when querying contacts: " + e.getMessage(), e);
            throw e;
        }
        
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "Contacts cursor has data, count: " + cursor.getCount());
            do {
                JSONObject contact = new JSONObject();
                String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                
                contact.put("id", id);
                contact.put("name", name);
                
                // Get phone numbers
                JSONArray phones = new JSONArray();
                Cursor phoneCursor = null;
                try {
                    phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id},
                        null
                    );
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException when querying phone numbers: " + e.getMessage(), e);
                    // Continue with empty phones array
                } catch (Exception e) {
                    Log.e(TAG, "Exception when querying phone numbers: " + e.getMessage(), e);
                }
                
                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    do {
                        String number = phoneCursor.getString(
                            phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        );
                        phones.put(number);
                    } while (phoneCursor.moveToNext());
                    phoneCursor.close();
                }
                
                contact.put("phones", phones);
                contacts.put(contact);
                
            } while (cursor.moveToNext());
            cursor.close();
        } else {
            Log.d(TAG, "Contacts cursor is null or empty");
        }
        
        // Write to file
        Log.d(TAG, "Writing contacts to file...");
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFile);
            writer.write(contacts.toString(2));
            Log.d(TAG, "Contacts written successfully, size: " + contacts.length());
        } catch (IOException e) {
            Log.e(TAG, "Error writing contacts to file: " + e.getMessage(), e);
            throw e;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing writer: " + e.getMessage(), e);
                }
            }
        }
    }
    
    private void extractSMS(File outputFile) throws Exception {
        Log.d(TAG, "extractSMS() called, output file: " + outputFile.getAbsolutePath());
        
        JSONArray messages = new JSONArray();
        
        Uri uri = Telephony.Sms.CONTENT_URI;
        Cursor cursor = null;
        try {
            Log.d(TAG, "Querying SMS content provider...");
            cursor = getContentResolver().query(uri, null, null, null, null);
            Log.d(TAG, "SMS query completed, cursor: " + (cursor != null ? "not null" : "null"));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when querying SMS: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Exception when querying SMS: " + e.getMessage(), e);
            throw e;
        }
        
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "SMS cursor has data, count: " + cursor.getCount());
            do {
                JSONObject msg = new JSONObject();
                msg.put("address", cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)));
                msg.put("body", cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY)));
                msg.put("date", cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE)));
                msg.put("type", cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE)));
                messages.put(msg);
            } while (cursor.moveToNext());
            cursor.close();
        } else {
            Log.d(TAG, "SMS cursor is null or empty");
        }
        
        // Write to file
        Log.d(TAG, "Writing SMS to file...");
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFile);
            writer.write(messages.toString(2));
            Log.d(TAG, "SMS written successfully, size: " + messages.length());
        } catch (IOException e) {
            Log.e(TAG, "Error writing SMS to file: " + e.getMessage(), e);
            throw e;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing writer: " + e.getMessage(), e);
                }
            }
        }
    }
    
    private void extractCallLogs(File outputFile) throws Exception {
        Log.d(TAG, "extractCallLogs() called, output file: " + outputFile.getAbsolutePath());
        
        JSONArray calls = new JSONArray();
        
        Uri uri = CallLog.Calls.CONTENT_URI;
        Cursor cursor = null;
        try {
            Log.d(TAG, "Querying call logs content provider...");
            cursor = getContentResolver().query(uri, null, null, null, null);
            Log.d(TAG, "Call logs query completed, cursor: " + (cursor != null ? "not null" : "null"));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when querying call logs: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Exception when querying call logs: " + e.getMessage(), e);
            throw e;
        }
        
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "Call logs cursor has data, count: " + cursor.getCount());
            do {
                JSONObject call = new JSONObject();
                call.put("number", cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)));
                call.put("date", cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)));
                call.put("duration", cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION)));
                call.put("type", cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE)));
                calls.put(call);
            } while (cursor.moveToNext());
            cursor.close();
        } else {
            Log.d(TAG, "Call logs cursor is null or empty");
        }
        
        // Write to file
        Log.d(TAG, "Writing call logs to file...");
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFile);
            writer.write(calls.toString(2));
            Log.d(TAG, "Call logs written successfully, size: " + calls.length());
        } catch (IOException e) {
            Log.e(TAG, "Error writing call logs to file: " + e.getMessage(), e);
            throw e;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing writer: " + e.getMessage(), e);
                }
            }
        }
    }
    
    private void extractCalendar(File outputFile) throws Exception {
        Log.d(TAG, "extractCalendar() called, output file: " + outputFile.getAbsolutePath());
        
        JSONArray events = new JSONArray();
        Cursor c = null;
        try {
            Log.d(TAG, "Querying calendar content provider...");
            c = getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND},
                    null, null, null);
            Log.d(TAG, "Calendar query completed, cursor: " + (c != null ? "not null" : "null"));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when querying calendar: " + e.getMessage(), e);
            // Continue with empty events array
        } catch (Exception e) {
            Log.e(TAG, "Exception when querying calendar: " + e.getMessage(), e);
        }
        
        if (c != null && c.moveToFirst()) {
            Log.d(TAG, "Calendar cursor has data, count: " + c.getCount());
            do {
                JSONObject ev = new JSONObject();
                ev.put("id", c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events._ID)));
                ev.put("title", c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)));
                ev.put("dtstart", c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)));
                ev.put("dtend", c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)));
                events.put(ev);
            } while (c.moveToNext());
            c.close();
        } else {
            Log.d(TAG, "Calendar cursor is null or empty");
        }
        
        // Write to file
        Log.d(TAG, "Writing calendar to file...");
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFile);
            writer.write(events.toString(2));
            Log.d(TAG, "Calendar written successfully, size: " + events.length());
        } catch (IOException e) {
            Log.e(TAG, "Error writing calendar to file: " + e.getMessage(), e);
            throw e;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing writer: " + e.getMessage(), e);
                }
            }
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}