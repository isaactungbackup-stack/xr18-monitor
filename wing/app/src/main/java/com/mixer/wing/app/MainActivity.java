package com.mixer.wing.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mixer.wing.R;
import com.mixer.wing.discovery.WingDiscovery;
import com.mixer.wing.util.WingStatusReader;
import com.mixer.wing.wapi.WApi;
import com.mixer.wing.wapi.WApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WING Mixer Control - Test App
 * 
 * Demonstrates the WING wapi communication layer.
 * Can discover devices on LAN and read channel status.
 */
public class MainActivity extends AppCompatActivity {
    
    private WingDiscovery discovery;
    private WApi wapi;
    private WingStatusReader statusReader;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // UI elements
    private Button btnDiscover;
    private Button btnConnect;
    private Button btnReadStatus;
    private ListView listDevices;
    private TextView tvStatus;
    private TextView tvConnection;
    
    private ArrayAdapter<String> deviceAdapter;
    private List<String> deviceStrings = new ArrayList<>();
    private List<WingDiscovery.WingDevice> devices = new ArrayList<>();
    private WingDiscovery.WingDevice selectedDevice;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupListeners();
        
        discovery = new WingDiscovery();
    }
    
    private void initViews() {
        btnDiscover = findViewById(R.id.btn_discover);
        btnConnect = findViewById(R.id.btn_connect);
        btnReadStatus = findViewById(R.id.btn_read_status);
        listDevices = findViewById(R.id.list_devices);
        tvStatus = findViewById(R.id.tv_status);
        tvConnection = findViewById(R.id.tv_connection);
        
        deviceAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_single_choice,
            deviceStrings);
        listDevices.setAdapter(deviceAdapter);
    }
    
    private void setupListeners() {
        btnDiscover.setOnClickListener(v -> discoverDevices());
        btnConnect.setOnClickListener(v -> connectToDevice());
        btnReadStatus.setOnClickListener(v -> readStatus());
        
        btnConnect.setEnabled(false);
        btnReadStatus.setEnabled(false);
    }
    
    private void discoverDevices() {
        btnDiscover.setEnabled(false);
        tvStatus.setText("Discovering...");
        deviceStrings.clear();
        devices.clear();
        deviceAdapter.notifyDataSetChanged();
        
        executor.execute(() -> {
            try {
                List<WingDiscovery.WingDevice> found = discovery.discover();
                
                mainHandler.post(() -> {
                    deviceStrings.clear();
                    devices.clear();
                    
                    for (WingDiscovery.WingDevice d : found) {
                        deviceStrings.add(d.toString());
                        devices.add(d);
                    }
                    
                    deviceAdapter.notifyDataSetChanged();
                    
                    if (found.isEmpty()) {
                        tvStatus.setText("No WING devices found");
                    } else {
                        tvStatus.setText("Found " + found.size() + " device(s)");
                        btnConnect.setEnabled(true);
                    }
                    
                    btnDiscover.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("Discovery failed: " + e.getMessage());
                    btnDiscover.setEnabled(true);
                });
            }
        });
    }
    
    private void connectToDevice() {
        int pos = listDevices.getCheckedItemPosition();
        if (pos < 0 || pos >= devices.size()) {
            Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        selectedDevice = devices.get(pos);
        
        btnConnect.setEnabled(false);
        btnReadStatus.setEnabled(false);
        tvStatus.setText("Connecting to " + selectedDevice.ip + "...");
        
        executor.execute(() -> {
            try {
                wapi = new WApi(selectedDevice.ip);
                String version = wapi.open();
                statusReader = new WingStatusReader(wapi);
                
                mainHandler.post(() -> {
                    tvConnection.setText("Connected to " + selectedDevice.name 
                        + " (FW: " + selectedDevice.firmware + ")");
                    tvStatus.setText("Connected! Version: " + version);
                    btnConnect.setText("Disconnect");
                    btnConnect.setEnabled(true);
                    btnReadStatus.setEnabled(true);
                    btnConnect.setOnClickListener(v -> disconnect());
                });
            } catch (WApiException e) {
                mainHandler.post(() -> {
                    tvStatus.setText("Connection failed: " + e.getMessage());
                    btnConnect.setEnabled(true);
                    btnConnect.setText("Connect");
                });
            }
        });
    }
    
    private void disconnect() {
        if (wapi != null) {
            wapi.close();
            wapi = null;
            statusReader = null;
        }
        
        tvConnection.setText("Not connected");
        btnConnect.setText("Connect");
        btnConnect.setEnabled(true);
        btnReadStatus.setEnabled(false);
        tvStatus.setText("Disconnected");
        btnConnect.setOnClickListener(v -> connectToDevice());
    }
    
    private void readStatus() {
        if (statusReader == null) {
            tvStatus.setText("Not connected");
            return;
        }
        
        btnReadStatus.setEnabled(false);
        tvStatus.setText("Reading channel status...");
        
        executor.execute(() -> {
            try {
                String status = statusReader.readAllChannels();
                
                mainHandler.post(() -> {
                    tvStatus.setText(status);
                    btnReadStatus.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("Read failed: " + e.getMessage());
                    btnReadStatus.setEnabled(true);
                });
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wapi != null) {
            wapi.close();
        }
        executor.shutdown();
    }
}