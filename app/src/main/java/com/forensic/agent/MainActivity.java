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
import android.net.Uri;
import android.provider.Settings;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "ForensicAgent";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private TextView statusText;
    private Button extractButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() called");
        setContentView(R.layout.activity_main);
        
        statusText = findViewById(R.id.statusText);
        extractButton = findViewById(R.id.extractButton);
        
        statusText.setText("Forensic Agent Ready\nVersion 1.0\n\nTap EXTRACT DATA to start");
        
        extractButton.setOnClickListener(v -> {
            Log.d(TAG, "Extract button clicked");
            // ALWAYS check and request permissions when button clicked
            if (checkPermissions()) {
                Log.d(TAG, "Permissions OK, starting extraction");
                startExtraction();
            } else {
                Log.d(TAG, "Permissions missing, requesting...");
                // Force permission request on button click
                Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show();
                requestPermissions();
            }
        });
        
        // Auto-request on first launch to ensure App Info reflects permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkPermissions()) {
            Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show();
            statusText.postDelayed(this::requestPermissions, 300);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called");
        // After returning from Settings, re-check essential permissions
        if (!checkPermissions()) {
            statusText.setText("Permissions missing. Tap EXTRACT DATA to grant or enable in Settings.");
        } else {
            statusText.setText("All permissions granted!\nTap EXTRACT DATA to start extraction");
        }
    }

    private boolean checkPermissions() {
        Log.d(TAG, "checkPermissions() called");
        // Only essential permissions are required to proceed
        String[] essential = new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR
        };
        
        boolean allGranted = true;
        for (String permission : essential) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            Log.d(TAG, "Permission " + permission + ": " + (result == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
            }
        }
        
        Log.d(TAG, "checkPermissions() result: " + allGranted);
        return allGranted;
    }
    
    private void requestPermissions() {
        Log.d(TAG, "requestPermissions() called");
        // Essential permissions only
        String[] permissions = new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR
        };
        
        Log.d(TAG, "Requesting " + permissions.length + " permissions");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult() called with requestCode: " + requestCode);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            int grantedCount = 0;
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, "Permission " + permissions[i] + ": " + (granted ? "GRANTED" : "DENIED"));
                if (granted) {
                    grantedCount++;
                }
            }
            
            String msg = "Permissions: " + grantedCount + "/" + permissions.length + " granted";
            Log.d(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            
            if (grantedCount == permissions.length) {
                statusText.setText("All permissions granted!\nTap EXTRACT DATA to start extraction");
            } else {
                statusText.setText("Warning: " + grantedCount + "/" + permissions.length + " permissions granted\n\nExtraction may be incomplete");
                
                // If some permissions were permanently denied, show a message
                boolean anyPermanentlyDenied = false;
                String[] essential = new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CALENDAR
                };
                
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                            anyPermanentlyDenied = true;
                            break;
                        }
                    }
                }
                
                if (anyPermanentlyDenied) {
                    statusText.append("\n\nSome permissions permanently denied. Please enable them in Settings.");
                    Toast.makeText(this, "Some permissions permanently denied. Please enable them in Settings.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    
    private void startExtraction() {
        Log.d(TAG, "startExtraction() called");
        if (!checkPermissions()) {
            Log.e(TAG, "Permissions not granted");
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show();
            requestPermissions();
            return;
        }
        
        Log.d(TAG, "Starting extraction service");
        Toast.makeText(this, "Starting data extraction...", Toast.LENGTH_SHORT).show();
        
        Intent serviceIntent = new Intent(this, ExtractionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        String appDataPath = "/sdcard/Android/data/" + getPackageName() + "/files/extracted/";
        statusText.setText("Extraction in progress...\n\nData saving to:\n" + appDataPath + "\n\nAccessible via Content Provider\n\nCheck logcat for details");
        extractButton.setEnabled(false);
        extractButton.setText("EXTRACTING...");
        
        // Re-enable button after 45 seconds
        extractButton.postDelayed(() -> {
            extractButton.setEnabled(true);
            extractButton.setText("EXTRACT DATA");
            statusText.setText("Extraction complete!\n\nCheck logcat for errors\n\nQuery via:\nadb shell content query --uri content://com.forensic.agent.provider/list");
            Toast.makeText(this, "Extraction complete! Check logcat for details", Toast.LENGTH_LONG).show();
        }, 45000);
    }
}