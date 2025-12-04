package com.example.posprint;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleDeepLink(getIntent());
    }

    private void handleDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            Log.d(TAG, "Received deep link: " + data);

            // Start your background printing
            Intent svc = new Intent(this, BackgroundPrintService.class);
            svc.setData(data);
            startService(svc);

            finish(); // Close instantly
        } else {
            finish();
        }
    }
}
