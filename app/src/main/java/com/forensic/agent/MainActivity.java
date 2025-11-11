package com.forensic.agent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
import android.os.Build;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    private TextView statusText;
    private Button extractButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusText = findViewById(R.id.statusText);
        extractButton = findViewById(R.id.extractButton);
        
        statusText.setText("Forensic Agent Ready\nVersion 1.0\n\nTap EXTRACT DATA to start");
        
        extractButton.setOnClickListener(v -> {
            // ALWAYS check and request permissions when button clicked
            if (checkPermissions()) {
                startExtraction();
            } else {
                // Force permission request on button click
                Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show();
                requestPermissions();
            }
        });
        
        // Also auto-request on first launch
        if (!checkPermissions()) {
            if (Build.VERSION.SDK_INT >= 34) {
                statusText.setText("Tap EXTRACT DATA to grant permissions\n\nAndroid 14+ Permissions needed:\n- Contacts\n- SMS\n- Call Logs\n- Calendar\n- Photos/Videos/Audio\n- Notifications");
            } else {
                statusText.setText("Tap EXTRACT DATA to grant permissions\n\nPermissions needed:\n- Contacts\n- SMS\n- Call Logs\n- Calendar\n- Storage/Media");
            }
            Toast.makeText(this, "Tap EXTRACT DATA to grant permissions", Toast.LENGTH_LONG).show();
        }
    }
    
    private boolean checkPermissions() {
        String[] permissions;
        
        // Android 14+ (API 34) requires notification permission
        if (Build.VERSION.SDK_INT >= 34) {
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        }
        // Android 13 (API 33) requires different permissions
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void requestPermissions() {
        String[] permissions;
        
        // Android 14+ (API 34) requires notification permission
        if (Build.VERSION.SDK_INT >= 34) {
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        }
        // Android 13 (API 33) requires different permissions
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Request all permissions at once
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            int grantedCount = 0;
            
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    grantedCount++;
                } else {
                    allGranted = false;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "All permissions granted! Ready to extract data", Toast.LENGTH_LONG).show();
                statusText.setText("Ready to extract!\nClick EXTRACT DATA button\n\nAll permissions granted (" + grantedCount + "/" + permissions.length + ")");
            } else {
                Toast.makeText(this, "Warning: " + grantedCount + "/" + permissions.length + " permissions granted\nExtraction may be incomplete", Toast.LENGTH_LONG).show();
                statusText.setText("Warning: Partial permissions\n" + grantedCount + "/" + permissions.length + " granted\n\nExtraction may be incomplete\nClick Extract to try anyway");
            }
        }
    }
    
    private void startExtraction() {
        if (!checkPermissions()) {
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show();
            requestPermissions();
            return;
        }
        
        Toast.makeText(this, "Starting data extraction...", Toast.LENGTH_SHORT).show();
        
        Intent serviceIntent = new Intent(this, ExtractionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        statusText.setText("Extraction in progress...\n\nData saving to:\n/sdcard/ForensicAgent/\n\nPlease wait 30-60 seconds...");
        extractButton.setEnabled(false);
        extractButton.setText("EXTRACTING...");
        
        // Re-enable button after 45 seconds
        extractButton.postDelayed(() -> {
            extractButton.setEnabled(true);
            extractButton.setText("EXTRACT DATA");
            statusText.setText("Extraction complete!\n\nData saved to:\n/sdcard/ForensicAgent/\n\nPull data via ADB:\nadb pull /sdcard/ForensicAgent/");
            Toast.makeText(this, "Extraction complete! Pull data with ADB", Toast.LENGTH_LONG).show();
        }, 45000);
    }
}
