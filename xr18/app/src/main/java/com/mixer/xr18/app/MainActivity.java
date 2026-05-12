package com.mixer.xr18.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.mixer.xr18.lib.domain.model.MixerDevice;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    
    private TextView tvStatus;
    private TextView tvResult;
    private EditText etIp;
    private Button btnConnectIp;
    private Button btnDiscover;
    private Button btnQuery;
    private MixerDevice connectedDevice;
    
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
        etIp = findViewById(R.id.et_ip);
        btnConnectIp = findViewById(R.id.btn_connect_ip);
        btnDiscover = findViewById(R.id.btn_discover);
        btnQuery = findViewById(R.id.btn_query);
        
        tvStatus.setText("XR18 Mixer V1.0014\nEnter IP or search broadcast");
    }
    
    private void setupListeners() {
        btnConnectIp.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            
            tvStatus.setText("Connecting to " + ip + "...");
            btnConnectIp.setEnabled(false);
            
            executor.execute(() -> {
                boolean success = DiscoveryHelper.connectToIpSync(ip);
                
                mainHandler.post(() -> {
                    btnConnectIp.setEnabled(true);
                    
                    if (success) {
                        connectedDevice = DiscoveryHelper.createDevice(ip, "XR18", "XR18", "unknown");
                        tvStatus.setText("Connected to " + ip + "!");
                        btnQuery.setEnabled(true);
                    } else {
                        tvStatus.setText("Cannot reach " + ip + "\nCheck network connection");
                    }
                });
            });
        });
        
        btnDiscover.setOnClickListener(v -> {
            tvStatus.setText("Searching for XR18...");
            btnDiscover.setEnabled(false);
            btnQuery.setEnabled(false);
            
            executor.execute(() -> {
                List<MixerDevice> devices = DiscoveryHelper.discoverSync();
                
                mainHandler.post(() -> {
                    btnDiscover.setEnabled(true);
                    
                    if (devices.isEmpty()) {
                        tvStatus.setText("No XR18 found - Demo Mode");
                        showDemoData();
                    } else {
                        connectedDevice = devices.get(0);
                        etIp.setText(connectedDevice.getIpAddress());
                        tvStatus.setText("Found: " + connectedDevice.getName() + "\nIP: " + connectedDevice.getIpAddress());
                        btnQuery.setEnabled(true);
                    }
                });
            });
        });
        
        btnQuery.setOnClickListener(v -> {
            if (connectedDevice != null) {
                tvStatus.setText("Querying channels...");
                DiscoveryHelper.queryChannels(connectedDevice, result -> {
                    mainHandler.post(() -> {
                        if (result != null) {
                            tvResult.setText(result);
                            tvStatus.setText("Channel data received!");
                        } else {
                            tvResult.setText("No response from mixer\nShowing demo data");
                            showDemoData();
                        }
                    });
                });
            } else {
                tvStatus.setText("No device connected");
            }
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
        btnQuery.setEnabled(true);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
