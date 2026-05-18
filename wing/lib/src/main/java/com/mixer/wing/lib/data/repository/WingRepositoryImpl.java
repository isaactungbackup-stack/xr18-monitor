package com.mixer.wing.lib.data.repository;

import com.mixer.wing.lib.data.osc.OscClient;
import com.mixer.wing.lib.data.osc.OSCMessage;
import com.mixer.wing.lib.data.osc.WingAddresses;
import com.mixer.wing.lib.data.osc.WingMessageParser;
import com.mixer.wing.lib.domain.model.ChannelState;
import com.mixer.wing.lib.domain.model.MixerDevice;
import com.mixer.wing.lib.domain.model.MixerState;
import com.mixer.wing.lib.domain.repository.MixerRepository;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * WING implementation of MixerRepository using OSC UDP (port 10024).
 * Mirrors XR18RepositoryImpl but adapts to WING's OSC path conventions.
 */
public class WingRepositoryImpl implements MixerRepository {
    private static final String TAG = "WingRepo";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MixerState state = new MixerState();
    private OscClient client;
    private boolean isQuerying = false;

    private MixerStateListener stateListener;
    private OscMessageListener oscMessageListener;

    // WING meter blob is little-endian
    private static final boolean WING_METER_LE = true;

    private Pattern chFaderPattern = Pattern.compile("^/ch/(\\d+)/fdr$");
    private Pattern chMutePattern = Pattern.compile("^/ch/(\\d+)/mute$");
    private Pattern chPanPattern = Pattern.compile("^/ch/(\\d+)/pan$");
    private Pattern chPreampPattern = Pattern.compile("^/ch/(\\d+)/pha$");

    public WingRepositoryImpl() {
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
        state.device = device;

        executor.submit(() -> {
            client = new OscClient(device.ipAddress, 10024);

            client.setLogListener((type, msg) -> {
                try {
                    if (oscMessageListener != null) oscMessageListener.onOscMessage(type, msg);
                } catch (Exception e) { android.util.Log.e("WingRepo", "logListener error", e); }
            });

            client.setMessageListener(msg -> {
                try { handleOSCMessage(msg); }
                catch (Exception e) { android.util.Log.e("WingRepo", "handleOSCMessage error", e); }
            });

            client.start();

            // Step 1: Send /xinfo to verify connection
            client.send("/xinfo");
            sleep(500);

            // Step 2: Subscribe to all parameter changes via /S~
            client.send("/S~");
            sleep(500);

            // Step 3: Subscribe to meter batch /meters/1 at 5Hz (re-subscribe every 200ms)
            client.send("/s~", "/meters/1", 1);
            addRepoLog("QUERY_SEND: /s~ /meters/1 1");
            sleep(500);

            // Step 4: Query all 48 channel states
            addRepoLog("STEP4: starting /ch/{n}/fdr queries for all 48 channels");
            sleep(2000);
            for (int ch = 1; ch <= 48; ch++) {
                client.send(WingAddresses.chFader(ch));
                if (ch % 8 == 0) sleep(200);  // batch in groups of 8
            }
            for (int ch = 1; ch <= 48; ch++) {
                client.send(WingAddresses.chMute(ch));
                if (ch % 8 == 0) sleep(200);
            }
            for (int ch = 1; ch <= 8; ch++) {
                client.send(WingAddresses.chPreamp(ch));
                sleep(50);
            }
            addRepoLog("STEP4: /ch/ queries complete");
            sleep(3000);

            // Keep alive: re-subscribe /s~ every 200ms, /S~ every 8s
            long lastMeterResubscribe = 0;
            while (isQuerying) {
                long now = System.currentTimeMillis();
                if (now - lastMeterResubscribe >= 200) {
                    client.send("/s~", "/meters/1", 1);
                    lastMeterResubscribe = now;
                }
                sleep(100);
                if (isQuerying) {
                    client.send("/S~");
                    sleep(7700);
                }
            }
        });
    }

    private void handleOSCMessage(OSCMessage msg) {
        String addr = msg.address;
        Object[] args = msg.args;

        // Handle /S~ subscription responses (all parameter changes)
        if (addr.equals("/S~") || addr.startsWith("/S~")) {
            if (args.length >= 3) {
                int ch = toInt(args[0]);
                if (ch < 1 || ch > 48) return;
                int idx = ch - 1;

                String param = args[1].toString();
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);

                switch (param) {
                    case "fdr": {
                        float v = toFloat(args[2]);
                        cs.fader = v;
                        cs.faderDb = ChannelState.faderToDb(v);
                        addRepoLog("WING /ch/"+ch+"/fdr="+String.format("%.4f",v)+" ("+cs.faderDbString()+")");
                        break;
                    }
                    case "mute": {
                        int v = toInt(args[2]);
                        cs.muted = (v != 0);
                        addRepoLog("WING /ch/"+ch+"/mute="+v+" mut="+cs.muted);
                        break;
                    }
                    case "pan": {
                        cs.pan = toFloat(args[2]);
                        break;
                    }
                    case "pha": {
                        float v = toFloat(args[2]);
                        cs.preampGain = v;
                        cs.preampGainDb = ChannelState.gainToDb(v);
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

        // Handle /meters/1 — WING meter blob (LITTLE-endian 16-bit signed)
        if (addr.equals("/meters/1") && args.length >= 1 && args[0] instanceof byte[]) {
            byte[] blob = (byte[]) args[0];
            if (blob.length >= 4) {
                int blobDataLen = ((blob[0] & 0xFF) << 24) |
                                  ((blob[1] & 0xFF) << 16) |
                                  ((blob[2] & 0xFF) << 8) |
                                  (blob[3] & 0xFF);
                int numMeters = blobDataLen / 2;
                // WING uses little-endian for meter values
                for (int mi = 0; mi < numMeters && mi < 48; mi++) {
                    int b0 = blob[4 + mi * 2] & 0xFF;
                    int b1 = blob[4 + mi * 2 + 1] & 0xFF;
                    int unsignedVal = (b1 << 8) | b0;  // little-endian
                    short meterValue = (short) (unsignedVal >= 32768 ? unsignedVal - 65536 : unsignedVal);

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
                addRepoLog("METER /meters/1: " + numMeters + " meters decoded (little-endian)");
            }
            notifyStateChanged();
            return;
        }

        // Handle /ch/{n}/fdr responses
        java.util.regex.Matcher m = chFaderPattern.matcher(addr);
        if (m.matches()) {
            int ch = Integer.parseInt(m.group(1));
            if (ch >= 1 && ch <= 48 && args.length > 0) {
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

        // Handle /ch/{n}/mute responses
        m = chMutePattern.matcher(addr);
        if (m.matches()) {
            int ch = Integer.parseInt(m.group(1));
            if (ch >= 1 && ch <= 48 && args.length > 0) {
                int idx = ch - 1;
                int v = toInt(args[0]);
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);
                cs.muted = (v != 0);
                addRepoLog("RESP /ch/"+ch+"/mute="+v+" muted="+cs.muted);
                channels.set(idx, cs);
                state.channels = channels;
                notifyStateChanged();
            }
            return;
        }

        // Handle /ch/{n}/pha (preamp gain) responses
        m = chPreampPattern.matcher(addr);
        if (m.matches()) {
            int ch = Integer.parseInt(m.group(1));
            if (ch >= 1 && ch <= 48 && args.length > 0) {
                int idx = ch - 1;
                float v = toFloat(args[0]);
                List<ChannelState> channels = new ArrayList<>(state.channels);
                ChannelState cs = channels.get(idx);
                cs.preampGain = v;
                cs.preampGainDb = ChannelState.gainToDb(v);
                channels.set(idx, cs);
                state.channels = channels;
                addRepoLog("SET /ch/" + ch + "/pha raw=" + String.format("%.4f", v) + " (" + cs.preampGainDbString() + ")");
                notifyStateChanged();
            }
            return;
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
        } catch (Exception e) { android.util.Log.e("WingRepo", "stateListener error", e); }
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
     * @param ch 1-48, @param muted true=muted, false=unmuted */
    public void setMute(int ch, boolean muted) {
        if (client != null) {
            client.send("/ch/" + ch + "/mute", muted ? 1 : 0);
            addRepoLog("SEND /ch/" + ch + "/mute=" + (muted ? 1 : 0));
        }
    }
}