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

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    // Progress bar for query blocking
    private ProgressBar progressBar;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NetworkThread");
        t.setUncaughtExceptionHandler((th, ex) -> android.util.Log.e("MainActivity", "NetworkThread error", ex));
        return t;
    });
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LogThread");
        t.setUncaughtExceptionHandler((th, ex) -> android.util.Log.e("MainActivity", "LogThread error", ex));
        return t;
    });

    // Thread-safe log queue
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(2000);
    private final AtomicBoolean logRunning = new AtomicBoolean(false);
    private final AtomicInteger logMsgCount = new AtomicInteger(0);
    private final StringBuilder logHistory = new StringBuilder(65536);  // full history
    private final Object logLock = new Object();
    private String lastLogLine = "";
    private static final int MAX_DISPLAY_LINES = 500;  // max history lines retained;

    // Throttle state refresh to ~10fps (every 100ms)
    private final long STATE_THROTTLE_MS = 100;
    private long lastStateRefresh = 0;

    // Cached channel states for UI update
    private final ChannelState[] channelStates = new ChannelState[16];

    // Pending state update — set by network thread, applied on UI thread
    private volatile ChannelState[] pendingStates = null;

    // Weak references to row views to avoid memory leak
    private final View[] uiRefs = new View[16];

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

        // Start log consumer thread
        startLogThread();
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

        // Progress bar (hidden by default)
        progressBar = findViewById(R.id.progress_bar);

        tvStatus.setText("XR18 Mixer V1.0103\nEnter IP or search broadcast");
    }

    private void setLoading(boolean loading) {
        mainHandler.post(() -> {
            if (loading) {
                progressBar.setVisibility(View.VISIBLE);
                btnConnectIp.setEnabled(false);
                btnDiscover.setEnabled(false);
                btnQuery.setEnabled(false);
                btnDebug.setEnabled(false);
            } else {
                progressBar.setVisibility(View.GONE);
                btnConnectIp.setEnabled(true);
                btnDiscover.setEnabled(true);
                btnQuery.setEnabled(connectedDevice != null);
                btnDebug.setEnabled(true);
            }
        });
    }

    private void setupListeners() {
        btnConnectIp.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            clearLog();
            tvStatus.setText("Connecting to " + ip + "...");
            setLoading(true);

            networkExecutor.execute(() -> {
                boolean success = DiscoveryHelper.connectToIpSync(ip);
                mainHandler.post(() -> {
                    setLoading(false);
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
            clearLog();
            tvStatus.setText("Searching for XR18...");
            setLoading(true);
            btnQuery.setEnabled(false);

            networkExecutor.execute(() -> {
                List<MixerDevice> devices = DiscoveryHelper.discoverSync();
                mainHandler.post(() -> {
                    setLoading(false);
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
                clearLog();
                logMsgCount.set(0);
                lastLogLine = "";
                tvStatus.setText("Querying channels...\n");
                channelsContainer.removeAllViews();
                channelsContainer.setVisibility(View.VISIBLE);

                // Build rows immediately with default state
                buildChannelRows();

                // Disable all buttons and show spinner
                setLoading(true);

                // Log messages go to queue (network thread → log thread → UI)
                DiscoveryHelper.setLogConsumer(msg -> {
                    if (msg != null) logQueue.offer(msg);
                });

                // State updates → collect and apply on UI thread with throttle
                DiscoveryHelper.setStateUpdateListener(states -> {
                    if (states != null) {
                        pendingStates = states;
                        scheduleStateApply();
                    }
                });

                networkExecutor.execute(() -> {
                    DiscoveryHelper.queryChannels(connectedDevice, result -> {
                        mainHandler.post(() -> {
                            setLoading(false);
                            if (result != null && result.length > 0) {
                                // Per-channel staggered update: one CH every 150ms
                                staggeredUpdate(result, 0);
                                tvStatus.setText("Connected: " + connectedDevice.getIpAddress() + " | " + result.length + " ch");
                            } else {
                                tvStatus.setText("No response from mixer\n" + DiscoveryHelper.getDebugMessages());
                            }
                        });
                    });
                });
            } else {
                tvStatus.setText("No device connected");
            }
        });

        btnDebug.setOnClickListener(v -> {
            clearLog();
            tvOscLog.setText(DiscoveryHelper.getDebugMessages());
        });

        btnClearLog.setOnClickListener(v -> {
            clearLog();
        });

        btnCopyLog.setOnClickListener(v -> {
            String log;
            synchronized (logLock) {
                log = logHistory.toString();
            }
            if (!log.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("XR18 Log", log);
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void staggeredUpdate(ChannelState[] result, int index) {
        if (index >= result.length || index >= 16) {
            // Done — restore buttons
            setLoading(false);
            return;
        }

        // Apply this CH
        ChannelState cs = result[index];
        channelStates[index] = cs;
        applyChannelToRow(index, cs);

        // Status update
        tvStatus.setText("Querying channels... CH" + String.format("%02d", index + 1) + "/16");

        // Next CH after 150ms
        mainHandler.postDelayed(() -> staggeredUpdate(result, index + 1), 150);
    }

    private void clearLog() {
        synchronized (logLock) {
            logHistory.setLength(0);
            logQueue.clear();
            logMsgCount.set(0);
            lastLogLine = "";
        }
        tvOscLog.setText("");
    }

    private void startLogThread() {
        logRunning.set(true);
        logExecutor.execute(() -> {
            while (logRunning.get()) {
                try {
                    String msg = logQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        int count = logMsgCount.incrementAndGet();
                        lastLogLine = msg;

                        synchronized (logLock) {
                            logHistory.append("[").append(count).append("] ").append(msg).append("\n");
                            // Trim to max lines to avoid memory bloat
                            int newlineCount = 0;
                            int cutoff = logHistory.length();
                            for (int i = logHistory.length() - 2; i >= 0 && newlineCount < MAX_DISPLAY_LINES; i--) {
                                if (logHistory.charAt(i) == '\n') newlineCount++;
                            }
                            if (newlineCount >= MAX_DISPLAY_LINES) {
                                // Find the cutoff point
                                int targetNewlines = MAX_DISPLAY_LINES;
                                int pos = logHistory.length() - 1;
                                while (pos >= 0 && targetNewlines > 0) {
                                    if (logHistory.charAt(pos) == '\n') targetNewlines--;
                                    pos--;
                                }
                                logHistory.delete(0, pos + 2);
                            }
                        }

                        final int c = count;
                        final String line = msg;
                        mainHandler.post(() -> {
                            // UI shows only last line (compact)
                            tvOscLog.setText("[" + c + "] " + line);
                            // Auto-scroll to bottom
                            int scrollAmount = tvOscLog.getLayout() != null ? tvOscLog.getLayout().getLineTop(tvOscLog.getLineCount()) - tvOscLog.getHeight() : 0;
                            if (scrollAmount > 0) tvOscLog.scrollTo(0, scrollAmount);
                        });
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private final Runnable stateApplyRunnable = new Runnable() {
        @Override
        public void run() {
            applyPendingStates();
        }
    };

    private void scheduleStateApply() {
        mainHandler.removeCallbacks(stateApplyRunnable);
        long now = System.currentTimeMillis();
        if (now - lastStateRefresh >= STATE_THROTTLE_MS) {
            mainHandler.post(stateApplyRunnable);
            lastStateRefresh = now;
        } else {
            mainHandler.postDelayed(stateApplyRunnable, STATE_THROTTLE_MS);
        }
    }

    private void applyPendingStates() {
        ChannelState[] states = pendingStates;
        if (states == null) return;
        for (ChannelState cs : states) {
            int idx = cs.channelNumber - 1;
            if (idx < 0 || idx >= 16) continue;
            // Use faderDb (not fader) to detect real data:
            // Default state has fader=0.0 and faderDb=-43.5f (from initial ChannelState).
            // Real -inf dB from mixer is fader=0.0, and faderDb would be < -43.5 with our formula.
            // Real data: fader > 0 OR faderDb deviates from initial -43.5.
            if (cs.fader > 0f || Math.abs(cs.faderDb - (-43.5f)) > 0.5f) {
                channelStates[idx] = cs;
                applyChannelToRow(idx, cs);
            }
        }
    }

    private void applyChannelToRow(int idx, ChannelState cs) {
        View row = channelsContainer.getChildAt(idx);
        if (row == null) return;
        ((TextView) row.findViewById(R.id.tv_db)).setText("F:" + cs.faderDbString());
        ((TextView) row.findViewById(R.id.tv_gain)).setText(cs.preampGainDbString());
        ((TextView) row.findViewById(R.id.tv_meter_db)).setText(cs.meterDbString());
        updateMuteIndicator((TextView) row.findViewById(R.id.tv_mute), cs.muted);
    }

    private void buildChannelRows() {
        channelsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < 16; i++) {
            View row = inflater.inflate(R.layout.channel_row, channelsContainer, false);
            uiRefs[i] = row;

            TextView tvChNum = row.findViewById(R.id.tv_ch_num);
            TextView tvDb = row.findViewById(R.id.tv_db);
            TextView tvGain = row.findViewById(R.id.tv_gain);
            TextView tvMeterDb = row.findViewById(R.id.tv_meter_db);
            TextView tvMute = row.findViewById(R.id.tv_mute);

            int chNum = i + 1;
            tvChNum.setText(String.format("CH%02d", chNum));

            ChannelState cs = channelStates[i];
            tvDb.setText("F:" + cs.faderDbString());
            tvGain.setText(cs.preampGainDbString());
            tvMeterDb.setText(cs.meterDbString());
            updateMuteIndicator(tvMute, cs.muted);

            channelsContainer.addView(row);
        }
    }

    private void refreshChannelRows() {
        for (int i = 0; i < 16; i++) {
            View row = channelsContainer.getChildAt(i);
            if (row == null) continue;
            ChannelState cs = channelStates[i];
            applyChannelToRow(i, cs);
        }
    }

    private void updateMuteIndicator(TextView tv, boolean muted) {
        if (muted) {
            tv.setBackgroundColor(0xFFFF1744);   // Bright red
            tv.setTextColor(0xFFFFFFFF);          // White text
        } else {
            tv.setBackgroundColor(0xFF333333);   // Dark gray
            tv.setTextColor(0xFF666666);          // Medium gray text
        }
    }

    private void showDemoData() {
        channelsContainer.setVisibility(View.VISIBLE);
        buildChannelRows();
        channelStates[2] = channelStates[2].withMuted(true);
        channelStates[7] = channelStates[7].withMuted(true);
        for (int i = 0; i < 16; i++) {
            View row = channelsContainer.getChildAt(i);
            if (row == null) continue;
            ChannelState cs = channelStates[i];
            ((TextView) row.findViewById(R.id.tv_db)).setText("F:" + cs.faderDbString());
            ((TextView) row.findViewById(R.id.tv_gain)).setText(cs.preampGainDbString());
            ((TextView) row.findViewById(R.id.tv_meter_db)).setText(cs.meterDbString());
            updateMuteIndicator((TextView) row.findViewById(R.id.tv_mute), cs.muted);
        }
        tvStatus.setText("Demo Mode — no mixer found");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logRunning.set(false);
        networkExecutor.shutdownNow();
        logExecutor.shutdownNow();
    }
}