package com.mixer.xr18.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    
    private TextView tvStatus;
    private TextView tvResult;
    private Button btnDiscover;
    private Button btnQuery;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupListeners();
    }
    
    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvResult = findViewById(R.id.tv_result);
        btnDiscover = findViewById(R.id.btn_discover);
        btnQuery = findViewById(R.id.btn_query);
        
        tvStatus.setText("XR18 Mixer V1.0011\nTap Search to discover");
    }
    
    private void setupListeners() {
        btnDiscover.setOnClickListener(v -> {
            tvStatus.setText("Searching for XR18...");
            btnDiscover.setEnabled(false);
            
            executor.execute(() -> {
                try {
                    Thread.sleep(1500);
                    mainHandler.post(() -> {
                        tvStatus.setText("Demo Mode - No XR18 found");
                        btnDiscover.setEnabled(true);
                        showDemoData();
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        tvStatus.setText("Error: " + e.getMessage());
                        btnDiscover.setEnabled(true);
                    });
                }
            });
        });
        
        btnQuery.setOnClickListener(v -> {
            tvResult.setText("Query feature coming soon");
        });
    }
    
    private void showDemoData() {
        String demo = "╔══════════════════════════════════════╗\n" +
                      "║       XR18 Demo Mode                ║\n" +
                      "╠══════════════════════════════════════╣\n" +
                      "║  CH01  Fader: 65% (-6.2dB)  Mute: OFF ║\n" +
                      "║  CH02  Fader: 70% (-4.1dB)  Mute: OFF ║\n" +
                      "║  CH03  Fader: 55% (-9.8dB)  Mute: ON  ║\n" +
                      "║  CH04  Fader: 80% (-1.2dB)  Mute: OFF ║\n" +
                      "║  CH05  Fader: 60% (-7.5dB)  Mute: OFF ║\n" +
                      "║  CH06  Fader: 75% (-2.8dB)  Mute: OFF ║\n" +
                      "║  CH07  Fader: 45% (-12dB)  Mute: OFF ║\n" +
                      "║  CH08  Fader: 85% (+0.5dB)  Mute: ON  ║\n" +
                      "║  CH09  Fader: 50% (-10dB)   Mute: OFF ║\n" +
                      "║  CH10  Fader: 55% (-8.5dB)  Mute: OFF ║\n" +
                      "║  CH11  Fader: 65% (-5.2dB)  Mute: OFF ║\n" +
                      "║  CH12  Fader: 70% (-3.8dB)  Mute: OFF ║\n" +
                      "║  CH13  Fader: 60% (-7.0dB)  Mute: OFF ║\n" +
                      "║  CH14  Fader: 75% (-2.5dB)  Mute: OFF ║\n" +
                      "║  CH15  Fader: 55% (-9.0dB)  Mute: ON  ║\n" +
                      "║  CH16  Fader: 80% (-1.0dB)  Mute: OFF ║\n" +
                      "╚══════════════════════════════════════╝\n\n" +
                      "  AUX1  Fader: 70% (-3.2dB)\n" +
                      "  AUX2  Fader: 65% (-5.5dB)";
        tvResult.setText(demo);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}