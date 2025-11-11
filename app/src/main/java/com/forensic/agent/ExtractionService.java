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
import androidx.core.app.NotificationCompat;
import android.app.PendingIntent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import android.os.Environment;

    private void startForegroundWithNotification() {
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
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground to comply with Android 8+ and Oxygen-style agent
        startForegroundWithNotification();
        
        new Thread(() -> {
            try {
                extractData();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }
    
    private void extractData() throws Exception {
        // Use app-accessible external storage (doesn't require MANAGE_EXTERNAL_STORAGE)
        File outputDir = new File(getExternalFilesDir(null), "extracted");
        if (!outputDir.exists()) outputDir.mkdirs();
        
        // Extract contacts
        extractContacts(new File(outputDir, "contacts.json"));
        
        // Extract SMS
        extractSMS(new File(outputDir, "sms.json"));
        
        // Extract call logs
        extractCallLogs(new File(outputDir, "call_logs.json"));
        
        // Extract calendar
        extractCalendar(new File(outputDir, "calendar.json"));
    }
    
    private void extractContacts(File outputFile) throws Exception {
        JSONArray contacts = new JSONArray();
        
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        
        if (cursor != null && cursor.moveToFirst()) {
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
        
        FileWriter writer = new FileWriter(outputFile);
        writer.write(contacts.toString(2));
        writer.close();
    }
    
    private void extractSMS(File outputFile) throws Exception {
        JSONArray messages = new JSONArray();
        
        Uri uri = Telephony.Sms.CONTENT_URI;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        
        if (cursor != null && cursor.moveToFirst()) {
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
        
        FileWriter writer = new FileWriter(outputFile);
        writer.write(messages.toString(2));
        writer.close();
    }
    
    private void extractCallLogs(File outputFile) throws Exception {
        JSONArray calls = new JSONArray();
        
        Uri uri = CallLog.Calls.CONTENT_URI;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        
        if (cursor != null && cursor.moveToFirst()) {
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
        
        FileWriter writer = new FileWriter(outputFile);
        writer.write(calls.toString(2));
        writer.close();
    }
    
    private void extractCalendar(File outputFile) throws Exception {
        JSONArray events = new JSONArray();
        Cursor c = getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND},
                null, null, null);
        if (c != null && c.moveToFirst()) {
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
        FileWriter writer = new FileWriter(outputFile);
        writer.write(events.toString(2));
        writer.close();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
