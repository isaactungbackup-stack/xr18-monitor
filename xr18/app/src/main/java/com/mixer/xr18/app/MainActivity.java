package com.mixer.xr18.app;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private TextView tvOscLog;
    private EditText etIpA, etIpB, etIpC, etIpD;
    private Button btnConnectIp;
    private Button btnDiscover;
    private Button btnDebug;
    private Button btnClearLog;
    private Button btnCopyLog;
    private LinearLayout channelsContainer;
    private MixerDevice connectedDevice;

    // Top bar: board number + clock
    private TextView tvBoard;
    private TextView tvClock;
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

        // Start clock updates
        mainHandler.postDelayed(clockTick, 500);
    }

    /** Called when user taps a channel row (CH button) to open the spectrogram dialog. */
    public void openChannelDetail(int channel) {
        String ip = (connectedDevice != null) ? connectedDevice.getIpAddress() : "";
        ChannelDetailDialog dialog = ChannelDetailDialog.newInstance(channel, ip);
        dialog.show(getSupportFragmentManager(), "channel_detail_" + channel);
    }

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            mainHandler.postDelayed(this, 1000);
        }
    };

    private void updateClock() {
        if (tvClock == null) return;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        tvClock.setText(sdf.format(new java.util.Date()));
        if (connectedDevice != null) {
            tvBoard.setText(getString(R.string.app_name));
        } else {
            tvBoard.setText("Board: --");
        }
    }

    private void initViews() {
        tvOscLog = findViewById(R.id.tv_osc_log);
        etIpA = findViewById(R.id.et_ip_a);
        etIpB = findViewById(R.id.et_ip_b);
        etIpC = findViewById(R.id.et_ip_c);
        etIpD = findViewById(R.id.et_ip_d);
        btnConnectIp = findViewById(R.id.btn_connect_ip);
        btnDiscover = findViewById(R.id.btn_discover);
        btnDebug = findViewById(R.id.btn_debug);
        btnClearLog = findViewById(R.id.btn_clear_log);
        btnCopyLog = findViewById(R.id.btn_copy_log);
        channelsContainer = findViewById(R.id.channels_container);
        tvBoard = findViewById(R.id.tv_board);
        tvClock = findViewById(R.id.tv_clock);

        // Progress bar (hidden by default)
        progressBar = findViewById(R.id.progress_bar);

        tvBoard.setText("Board: --");
        tvClock.setText("--:--:--");
    }

    private void setLoading(boolean loading) {
        mainHandler.post(() -> {
            if (loading) {
                progressBar.setVisibility(View.VISIBLE);
                btnConnectIp.setEnabled(false);
                btnDiscover.setEnabled(false);
                btnDebug.setEnabled(false);
            } else {
                progressBar.setVisibility(View.GONE);
                btnConnectIp.setEnabled(true);
                btnDiscover.setEnabled(true);
                btnDebug.setEnabled(true);
            }
        });
    }

    private void setupListeners() {
        btnConnectIp.setOnClickListener(v -> {
            String ip = etIpA.getText().toString().trim() + "."
                    + etIpB.getText().toString().trim() + "."
                    + etIpC.getText().toString().trim() + "."
                    + etIpD.getText().toString().trim();
            if (ip.equals("...")) {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            clearLog();
            tvBoard.setText("Connecting...");
            setLoading(true);

            networkExecutor.execute(() -> {
                boolean success = DiscoveryHelper.connectToIpSync(ip);
                mainHandler.post(() -> {
                    setLoading(false);
                    if (success) {
                        connectedDevice = DiscoveryHelper.createDevice(ip, "XR18", "XR18", "unknown");
                        tvBoard.setText(getString(R.string.app_name));
                        Toast.makeText(this, "Connected to " + ip, Toast.LENGTH_SHORT).show();
                        autoQueryChannels();
                    } else {
                        tvBoard.setText("Board: --");
                        Toast.makeText(this, "Cannot reach " + ip, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        btnDiscover.setOnClickListener(v -> {
            clearLog();
            tvBoard.setText("Searching...");
            setLoading(true);

            networkExecutor.execute(() -> {
                List<MixerDevice> devices = DiscoveryHelper.discoverSync();
                mainHandler.post(() -> {
                    setLoading(false);
                    if (devices.isEmpty()) {
                        tvBoard.setText("Board: --");
                        Toast.makeText(this, "No XR18 found", Toast.LENGTH_SHORT).show();
                        showDemoData();
                    } else {
                        connectedDevice = devices.get(0);
                        String ip = connectedDevice.getIpAddress();
                        String[] parts = ip.replace("127.0.0.1", "192.168.31.6").split("\\.");
                        if (parts.length == 4) {
                            etIpA.setText(parts[0]);
                            etIpB.setText(parts[1]);
                            etIpC.setText(parts[2]);
                            etIpD.setText(parts[3]);
                        }
                        tvBoard.setText(getString(R.string.app_name));
                        Toast.makeText(this, "Found: " + connectedDevice.getName(), Toast.LENGTH_SHORT).show();
                        autoQueryChannels();
                    }
                });
            });
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
                Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void autoQueryChannels() {
        if (connectedDevice == null) {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show();
            return;
        }
        clearLog();
        logMsgCount.set(0);
        lastLogLine = "";
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
                        Toast.makeText(this, "Connected: " + result.length + " ch", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No response from mixer", Toast.LENGTH_SHORT).show();
                    }
                });
            });
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
                            if (tvOscLog.getLayout() != null) {
                                int scrollAmount = tvOscLog.getLayout().getLineTop(tvOscLog.getLineCount()) - tvOscLog.getHeight();
                                if (scrollAmount > 0) tvOscLog.scrollTo(0, scrollAmount);
                            }
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
            // Always update — mute/fader/meter must reflect live data regardless of filter
            channelStates[idx] = cs;
            applyChannelToRow(idx, cs);
        }
    }

    private void applyChannelToRow(int idx, ChannelState cs) {
        View row = channelsContainer.getChildAt(idx);
        if (row == null) return;

        // Update horizontal meter bar
        MeterBarView meterBar = row.findViewById(R.id.meter_bar);
        if (meterBar != null) {
            meterBar.setMeterDb(cs.faderDb);
        }

        // Fader value as text only (no bar)
        ((TextView) row.findViewById(R.id.tv_db)).setText("F:" + cs.faderDbString());
        ((TextView) row.findViewById(R.id.tv_gain)).setText("G:" + cs.preampGainDbString());
        // REMOVED: meter label no longer needed
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
            TextView tvMute = row.findViewById(R.id.tv_mute);

            int chNum = i + 1;
            tvChNum.setText(String.format("CH%02d", chNum));

            ChannelState cs = channelStates[i];
            tvDb.setText("F:" + cs.faderDbString());
            tvGain.setText("G:" + cs.preampGainDbString());
            // REMOVED: meter label no longer needed
            updateMuteIndicator(tvMute, cs.muted);
            final int ch = chNum;
            android.util.Log.i("MainActivity", "buildChannelRows: registering mute click for ch=" + ch + " row=" + i);
            tvMute.setOnClickListener(v -> {
                android.util.Log.i("MainActivity", "Mute clicked! ch=" + ch + " currently muted=" + channelStates[ch - 1].muted);
                boolean newMuted = !channelStates[ch - 1].muted;
                DiscoveryHelper.setMute(ch, newMuted);
                channelStates[ch - 1] = channelStates[ch - 1].withMuted(newMuted);
                updateMuteIndicator(tvMute, newMuted);
            });

            // Tap anywhere on the channel row to open the spectrogram / channel detail dialog
            row.setOnClickListener(v -> openChannelDetail(ch));

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
            tv.setBackgroundColor(0xFF333333);   // Dark gray → muted (silenced)
            tv.setTextColor(0xFF666666);          // Medium gray text
        } else {
            tv.setBackgroundColor(0xFFFF1744);   // Bright red → not muted (audio passing)
            tv.setTextColor(0xFFFFFFFF);          // White text
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
            ((TextView) row.findViewById(R.id.tv_gain)).setText("G:" + cs.preampGainDbString());
            // REMOVED: meter label no longer needed
            updateMuteIndicator((TextView) row.findViewById(R.id.tv_mute), cs.muted);
        }
        Toast.makeText(this, "Demo Mode — no mixer found", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logRunning.set(false);
        networkExecutor.shutdownNow();
        logExecutor.shutdownNow();
    }
}