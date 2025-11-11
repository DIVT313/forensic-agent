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
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
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
    
    private void extractData() {
        Log.d(TAG, "extractData() called");
        
        try {
            // Use app-accessible external storage (doesn't require MANAGE_EXTERNAL_STORAGE)
            File outputDir = new File(getExternalFilesDir(null), "extracted");
            Log.d(TAG, "Output directory: " + outputDir.getAbsolutePath());
            
            // Create directory if it doesn't exist
            if (!outputDir.exists()) {
                Log.d(TAG, "Directory doesn't exist, creating...");
                boolean created = outputDir.mkdirs();
                Log.d(TAG, "Directory created: " + created);
                if (!created) {
                    Log.e(TAG, "Failed to create directory: " + outputDir.getAbsolutePath());
                    return;
                }
            }
            
            Log.d(TAG, "Starting extraction...");
            
            // Extract all data types
            extractContacts(new File(outputDir, "contacts.json"));
            extractSMS(new File(outputDir, "sms.json"));
            extractCallLogs(new File(outputDir, "call_logs.json"));
            extractCalendar(new File(outputDir, "calendar.json"));
            
            Log.d(TAG, "Extraction completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during extraction: " + e.getMessage(), e);
        }
    }
    
    private void extractContacts(File outputFile) {
        Log.d(TAG, "extractContacts() called, output file: " + outputFile.getAbsolutePath());
        
        try {
            JSONArray contacts = new JSONArray();
            
            Uri uri = ContactsContract.Contacts.CONTENT_URI;
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            
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
                    Cursor phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id},
                        null
                    );
                    
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
            }
            
            // Write to file
            FileWriter writer = new FileWriter(outputFile);
            writer.write(contacts.toString(2));
            writer.close();
            Log.d(TAG, "Contacts written successfully, size: " + contacts.length());
        } catch (Exception e) {
            Log.e(TAG, "Error extracting contacts: " + e.getMessage(), e);
        }
    }
    
    private void extractSMS(File outputFile) {
        Log.d(TAG, "extractSMS() called, output file: " + outputFile.getAbsolutePath());
        
        try {
            JSONArray messages = new JSONArray();
            
            Uri uri = Telephony.Sms.CONTENT_URI;
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            
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
            }
            
            // Write to file
            FileWriter writer = new FileWriter(outputFile);
            writer.write(messages.toString(2));
            writer.close();
            Log.d(TAG, "SMS written successfully, size: " + messages.length());
        } catch (Exception e) {
            Log.e(TAG, "Error extracting SMS: " + e.getMessage(), e);
        }
    }
    
    private void extractCallLogs(File outputFile) {
        Log.d(TAG, "extractCallLogs() called, output file: " + outputFile.getAbsolutePath());
        
        try {
            JSONArray calls = new JSONArray();
            
            Uri uri = CallLog.Calls.CONTENT_URI;
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            
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
            }
            
            // Write to file
            FileWriter writer = new FileWriter(outputFile);
            writer.write(calls.toString(2));
            writer.close();
            Log.d(TAG, "Call logs written successfully, size: " + calls.length());
        } catch (Exception e) {
            Log.e(TAG, "Error extracting call logs: " + e.getMessage(), e);
        }
    }
    
    private void extractCalendar(File outputFile) {
        Log.d(TAG, "extractCalendar() called, output file: " + outputFile.getAbsolutePath());
        
        try {
            JSONArray events = new JSONArray();
            Cursor c = getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND},
                    null, null, null);
            
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
            }
            
            // Write to file
            FileWriter writer = new FileWriter(outputFile);
            writer.write(events.toString(2));
            writer.close();
            Log.d(TAG, "Calendar written successfully, size: " + events.length());
        } catch (Exception e) {
            Log.e(TAG, "Error extracting calendar: " + e.getMessage(), e);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}