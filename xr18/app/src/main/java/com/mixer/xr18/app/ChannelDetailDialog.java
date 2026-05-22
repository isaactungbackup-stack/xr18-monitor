package com.mixer.xr18.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.mixer.xr18.lib.data.osc.OSCMessage;
import com.mixer.xr18.lib.domain.model.ChannelState;
import com.mixer.xr18.lib.domain.model.EqBands;
import com.mixer.xr18.lib.domain.model.MixerState;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel Detail Dialog — BottomSheet-style.
 *
 * Shows:
 *   - Scrolling spectrogram (2D energy map)
 *   - RTA bar display (top strip)
 *   - EQ curve overlay (white/cyan line)
 *   - 4-band EQ SeekBar controls
 *
 * Meter data comes from /meters/1 stream via DiscoveryHelper's repository.
 * EQ gain changes are sent via OSC to the XR18 device.
 */
public class ChannelDetailDialog extends DialogFragment {
    private static final String ARG_CHANNEL = "channel";
    private static final String ARG_IP = "ip";

    private int channel;          // 1-16
    private String mixerIp = "";

    // UI
    private TextView tvTitle;
    private TextView tvMeterDb;
    private SpectrogramView spectrogramView;
    private SeekBar seekB1, seekB2, seekB3, seekB4;
    private TextView tvB1, tvB2, tvB3, tvB4;
    private Button btnClose;

    // EQ band values (normalized 0-1, maps to -15 to +15 dB)
    private float[] eqBandGain = {0.5f, 0.5f, 0.5f, 0.5f}; // default neutral

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService meterExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpectrogramMeter");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Socket for receiving meter stream directly in this dialog
    private DatagramSocket meterSocket;
    private int localPort = 0;

    public static ChannelDetailDialog newInstance(int channel, String mixerIp) {
        ChannelDetailDialog frag = new ChannelDetailDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_CHANNEL, channel);
        args.putString(ARG_IP, mixerIp);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                DisplayMetrics dm = new DisplayMetrics();
                window.getWindowManager().getDefaultDisplay().getMetrics(dm);
                params.width = (int)(dm.widthPixels * 0.9);
                params.height = (int)(dm.heightPixels * 0.8);
                window.setAttributes(params);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Dialog);

        if (getArguments() != null) {
            channel = getArguments().getInt(ARG_CHANNEL, 1);
            mixerIp = getArguments().getString(ARG_IP, "");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#111122")));
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.channel_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupUI();
        startMeterListener();
    }

    private void bindViews(View view) {
        tvTitle = view.findViewById(R.id.tv_channel_title);
        tvMeterDb = view.findViewById(R.id.tv_meter_db);
        spectrogramView = view.findViewById(R.id.spectrogram_view);
        seekB1 = view.findViewById(R.id.seek_eq_b1);
        seekB2 = view.findViewById(R.id.seek_eq_b2);
        seekB3 = view.findViewById(R.id.seek_eq_b3);
        seekB4 = view.findViewById(R.id.seek_eq_b4);
        tvB1 = view.findViewById(R.id.tv_eq_b1);
        tvB2 = view.findViewById(R.id.tv_eq_b2);
        tvB3 = view.findViewById(R.id.tv_eq_b3);
        tvB4 = view.findViewById(R.id.tv_eq_b4);
        btnClose = view.findViewById(R.id.btn_close);
    }

    private void setupUI() {
        String chStr = String.format("CH%02d", channel);
        tvTitle.setText(chStr + " — Spectrogram");
        tvTitle.setTextColor(Color.parseColor("#AAAAAA"));

        // Load current EQ bands from repository if available
        loadCurrentEqBands();

        // SeekBar listeners for EQ bands
        SeekBar.OnSeekBarChangeListener eqListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int bandIndex = (seekBar == seekB1) ? 0 : (seekBar == seekB2) ? 1 :
                               (seekBar == seekB3) ? 2 : 3;

                // progress 0-100 → gain -15 to +15 dB
                float gainDb = (progress / 100f) * 30f - 15f;
                float gainNorm = progress / 100f; // 0-1
                eqBandGain[bandIndex] = gainNorm;

                // Update label
                TextView tv = (bandIndex == 0) ? tvB1 : (bandIndex == 1) ? tvB2 :
                             (bandIndex == 2) ? tvB3 : tvB4;
                tv.setText(String.format("%+.1f dB", gainDb));

                // Update spectrogram EQ curve overlay
                updateSpectrogramEqCurve();

                // Send OSC to XR18 if user is interacting
                if (fromUser) {
                    sendEqGainToXR18(bandIndex + 1, gainNorm);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekB1.setOnSeekBarChangeListener(eqListener);
        seekB2.setOnSeekBarChangeListener(eqListener);
        seekB3.setOnSeekBarChangeListener(eqListener);
        seekB4.setOnSeekBarChangeListener(eqListener);

        btnClose.setOnClickListener(v -> dismiss());

        // Initial spectrogram EQ curve
        updateSpectrogramEqCurve();
    }

    private void loadCurrentEqBands() {
        // Try to read from XR18's current EQ settings
        // We read from DiscoveryHelper's repository state if available
        try {
            MixerState state = DiscoveryHelper.getRepositoryState();
            if (state != null && state.channels != null && channel >= 1 && channel <= state.channels.size()) {
                ChannelState cs = state.channels.get(channel - 1);
                EqBands bands = cs.eqBands;
                if (bands != null) {
                    eqBandGain[0] = (bands.band1 + 15f) / 30f;
                    eqBandGain[1] = (bands.band2 + 15f) / 30f;
                    eqBandGain[2] = (bands.band3 + 15f) / 30f;
                    eqBandGain[3] = (bands.band4 + 15f) / 30f;

                    // Update SeekBars
                    seekB1.setProgress((int)(eqBandGain[0] * 100));
                    seekB2.setProgress((int)(eqBandGain[1] * 100));
                    seekB3.setProgress((int)(eqBandGain[2] * 100));
                    seekB4.setProgress((int)(eqBandGain[3] * 100));
                }
            }
        } catch (Exception e) {
            // Use defaults
        }
    }

    private void updateSpectrogramEqCurve() {
        EqBands bands = new EqBands(
            eqBandGain[0] * 30f - 15f,
            eqBandGain[1] * 30f - 15f,
            eqBandGain[2] * 30f - 15f,
            eqBandGain[3] * 30f - 15f
        );
        if (spectrogramView != null) {
            spectrogramView.setEqBands(bands);
        }
    }

    /**
     * Send EQ band gain to XR18 via OSC.
     * XR18 OSC paths: /ch/xx/eq/band/{1,2,3,4}/gain (float 0-1 → -15 to +15 dB)
     */
    private void sendEqGainToXR18(int band, float gainNorm) {
        if (mixerIp == null || mixerIp.isEmpty()) return;

        meterExecutor.execute(() -> {
            try {
                DatagramSocket sock = new DatagramSocket(0);
                sock.setSoTimeout(1000);
                byte[] packet = buildOscGainPacket(channel, band, gainNorm);
                InetAddress addr = InetAddress.getByName(mixerIp);
                DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, 10024);
                sock.send(dp);
                sock.close();
            } catch (Exception e) {
                android.util.Log.e("ChannelDetailDialog", "Failed to send EQ gain", e);
            }
        });
    }

    private byte[] buildOscGainPacket(int ch, int band, float gainNorm) {
        String addr = String.format("/ch/%02d/eq/band/%d/gain", ch, band);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            byte[] addrBytes = addr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(addrBytes, 0, addrBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);
            baos.write(44); // ','
            baos.write(102); // 'f' for float
            while (baos.size() % 4 != 0) baos.write(0);
            // Float as big-endian bits
            int bits = Float.floatToIntBits(gainNorm);
            baos.write((bits >> 24) & 0xFF);
            baos.write((bits >> 16) & 0xFF);
            baos.write((bits >> 8) & 0xFF);
            baos.write(bits & 0xFF);
        } catch (Exception e) { }
        return baos.toByteArray();
    }

    /**
     * Start a background thread to listen for /meters/1 UDP stream.
     * Parses little-endian 16-bit signed meter blobs (36 bytes = 18 channels × 2 bytes).
     * Pushes energy levels into SpectrogramView every ~100ms.
     */
    private void startMeterListener() {
        running.set(true);

        meterExecutor.execute(() -> {
            try {
                // Create a listening socket on ephemeral port
                meterSocket = new DatagramSocket(0);
                localPort = meterSocket.getLocalPort();
                android.util.Log.i(TAG, "Meter listener socket on port " + localPort);

                byte[] buffer = new byte[2048];
                long lastUpdateMs = 0;
                long updateIntervalMs = 100; // ~10 fps

                while (running.get()) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                        meterSocket.setSoTimeout(1000);
                        meterSocket.receive(pkt);

                        long now = System.currentTimeMillis();
                        if (now - lastUpdateMs < updateIntervalMs) continue;
                        lastUpdateMs = now;

                        int len = pkt.getLength();
                        String addr = parseOscAddress(buffer, len);

                        // Parse /meters/1 blob
                        if (addr.equals("/meters/1") || addr.equals("/meters")) {
                            android.util.Log.i(TAG, "Received /meters/1 len=" + len);
                            int chMeter = parseMeterBlob(buffer, len, channel);
                            float db = chMeter / 256f;
                            android.util.Log.i(TAG, "Meter raw=" + chMeter + " = " + db + " dB for CH" + channel);
                            float meterDb = Math.max(-96f, Math.min(12f, db));

                            // Update UI
                            mainHandler.post(() -> {
                                if (tvMeterDb != null) {
                                    tvMeterDb.setText(String.format("%+.1f dB", meterDb));
                                }
                            });

                            // Push energy into spectrogram
                            float[] energy = SpectrogramView.energyFromMeterDb(meterDb);
                            android.util.Log.i(TAG, "CH" + channel + " meter=" + db + " dB, energy overall=" + energy[32]);
                            final float[] finalEnergy = energy;
                            mainHandler.post(() -> {
                                if (spectrogramView != null) {
                                    spectrogramView.pushEnergyLevels(finalEnergy);
                                    spectrogramView.invalidateView();
                                }
                            });
                        }

                    } catch (java.net.SocketTimeoutException e) {
                        // Normal timeout — continue
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Meter receive error", e);
                    }
                }

            } catch (Exception e) {
                android.util.Log.e(TAG, "Meter listener failed", e);
            }
        });

        // Subscribe to /meters/1 stream by sending OSC subscription from this dialog's port
        // We use the same port the repository uses, so meter stream will arrive here too
        // Actually we need to send subscribe from the same socket
        meterExecutor.execute(() -> {
            // Give the socket a moment to bind
            try { Thread.sleep(300); } catch (InterruptedException e) { }

            if (mixerIp == null || mixerIp.isEmpty()) return;
            try {
                DatagramSocket sock = new DatagramSocket(0);
                // Subscribe /meters/1 chnmeterid=1
                byte[] sub = buildOscSubscribePacket("/meters", "/meters/1", 1);
                InetAddress addr = InetAddress.getByName(mixerIp);
                DatagramPacket dp = new DatagramPacket(sub, sub.length, addr, 10024);
                sock.send(dp);
                android.util.Log.i(TAG, "Subscribed to /meters/1 from dialog");
                sock.close();
            } catch (Exception e) {
                android.util.Log.e(TAG, "Subscribe failed", e);
            }
        });
    }

    private String parseOscAddress(byte[] data, int length) {
        int end = 0;
        while (end < length && data[end] != 0 && end < 256) end++;
        return new String(data, 0, Math.min(end, length), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Parse /meters/1 blob and return the meter value for targetChannel.
     * Blob format: [4-byte BE size][little-endian 16-bit signed per channel]
     * @return meter value as signed short (÷256 = dB)
     */
    private int parseMeterBlob(byte[] data, int length, int targetChannel) {
        if (length < 4) return -24576;
        int blobSize = ((data[0] & 0xFF) << 24) |
                       ((data[1] & 0xFF) << 16) |
                       ((data[2] & 0xFF) << 8) |
                       (data[3] & 0xFF);
        int numMeters = blobSize / 2;
        if (targetChannel < 1 || targetChannel > numMeters) return -24576;

        int idx = targetChannel - 1;
        int b0 = data[4 + idx * 2] & 0xFF;
        int b1 = data[4 + idx * 2 + 1] & 0xFF;
        int unsignedVal = (b1 << 8) | b0;
        return unsignedVal >= 32768 ? unsignedVal - 65536 : unsignedVal;
    }

    private byte[] buildOscSubscribePacket(String addr, String path, int value) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            byte[] addrBytes = addr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(addrBytes, 0, addrBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);

            byte[] pathBytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(pathBytes, 0, pathBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);

            // Type tag: ,si
            baos.write(44); // ','
            baos.write(115); // 's'
            baos.write(105); // 'i'
            while (baos.size() % 4 != 0) baos.write(0);

            // String arg: path
            baos.write(pathBytes, 0, pathBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);

            // Int arg: chnmeterid
            int val = value;
            baos.write((val >> 24) & 0xFF);
            baos.write((val >> 16) & 0xFF);
            baos.write((val >> 8) & 0xFF);
            baos.write(val & 0xFF);
        } catch (Exception e) { }
        return baos.toByteArray();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        running.set(false);
        if (meterSocket != null) {
            meterSocket.close();
            meterSocket = null;
        }
        meterExecutor.shutdownNow();
    }

    private static final String TAG = "ChannelDetailDialog";
}