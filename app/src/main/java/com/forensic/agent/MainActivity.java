package com.forensic.agent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    
    private static final int PERMISSION_REQUEST_CODE = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        TextView statusText = findViewById(R.id.statusText);
        Button extractButton = findViewById(R.id.extractButton);
        
        statusText.setText("Forensic Agent Ready\nVersion 1.0");
        
        extractButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startExtraction();
            } else {
                requestPermissions();
            }
        });
    }
    
    private boolean checkPermissions() {
        String[] permissions = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }
    
    private void startExtraction() {
        Intent serviceIntent = new Intent(this, ExtractionService.class);
        startService(serviceIntent);
        
        TextView statusText = findViewById(R.id.statusText);
        statusText.setText("Extraction started...\nData will be saved to /sdcard/ForensicAgent/");
    }
}
