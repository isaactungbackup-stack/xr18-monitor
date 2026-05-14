package com.mixer.xr18.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.mixer.xr18.app.R;
import com.mixer.xr18.lib.domain.model.ChannelState;
import com.mixer.xr18.lib.domain.model.MixerDevice;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;
    private TextView tvOscLog;
    private EditText etIp;
    private Button btnConnectIp;
    private Button btnDiscover;
    private Button btnQuery;
    private Button btnDebug;
    private Button btnClearLog;
    private Button btnCopyLog;
    private LinearLayout channelsContainer;
    private MixerDevice connectedDevice;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Cached channel states for UI update
    private final ChannelState[] channelStates = new ChannelState[16];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        // Pre-init channel states with defaults
        for (int i = 0; i < 16; i++) {
            channelStates[i] = new ChannelState(i + 1);
        }
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvOscLog = findViewById(R.id.tv_osc_log);
        etIp = findViewById(R.id.et_ip);
        btnConnectIp = findViewById(R.id.btn_connect_ip);
        btnDiscover = findViewById(R.id.btn_discover);
        btnQuery = findViewById(R.id.btn_query);
        btnDebug = findViewById(R.id.btn_debug);
        btnClearLog = findViewById(R.id.btn_clear_log);
        btnCopyLog = findViewById(R.id.btn_copy_log);
        channelsContainer = findViewById(R.id.channels_container);

        tvStatus.setText("XR18 Mixer V1.0061\nEnter IP or search broadcast");
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
                        tvStatus.setText("Connected to " + ip + "!\n" + DiscoveryHelper.getDebugMessages());
                        btnQuery.setEnabled(true);
                    } else {
                        tvStatus.setText("Cannot reach " + ip + "\nCheck network connection\n" + DiscoveryHelper.getDebugMessages());
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
                        tvStatus.setText("No XR18 found\n" + DiscoveryHelper.getDebugMessages());
                        showDemoData();
                    } else {
                        connectedDevice = devices.get(0);
                        etIp.setText(connectedDevice.getIpAddress());
                        tvStatus.setText("Found: " + connectedDevice.getName() + "\nIP: " + connectedDevice.getIpAddress() + "\n" + DiscoveryHelper.getDebugMessages());
                        btnQuery.setEnabled(true);
                    }
                });
            });
        });

        btnQuery.setOnClickListener(v -> {
            if (connectedDevice != null) {
                tvStatus.setText("Querying channels...\n" + DiscoveryHelper.getDebugMessages());

                // Clear previous log
                final StringBuilder oscLog = new StringBuilder(8192);
                channelsContainer.removeAllViews();
                channelsContainer.setVisibility(View.VISIBLE);

                // Build channel rows immediately (with default values)
                buildChannelRows();

                DiscoveryHelper.setLogConsumer(msg -> {
                    mainHandler.post(() -> {
                        oscLog.append(msg).append("\n");
                        String logText = oscLog.toString();
                        String[] lines = logText.split("\n");
                        if (lines.length > 500) {
                            StringBuilder trimmed = new StringBuilder();
                            for (int i = lines.length - 500; i < lines.length; i++) {
                                trimmed.append(lines[i]).append("\n");
                            }
                            tvOscLog.setText(trimmed.toString());
                        } else {
                            tvOscLog.setText(logText);
                        }
                    });
                });

                DiscoveryHelper.queryChannels(connectedDevice, result -> {
                    mainHandler.post(() -> {
                        String debug = DiscoveryHelper.getDebugMessages();
                        if (result != null && result.length > 0) {
                            updateChannelRows(result);
                            tvStatus.setText("Channel data received!\n" + debug);
                        } else {
                            tvStatus.setText("No response from mixer\n" + debug);
                        }
                    });
                });
            } else {
                tvStatus.setText("No device connected");
            }
        });

        btnDebug.setOnClickListener(v -> {
            tvOscLog.setText(DiscoveryHelper.getDebugMessages());
        });

        btnClearLog.setOnClickListener(v -> {
            tvOscLog.setText("");
        });

        btnCopyLog.setOnClickListener(v -> {
            String log = tvOscLog.getText().toString();
            if (!log.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("XR18 Log", log);
                cm.setPrimaryClip(clip);
                android.widget.Toast.makeText(this, "Log copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildChannelRows() {
        channelsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < 16; i++) {
            View row = inflater.inflate(R.layout.channel_row, channelsContainer, false);

            TextView tvChNum = row.findViewById(R.id.tv_ch_num);
            SeekBar sbFader = row.findViewById(R.id.sb_fader);
            TextView tvDb = row.findViewById(R.id.tv_db);
            Button btnMute = row.findViewById(R.id.btn_mute);

            int chNum = i + 1;
            tvChNum.setText(String.format("CH%02d", chNum));

            ChannelState cs = channelStates[i];
            sbFader.setProgress((int) (cs.fader * 100f));
            tvDb.setText(cs.faderDbString());

            updateMuteButton(btnMute, cs.muted);

            // Mute button toggle (visual only for now — no command sent)
            final int ch = chNum;
            btnMute.setOnClickListener(v -> {
                // Toggle local state (UI demo)
                boolean newMuted = !channelStates[ch - 1].muted;
                channelStates[ch - 1] = channelStates[ch - 1].withMuted(newMuted);
                updateMuteButton((Button) v, newMuted);
                Toast.makeText(this, "CH" + String.format("%02d", ch) + " Mute: " + (newMuted ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            });

            channelsContainer.addView(row);
        }
    }

    private void updateChannelRows(ChannelState[] states) {
        if (states == null) return;

        for (ChannelState cs : states) {
            int idx = cs.channelNumber - 1;
            if (idx < 0 || idx >= 16) continue;

            channelStates[idx] = cs;

            View row = channelsContainer.getChildAt(idx);
            if (row == null) continue;

            SeekBar sb = row.findViewById(R.id.sb_fader);
            TextView tvDb = row.findViewById(R.id.tv_db);
            Button btnMute = row.findViewById(R.id.btn_mute);

            int progress = (int) (cs.fader * 100f);
            sb.setProgress(progress);
            tvDb.setText(cs.faderDbString());
            updateMuteButton(btnMute, cs.muted);
        }
    }

    private void updateMuteButton(Button btn, boolean muted) {
        if (muted) {
            btn.setBackgroundColor(0xFFFF5722);  // orange
            btn.setTextColor(Color.WHITE);
            btn.setText("MUT");
        } else {
            btn.setBackgroundColor(0xFF333333);  // dark gray
            btn.setTextColor(Color.parseColor("#CCCCCC"));
            btn.setText("MUT");
        }
    }

    private void showDemoData() {
        channelsContainer.setVisibility(View.VISIBLE);
        buildChannelRows();
        // Fill with demo values
        channelStates[2] = channelStates[2].withMuted(true);
        channelStates[7] = channelStates[7].withMuted(true);
        for (int i = 0; i < 16; i++) {
            View row = channelsContainer.getChildAt(i);
            if (row == null) continue;
            ChannelState cs = channelStates[i];
            ((SeekBar) row.findViewById(R.id.sb_fader)).setProgress((int) (cs.fader * 100f));
            ((TextView) row.findViewById(R.id.tv_db)).setText(cs.faderDbString());
            updateMuteButton((Button) row.findViewById(R.id.btn_mute), cs.muted);
        }
        tvStatus.setText("Demo Mode — no mixer found");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}