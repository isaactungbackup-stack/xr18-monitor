package com.mixer.wing.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.mixer.wing.app.R;
import com.mixer.wing.lib.domain.model.ChannelState;
import com.mixer.wing.lib.domain.model.MixerDevice;

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
    private final StringBuilder logHistory = new StringBuilder(65536);
    private final Object logLock = new Object();
    private String lastLogLine = "";
    private static final int MAX_DISPLAY_LINES = 500;

    private final long STATE_THROTTLE_MS = 100;
    private long lastStateRefresh = 0;

    // WING has 48 channels, show CH1-16 by default with bank selector
    private static final int CHANNELS_PER_BANK = 16;
    private static final int TOTAL_CHANNELS = 48;
    private int currentBank = 0; // 0=CH1-16, 1=CH17-32, 2=CH33-48

    private final ChannelState[] channelStates = new ChannelState[TOTAL_CHANNELS];
    private volatile ChannelState[] pendingStates = null;
    private final View[] uiRefs = new View[TOTAL_CHANNELS];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        for (int i = 0; i < TOTAL_CHANNELS; i++) {
            channelStates[i] = new ChannelState(i + 1);
        }

        startLogThread();
        mainHandler.postDelayed(clockTick, 500);
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
                boolean success = WINGDiscoveryHelper.connectToIpSync(ip);
                mainHandler.post(() -> {
                    setLoading(false);
                    if (success) {
                        connectedDevice = WINGDiscoveryHelper.createDevice(ip, "WING", "WING", "unknown");
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
                List<MixerDevice> devices = WINGDiscoveryHelper.discoverSync();
                mainHandler.post(() -> {
                    setLoading(false);
                    if (devices.isEmpty()) {
                        tvBoard.setText("Board: --");
                        Toast.makeText(this, "No WING found", Toast.LENGTH_SHORT).show();
                        showDemoData();
                    } else {
                        connectedDevice = devices.get(0);
                        String ip = connectedDevice.getIpAddress();
                        String[] parts = ip.replace("127.0.0.1", "192.168.31.100").split("\\.");
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
            tvOscLog.setText(WINGDiscoveryHelper.getDebugMessages());
        });

        btnClearLog.setOnClickListener(v -> {
            clearLog();
        });

        // Bank selector buttons
        Button btnBank1 = findViewById(R.id.btn_bank_1_16);
        Button btnBank2 = findViewById(R.id.btn_bank_17_32);
        Button btnBank3 = findViewById(R.id.btn_bank_33_48);

        if (btnBank1 != null) {
            btnBank1.setOnClickListener(v -> switchBank(0));
        }
        if (btnBank2 != null) {
            btnBank2.setOnClickListener(v -> switchBank(1));
        }
        if (btnBank3 != null) {
            btnBank3.setOnClickListener(v -> switchBank(2));
        }

        btnCopyLog.setOnClickListener(v -> {
            String log;
            synchronized (logLock) {
                log = logHistory.toString();
            }
            if (!log.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("WING Log", log);
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

        buildChannelRows();

        setLoading(true);

        WINGDiscoveryHelper.setLogConsumer(msg -> {
            if (msg != null) logQueue.offer(msg);
        });

        WINGDiscoveryHelper.setStateUpdateListener(states -> {
            if (states != null) {
                pendingStates = states;
                scheduleStateApply();
            }
        });

        networkExecutor.execute(() -> {
            WINGDiscoveryHelper.queryChannels(connectedDevice, result -> {
                mainHandler.post(() -> {
                    setLoading(false);
                    if (result != null && result.length > 0) {
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
        if (index >= result.length || index >= CHANNELS_PER_BANK) {
            setLoading(false);
            return;
        }

        ChannelState cs = result[index];
        channelStates[index] = cs;
        applyChannelToRow(index, cs);

        mainHandler.postDelayed(() -> staggeredUpdate(result, index + 1), 150);
    }

    private void scheduleStateApply() {
        mainHandler.post(() -> {
            long now = System.currentTimeMillis();
            if (now - lastStateRefresh >= STATE_THROTTLE_MS) {
                lastStateRefresh = now;
                if (pendingStates != null) {
                    System.arraycopy(pendingStates, 0, channelStates, 0,
                        Math.min(pendingStates.length, TOTAL_CHANNELS));
                    refreshChannelRows();
                    pendingStates = null;
                }
            } else {
                mainHandler.postDelayed(this::scheduleStateApply, STATE_THROTTLE_MS);
            }
        });
    }

    private void clearLog() {
        synchronized (logLock) {
            logHistory.setLength(0);
            logQueue.clear();
            logMsgCount.set(0);
            lastLogLine = "";
        }
        tvOscLog.setText("(empty)");
    }

    private void startLogThread() {
        logRunning.set(true);
        logExecutor.execute(() -> {
            while (logRunning.get()) {
                try {
                    String msg = logQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        appendLog(msg);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private void appendLog(String msg) {
        synchronized (logLock) {
            if (logHistory.length() > 32768) {
                logHistory.setLength(16384);
            }
            logHistory.append(msg).append("\n");
            logMsgCount.incrementAndGet();

            if (logMsgCount.get() > MAX_DISPLAY_LINES) {
                int pos = logHistory.indexOf("\n", logHistory.length() / 2);
                if (pos > 0) {
                    logHistory.delete(0, pos + 1);
                }
                logMsgCount.set(logHistory.length() / 80 + 1);
            }
        }

        mainHandler.post(() -> {
            String display;
            synchronized (logLock) {
                display = logHistory.toString();
            }
            if (!display.equals(lastLogLine)) {
                lastLogLine = display;
                int len = display.length();
                if (len > 8192) {
                    tvOscLog.setText(display.substring(len - 8192));
                } else {
                    tvOscLog.setText(display);
                }
            }
        });
    }

    private void buildChannelRows() {
        channelsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        int bankOffset = currentBank * CHANNELS_PER_BANK;
        for (int i = 0; i < CHANNELS_PER_BANK; i++) {
            int chNum = bankOffset + i + 1;
            if (chNum > TOTAL_CHANNELS) break;

            View row = inflater.inflate(R.layout.channel_row, channelsContainer, false);
            uiRefs[i] = row;

            TextView tvChNum = row.findViewById(R.id.tv_ch_num);
            TextView tvDb = row.findViewById(R.id.tv_db);
            TextView tvGain = row.findViewById(R.id.tv_gain);
            TextView tvMute = row.findViewById(R.id.tv_mute);

            tvChNum.setText(String.format("CH%02d", chNum));

            ChannelState cs = channelStates[i];
            tvDb.setText("F:" + cs.faderDbString());
            tvGain.setText("G:" + cs.preampGainDbString());
            updateMuteIndicator(tvMute, cs.muted);

            final int ch = chNum;
            tvMute.setOnClickListener(v -> {
                boolean newMuted = !channelStates[ch - 1].muted;
                WINGDiscoveryHelper.setMute(ch, newMuted);
                channelStates[ch - 1] = channelStates[ch - 1].withMuted(newMuted);
                updateMuteIndicator(tvMute, newMuted);
            });

            channelsContainer.addView(row);
        }
    }

    private void applyChannelToRow(int idx, ChannelState cs) {
        if (idx >= CHANNELS_PER_BANK) return;
        View row = channelsContainer.getChildAt(idx);
        if (row == null) return;

        MeterBarView meterBar = row.findViewById(R.id.meter_bar);
        if (meterBar != null) {
            meterBar.setMeterDb(cs.meterDb);
        }

        ((TextView) row.findViewById(R.id.tv_db)).setText("F:" + cs.faderDbString());
        ((TextView) row.findViewById(R.id.tv_gain)).setText("G:" + cs.preampGainDbString());
        updateMuteIndicator((TextView) row.findViewById(R.id.tv_mute), cs.muted);
    }

    private void refreshChannelRows() {
        for (int i = 0; i < CHANNELS_PER_BANK; i++) {
            View row = channelsContainer.getChildAt(i);
            if (row == null) continue;
            int chNum = currentBank * CHANNELS_PER_BANK + i;
            if (chNum >= TOTAL_CHANNELS) break;
            applyChannelToRow(i, channelStates[chNum]);
        }
    }

    private void updateMuteIndicator(TextView tv, boolean muted) {
        if (muted) {
            tv.setBackgroundColor(0xFF333333);
            tv.setTextColor(0xFF666666);
        } else {
            tv.setBackgroundColor(0xFFFF1744);
            tv.setTextColor(0xFFFFFFFF);
        }
    }

    private void switchBank(int bank) {
        if (bank < 0 || bank > 2) return;
        currentBank = bank;
        buildChannelRows();
        refreshChannelRows();

        // Update button highlights
        Button btn1 = findViewById(R.id.btn_bank_1_16);
        Button btn2 = findViewById(R.id.btn_bank_17_32);
        Button btn3 = findViewById(R.id.btn_bank_33_48);
        if (btn1 != null) btn1.setBackgroundColor(bank == 0 ? 0xFF666666 : 0xFF333333);
        if (btn2 != null) btn2.setBackgroundColor(bank == 1 ? 0xFF666666 : 0xFF333333);
        if (btn3 != null) btn3.setBackgroundColor(bank == 2 ? 0xFF666666 : 0xFF333333);
    }

    private void showDemoData() {
        channelsContainer.setVisibility(View.VISIBLE);
        buildChannelRows();
        channelStates[2] = channelStates[2].withMuted(true);
        channelStates[7] = channelStates[7].withMuted(true);
        for (int i = 0; i < CHANNELS_PER_BANK; i++) {
            View row = channelsContainer.getChildAt(i);
            if (row == null) continue;
            int chNum = currentBank * CHANNELS_PER_BANK + i;
            if (chNum >= TOTAL_CHANNELS) break;
            ChannelState cs = channelStates[chNum];
            ((TextView) row.findViewById(R.id.tv_db)).setText("F:" + cs.faderDbString());
            ((TextView) row.findViewById(R.id.tv_gain)).setText("G:" + cs.preampGainDbString());
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