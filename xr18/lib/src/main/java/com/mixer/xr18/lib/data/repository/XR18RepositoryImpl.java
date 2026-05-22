package com.mixer.xr18.lib.data.repository;

import com.mixer.xr18.lib.data.osc.OscClient;
import com.mixer.xr18.lib.data.osc.OSCMessage;
import com.mixer.xr18.lib.domain.model.ChannelState;
import com.mixer.xr18.lib.domain.model.EqBands;
import com.mixer.xr18.lib.domain.model.MixerDevice;
import com.mixer.xr18.lib.domain.model.MixerState;
import com.mixer.xr18.lib.domain.repository.MixerRepository;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.SharedPreferences;

/**
 * Implementation of MixerRepository using raw Java sockets and callback listeners.
 * No coroutines, no StateFlow.
 */
public class XR18RepositoryImpl implements MixerRepository {
    private static final String TAG = "XR18Repo";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MixerState state = new MixerState();
    private OscClient client;
    private boolean isQuerying = false;
    private Pattern chMixPattern = Pattern.compile("^/ch/(\\d+)/mix$");
    private Pattern chMixOnPattern = Pattern.compile("^/ch/(\\d+)/mix/on$");
    private Pattern faderPattern = Pattern.compile("^/ch/(\\d+)/mix/fader$");

    private MixerStateListener stateListener;
    private OscMessageListener oscMessageListener;
    private int meterEndianMode = 1; // 0=BE, 1=LE (XR18 uses little-endian)
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "XR18MixerPrefs";
    private static final String KEY_ENDIAN = "meterEndian";
    private static final String KEY_IP = "mixerIp";
    private static final String KEY_PORT = "mixerPort";

    public void setMeterEndianMode(int mode) {
        meterEndianMode = mode;
    }
    public int getMeterEndianMode() {
        return meterEndianMode;
    }
    public void savePrefs(String ip, int port) {
        if (prefs != null) {
            prefs.edit().putString(KEY_IP, ip).putInt(KEY_PORT, port)
                .putInt(KEY_ENDIAN, meterEndianMode).apply();
        }
    }
    public void loadPrefs() {
        if (prefs != null) {
            meterEndianMode = prefs.getInt(KEY_ENDIAN, 0);
        }
    }

    public XR18RepositoryImpl() {
    }
    public void setContext(android.content.Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        loadPrefs();
    }

    @Override
    public void discoverDevices(long timeoutMs, DiscoveryCallback callback) {
        executor.submit(() -> {
            List<MixerDevice> results = new ArrayList<>();
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout((int) timeoutMs);
                socket.setBroadcast(true);

                byte[] triggerData = new byte[1];
                InetAddress broadcastAddr = InetAddress.getByAddress(
                    new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }
                );
                DatagramPacket trigger = new DatagramPacket(
                    triggerData, triggerData.length, broadcastAddr, 10024
                );
                socket.send(trigger);

                byte[] buffer = new byte[2048];
                long deadline = System.currentTimeMillis() + timeoutMs;

                while (System.currentTimeMillis() < deadline) {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);

                    int pktLen = pkt.getLength();
                    int addrEnd = findNullTerminator(buffer, 0, pktLen);
                    String addr = new String(buffer, 0, addrEnd, java.nio.charset.StandardCharsets.UTF_8);

                    if (!addr.equals("/xinfo")) continue;

                    int typeTagOffset = (addrEnd + 4) & 0x7FFFFFFC;
                    if (typeTagOffset >= pktLen || buffer[typeTagOffset] != 0x2C) continue;

                    List<String> strings = parseOSCStrings(buffer, typeTagOffset + 1, pktLen);
                    if (strings.size() < 3) continue;

                    String srcIp = pkt.getAddress().getHostAddress();
                    MixerDevice dev = new MixerDevice(srcIp, strings.get(0), strings.get(1), strings.get(2));
                    results.add(dev);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (socket != null) socket.close();
            }
            callback.onDevicesFound(results);
        });
    }

    @Override
    public void queryChannelStates(MixerDevice device) {
        if (isQuerying) {
            stopAllConnections();
        }
        isQuerying = true;

        // Update device in state
        state.device = device;

        // Create OscClient SYNCHRONously on calling thread (before executor),
        // so setMute() can safely use it immediately after this method returns.
        client = new OscClient(device.ipAddress, 10024);

        executor.submit(() -> {
            client.setLogListener((type, msg) -> {
                try {
                    if (oscMessageListener != null) oscMessageListener.onOscMessage(type, msg);
                } catch (Exception e) { android.util.Log.e("XR18Repo", "logListener error", e); }
            });

            client.setMessageListener(msg -> {
                try { handleOSCMessage(msg); }
                catch (Exception e) { android.util.Log.e("XR18Repo", "handleOSCMessage error", e); }
            });

            client.start();

            // Step 1: Send /xinfo to verify connection
            client.send("/xinfo");
            sleep(500);

            // Step 2: Send /xremote to subscribe to periodic updates
            client.send("/xremote");
            sleep(500);

            // Step 2b: Subscribe to per-channel mute change notifications via /ch/xx/mix/on/subscribe
            // This ensures XR18 pushes mute changes when they occur on the mixer
            for (int ch = 1; ch <= 16; ch++) {
                String chStr = ch < 10 ? ("0" + ch) : String.valueOf(ch);
                client.send("/ch/" + chStr + "/mix/on/subscribe");
                addRepoLog("QUERY_SEND: /ch/" + chStr + "/mix/on/subscribe");
            }
            sleep(500);

            // Step 2b: Query LR meter
            client.send("/lr/meter");
            addRepoLog("QUERY_SEND: /lr/meter");
            sleep(500);

            // Step 2c: Subscribe to /meters/1 for continuous all-channel meter stream at ~5Hz
            // /meters/1 chnmeterid=1 returns all 40 values: 16 mono channels first
            // Re-subscribe every 200ms to keep the stream alive at 5Hz
            client.sendSubscribe("/meters/1", 1);
            addRepoLog("QUERY_SEND: /meters/1 chnmeterid=1");
            sleep(500);

            // Step 3: Query all channel main states (wait first for connection stability)
            addRepoLog("STEP3: starting /ch/ fader queries");
            android.util.Log.d("XR18Repo", "STEP3: starting /ch/ fader queries");
            sleep(2000);  // wait 2s before querying faders
            for (int ch = 1; ch <= 16; ch++) {
                String chStr = ch < 10 ? ("0" + ch) : String.valueOf(ch);
                String addr = "/ch/" + chStr + "/mix/fader";
                addRepoLog("QUERY_SEND: " + addr);
                android.util.Log.d("XR18Repo", "QUERY_SEND: " + addr);
                client.send(addr);
                addRepoLog("SEND_DONE: " + addr);
                sleep(200);
            }
            // Also query mute state (mix/on) for each channel
            for (int ch = 1; ch <= 16; ch++) {
                String chStr = ch < 10 ? ("0" + ch) : String.valueOf(ch);
                String addrOn = "/ch/" + chStr + "/mix/on";
                addRepoLog("QUERY_SEND: " + addrOn);
                android.util.Log.d("XR18Repo", "QUERY_SEND: " + addrOn);
                client.send(addrOn);
                sleep(200);
            }
            addRepoLog("STEP3: /ch/ queries complete, waiting for responses");
            sleep(3000);  // wait 3s for responses
            addRepoLog("STEP3: done waiting, result count = " + state.channels.size());

            // Step 4: Query headamp gain
            for (int ch = 1; ch <= 16; ch++) {
                String chStr = ch < 10 ? ("0" + ch) : String.valueOf(ch);
                client.send("/headamp/" + chStr + "/gain");
                sleep(50);
            }

            // Send /xremote every 8 seconds to stay subscribed
            // Re-subscribe /meters/1 every 200ms (5Hz) to keep meter stream alive
            long lastMeterResubscribe = 0;
            while (isQuerying) {
                long now = System.currentTimeMillis();
                if (now - lastMeterResubscribe >= 200) {
                    client.sendSubscribe("/meters/1", 1);
                    lastMeterResubscribe = now;
                }
                sleep(100);  // sleep 100ms between checks to avoid busy loop
                if (isQuerying) {
                    client.send("/xremote");
                    sleep(7700);  // sleep 7.7s, so /xremote is sent every ~8s total
                }
            }
        });
    }

    private void handleOSCMessage(OSCMessage msg) {
        String addr = msg.address;
        Object[] args = msg.args;

        // Handle /xremote subscription responses
        // Format: /xremote <channel(int)> <param(string)> <value(float|int)>
        if (addr.equals("/xremote") || addr.startsWith("/xremote")) {
            if (args.length >= 3) {
                int ch = toInt(args[0]);
                if (ch < 1 || ch > 16) return;
                int idx = ch - 1;

                String param = args[1].toString();
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);

                switch (param) {
                    case "mix/fader": {
                        float v = toFloat(args[2]);
                        cs.fader = v;
                        cs.faderDb = ChannelState.faderToDb(v);
                        addRepoLog("XRMT /ch/"+ch+"/mix/fader="+String.format("%.4f",v)+" ("+cs.faderDbString()+")");
                        break;
                    }
                    case "mix/on": {
                        int v = toInt(args[2]);
                        cs.muted = (v != 0);  // mix/on=1 = muted, mix/on=0 = audio active
                        addRepoLog("SET /xremote ch="+ch+" mix/on="+v+" mut="+cs.muted);
                        break;
                    }
                    case "mix/pan": {
                        cs.pan = toFloat(args[2]);
                        break;
                    }
                    case "eq/on": {
                        int v = toInt(args[2]);
                        cs.eqEnabled = (v == 1);
                        break;
                    }
                    case "eq/1/g": {
                        cs.eqBands = cs.eqBands.copy().withBand1(toFloat(args[2]));
                        break;
                    }
                    case "eq/2/g": {
                        cs.eqBands = cs.eqBands.copy().withBand2(toFloat(args[2]));
                        break;
                    }
                    case "eq/3/g": {
                        cs.eqBands = cs.eqBands.copy().withBand3(toFloat(args[2]));
                        break;
                    }
                    case "eq/4/g": {
                        cs.eqBands = cs.eqBands.copy().withBand4(toFloat(args[2]));
                        break;
                    }
                    case "mix": {
                        // Batch: [fader(float), on(int), pan(float)]
                        float fader = args.length > 2 ? toFloat(args[2]) : 0.75f;
                        int on = args.length > 3 ? toInt(args[3]) : 1;
                        float pan = args.length > 4 ? toFloat(args[4]) : 0.5f;
                        cs.fader = fader;
                        cs.faderDb = ChannelState.faderToDb(fader);
                        cs.muted = (on != 0);
                        cs.pan = pan;
                        break;
                    }
                }

                channels.set(idx, cs);
                state.channels = channels;
                notifyStateChanged();
            }
            return;
        }

        // Handle /lr/meter (LR main meter values)
        if (addr.equals("/lr/meter") && args.length >= 2) {
            state.lrMeterLeft = toFloat(args[0]);
            state.lrMeterRight = toFloat(args[1]);
            addRepoLog("SET /lr/meter L=" + String.format("%.4f", state.lrMeterLeft) + " R=" + String.format("%.4f", state.lrMeterRight));
            notifyStateChanged();
            return;
        }

        // Handle /meters/0 (channel meter blob, 8x 16-bit signed ints per channel)
        // Format: /meters/0 <blob>  where blob = [4-byte big-endian size][n x big-endian 16-bit signed]
        // For chnmeterid=8: 8 values per channel (pre-fader L/R, gate+comp reduction, post-fader L/R, gate+comp key)
        if ((addr.equals("/meters/0") || addr.equals("/meters")) && args.length >= 1 && args[0] instanceof byte[]) {
            byte[] blob = (byte[]) args[0];
            // Blob: [4-byte BE size][meter0][meter1]... (little-endian 16-bit signed per XR18 spec)
            if (blob.length >= 4) {
                int blobDataLen = ((blob[0] & 0xFF) << 24) |
                                  ((blob[1] & 0xFF) << 16) |
                                  ((blob[2] & 0xFF) << 8) |
                                  (blob[3] & 0xFF);
                int numMeters = blobDataLen / 2;
                for (int mi = 0; mi < numMeters && mi < 16; mi++) {
                    // XR18 /meters/0 uses little-endian 16-bit signed (same as /meters/1)
                    int b0 = blob[4 + mi * 2] & 0xFF;
                    int b1 = blob[4 + mi * 2 + 1] & 0xFF;
                    int unsignedVal = (meterEndianMode == 1) ? (b1 << 8) | b0 : (b0 << 8) | b1;
                    short meterValue = (short) (unsignedVal >= 32768 ? unsignedVal - 65536 : unsignedVal);
                    // Clamp: XR18 meters are signed 16-bit, resolution 1/256 dB, typical range -9600 to +3072
                    if (meterValue < -24576) meterValue = -24576;  // clamp to ≥-96 dB
                    if (meterValue > 3072) meterValue = 3072;     // clamp to ≤+12 dB
                    float linear = ChannelState.meterValueToLinear(meterValue);

                    int ch = mi + 1;
                    List<ChannelState> channels = new ArrayList<>(state.channels);
                    if (ch >= 1 && ch <= channels.size()) {
                        ChannelState cs = channels.get(ch - 1);
                        cs.meter = linear;
                        cs.meterDb = ChannelState.meterValueToDb(meterValue);
                        channels.set(ch - 1, cs);
                        if (ch <= 4) {  // log first 4 channels
                            addRepoLog("METER /meters/0 ch" + ch + " raw=" + meterValue + " linear=" + String.format("%.3f", linear) + " (" + cs.meterDbString() + ")");
                        }
                    }
                }
                addRepoLog("METER /meters/0: " + numMeters + " meters decoded");
            }
            notifyStateChanged();
            return;
        }

        // Handle /meters/1 (all-channels meter blob: 16 mono + aux/fx + bus + fx send + st + monitor)
        // Blob: [4-byte BE count][little-endian 16-bit signed meter values]
        if (addr.equals("/meters/1") && args.length >= 1 && args[0] instanceof byte[]) {
            byte[] blob = (byte[]) args[0];
            if (blob.length >= 4) {
                int blobDataLen = ((blob[0] & 0xFF) << 24) |
                                  ((blob[1] & 0xFF) << 16) |
                                  ((blob[2] & 0xFF) << 8) |
                                  (blob[3] & 0xFF);
                int numMeters = blobDataLen / 2;
                // chnmeterid=1 returns 40 values: 16 mono channels first
                // XR18 /meters/1 meter blob: data starts at blob[4] (blob[0:3] = size header)
                // Byte order: 0=BE, 1=LE (XR18 uses little-endian for /meters/1, verified against ground truth)
                for (int mi = 0; mi < numMeters && mi < 16; mi++) {
                    int b0 = blob[4 + mi * 2] & 0xFF;
                    int b1 = blob[4 + mi * 2 + 1] & 0xFF;
                    int unsignedVal = (meterEndianMode == 1) ? (b1 << 8) | b0 : (b0 << 8) | b1;
                    short meterValue = (short) (unsignedVal >= 32768 ? unsignedVal - 65536 : unsignedVal);
                    // Clamp: XR18 meter range -96dB to +12dB
                    if (meterValue < -24576) meterValue = -24576;
                    if (meterValue > 3072) meterValue = 3072;
                    float linear = ChannelState.meterValueToLinear(meterValue);

                    int ch = mi + 1;
                    List<ChannelState> channels = new ArrayList<>(state.channels);
                    if (ch >= 1 && ch <= channels.size()) {
                        ChannelState cs = channels.get(ch - 1);
                        cs.meter = linear;
                        cs.meterDb = ChannelState.meterValueToDb(meterValue);
                        channels.set(ch - 1, cs);
                        if (ch <= 4) {
                            addRepoLog("METER /meters/1 ch" + ch + " raw=" + meterValue + " linear=" + String.format("%.3f", linear) + " (" + cs.meterDbString() + ")");
                        }
                    }
                }
                addRepoLog("METER /meters/1: " + numMeters + " meters decoded");
            }
            notifyStateChanged();
            return;
        }

        // Handle /ch/xx/mix (use matches() to avoid matching /ch/xx/mix/fader)
        Matcher m = chMixPattern.matcher(addr);
        if (m.matches()) {
            int ch = Integer.parseInt(m.group(1));
            if (ch >= 1 && ch <= 16) {
                int idx = ch - 1;
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);
                if (args.length > 0) {
                    cs.fader = toFloat(args[0]);
                    cs.faderDb = ChannelState.faderToDb(cs.fader);
                    addRepoLog("SET /ch/"+ch+"/mix fader="+String.format("%.4f", cs.fader)+" ("+cs.faderDbString()+") mut="+cs.muted);
                }
                if (args.length > 1) {
                    // mix/on=1 = muted, mix/on=0 = audio active
                    cs.muted = (toInt(args[1]) != 0);
                }
                if (args.length > 2) {
                    cs.pan = toFloat(args[2]);
                }
                channels.set(idx, cs);
                state.channels = channels;
                notifyStateChanged();
            }
            return;
        }

        // Handle /ch/xx/mix/fader
        m = faderPattern.matcher(addr);
        if (m.matches()) {
            int ch = Integer.parseInt(m.group(1));
            if (ch >= 1 && ch <= 16 && args.length > 0) {
                int idx = ch - 1;
                float v = toFloat(args[0]);
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);
                cs.fader = v;
                cs.faderDb = ChannelState.faderToDb(v);
                channels.set(idx, cs);
                state.channels = channels;
                notifyStateChanged();
            }
            return;
        }

        // Handle /ch/xx/mix/on — standalone mute state query response
        // mix/on=1 = muted, mix/on=0 = audio active
        m = chMixOnPattern.matcher(addr);
        if (m.matches()) {
            int ch = Integer.parseInt(m.group(1));
            if (ch >= 1 && ch <= 16 && args.length > 0) {
                int idx = ch - 1;
                int onVal = toInt(args[0]);
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);
                cs.muted = (onVal != 0);
                addRepoLog("QUERY_RESP /ch/"+ch+"/mix/on="+onVal+" muted="+cs.muted);
                channels.set(idx, cs);
                state.channels = channels;
                notifyStateChanged();
            }
            return;
        }


        // Handle /headamp/xx/gain
        if (addr.startsWith("/headamp") && addr.contains("/gain") && args.length > 0) {
            String[] parts = addr.split("/");
            if (parts.length >= 3) {
                try {
                    int ch = Integer.parseInt(parts[2]);
                    if (ch >= 1 && ch <= 16) {
                        int idx = ch - 1;
                        float v = toFloat(args[0]);
                        List<ChannelState> channels = new ArrayList<>(state.channels);
                        ChannelState cs = channels.get(idx);
                        cs.preampGain = v;
                        cs.preampGainDb = ChannelState.gainToDb(v);
                        channels.set(idx, cs);
                        state.channels = channels;
                        notifyStateChanged();
                        addRepoLog("SET /headamp/" + ch + "/gain raw=" + String.format("%.4f", cs.preampGain) + " (" + cs.preampGainDbString() + ")");
                    }
                } catch (NumberFormatException e) { }
            }
        }
    }

    private int findNullTerminator(byte[] data, int start, int end) {
        int i = start;
        while (i < end && data[i] != 0) i++;
        return Math.min(i, end);
    }

    private List<String> parseOSCStrings(byte[] data, int start, int length) {
        List<String> strings = new ArrayList<>();
        int pos = start;
        while (pos < length && data[pos] != 0) {
            int end = findNullTerminator(data, pos, length);
            if (end <= pos) break;
            strings.add(new String(data, pos, end - pos, java.nio.charset.StandardCharsets.UTF_8));
            pos = (end + 4) & 0x7FFFFFFC;
        }
        return strings;
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }

    private float toFloat(Object o) {
        if (o instanceof Number) return ((Number) o).floatValue();
        return 0f;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }

    private void addRepoLog(String msg) {
        try {
            if (oscMessageListener != null) oscMessageListener.onOscMessage("REPO", msg);
        } catch (Exception e) { }
    }

    private void notifyStateChanged() {
        try {
            if (stateListener != null) {
                stateListener.onMixerStateChanged(state);
            }
        } catch (Exception e) { android.util.Log.e("XR18Repo", "stateListener error", e); }
    }

    private void stopAllConnections() {
        isQuerying = false;
        if (client != null) {
            client.stop();
            client = null;
        }
    }

    @Override
    public void setMixerStateListener(MixerStateListener listener) {
        this.stateListener = listener;
    }

    @Override
    public void setOscMessageListener(OscMessageListener listener) {
        this.oscMessageListener = listener;
    }

    public MixerState getMixerState() { return state; }

    @Override
    public void close() {
        stopAllConnections();
        executor.shutdownNow();
    }

    /** Send mute toggle command to mixer.
     * mix/on=1 = mute on (channel silenced), mix/on=0 = mute off (audio passes).
     * @param ch 1-16, @param muted true=muted (mix/on=1), false=unmuted (mix/on=0) */
    public void setMute(int ch, boolean muted) {
        addRepoLog("setMute called: ch=" + ch + " muted=" + muted + " client=" + (client != null ? "OK" : "NULL"));
        if (client == null) {
            addRepoLog("setMute FAILED: client is null - queryChannelStates not called yet");
            return;
        }
        // Send mute command
        client.send("/ch/" + ch + "/mix/on", muted ? 1 : 0);
        addRepoLog("SEND /ch/" + ch + "/mix/on=" + (muted ? 1 : 0));

        // Immediately query back the mute state to confirm XR18 received it
        // This forces XR18 to respond with current mute status
        sleep(100);
        String chStr = ch < 10 ? ("0" + ch) : String.valueOf(ch);
        client.send("/ch/" + chStr + "/mix/on");
        addRepoLog("QUERY_AFTERMUTE: /ch/" + chStr + "/mix/on (confirming)");
    }

}