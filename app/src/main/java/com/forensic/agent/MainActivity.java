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
        // Only essential permissions are required to proceed
        String[] essential;
        if (Build.VERSION.SDK_INT >= 34) {
            essential = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essential = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR
            };
        } else {
            essential = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR
            };
        }
        
        for (String permission : essential) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void requestPermissions() {
        // Essential + optional permissions
        String[] essential = new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR
        };
        String[] optional;
        if (Build.VERSION.SDK_INT >= 34) {
            optional = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            optional = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            optional = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        
        // Combine lists
        String[] all = new String[essential.length + optional.length];
        System.arraycopy(essential, 0, all, 0, essential.length);
        System.arraycopy(optional, 0, all, essential.length, optional.length);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, all, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Define essential set
            Set<String> essential = new HashSet<>(Arrays.asList(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CALENDAR
            ));
            
            int essentialTotal = 0, essentialGranted = 0;
            int optionalTotal = 0, optionalGranted = 0;
            boolean anyPermanentlyDenied = false;
            
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                boolean isEssential = essential.contains(permissions[i]);
                if (isEssential) {
                    essentialTotal++;
                    if (granted) essentialGranted++;
                    // Permanently denied check (user selected "Don't ask again")
                    if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        anyPermanentlyDenied = true;
                    }
                } else {
                    optionalTotal++;
                    if (granted) optionalGranted++;
                }
            }
            
            if (essentialGranted == essentialTotal) {
                Toast.makeText(this, "Essential permissions granted! Ready to extract", Toast.LENGTH_LONG).show();
                statusText.setText("Ready to extract!\nClick EXTRACT DATA button\n\nEssential: " + essentialGranted + "/" + essentialTotal +
                        "\nOptional: " + optionalGranted + "/" + optionalTotal);
            } else {
                String msg = "Essential: " + essentialGranted + "/" + essentialTotal +
                             "\nOptional: " + optionalGranted + "/" + optionalTotal +
                             "\n\nExtraction may be incomplete";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                statusText.setText("Warning: Missing essential permissions\n" + msg +
                        (anyPermanentlyDenied ? "\n\nSome permissions permanently denied. Open Settings to enable." : ""));
                
                if (anyPermanentlyDenied) {
                    // Open app settings so user can enable manually
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
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
