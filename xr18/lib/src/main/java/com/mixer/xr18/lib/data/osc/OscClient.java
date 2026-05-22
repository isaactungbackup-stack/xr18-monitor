package com.mixer.xr18.lib.data.osc;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OSC client using raw Java sockets.
 * Uses ephemeral source port (0) to avoid binding conflicts.
 *
 * NOTE: Parsing logic in this file is kept as-is from the original Kotlin.
 * Do not modify OSCMessage parsing until confirmed by the requester.
 */
public class OscClient {
    private static final String TAG = "OscClient";

    private final String mixerIp;
    private final int mixerPort;

    private DatagramSocket socket;
    private ExecutorService recvExecutor;
    private ExecutorService sendExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** Called for each received OSC message. Runs on receive thread. */
    public interface MessageListener {
        void onMessage(OSCMessage msg);
    }
    private MessageListener messageListener;

    /** Called for raw log lines. Runs on receive thread. */
    public interface LogListener {
        void onLog(String type, String msg);
    }
    private LogListener logListener;

    public OscClient(String mixerIp, int mixerPort) {
        this.mixerIp = mixerIp;
        this.mixerPort = mixerPort;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    public void start() {
        if (isRunning.getAndSet(true)) return;

        recvExecutor = Executors.newSingleThreadExecutor();
        sendExecutor = Executors.newSingleThreadExecutor();
        recvExecutor.execute(this::receiveLoop);
    }

    private void receiveLoop() {
        try {
            // CRITICAL: Bind to port 0 (ephemeral) — OS assigns available port
            // XR18 replies to the source port of our query, so no conflict!
            socket = new DatagramSocket(0);
            socket.setReuseAddress(true);
            socket.setSoTimeout(2000);

            int localPort = socket.getLocalPort();
            log("Socket on ephemeral port " + localPort);

            byte[] buffer = new byte[4096];
            while (isRunning.get()) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);
                    int len = pkt.getLength();

                    log("RECV " + len + " bytes");
                    OSCMessage msg = new OSCMessage(buffer, len);
                    log("RECV addr=" + msg.address);

                    if (messageListener != null) {
                        try { messageListener.onMessage(msg); }
                        catch (Exception e) { android.util.Log.e("OscClient", "messageListener error", e); }
                    }

                } catch (java.net.SocketTimeoutException e) {
                    // Normal — continue
                } catch (Exception e) {
                    log("Recv error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log("Socket start failed: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }

    public void send(String address, Object... args) {
        try {
            byte[] packet = buildOscPacket(address, args);
            sendRaw(packet);
        } catch (Exception e) {
            log("Send FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Send a raw pre-built OSC packet directly. Used for subscribe packets with string args. */
    public void sendRaw(byte[] packet) {
        try {
            log("SEND raw " + packet.length + " bytes");
            InetAddress addr = InetAddress.getByName(mixerIp);
            final DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, mixerPort);
            sendExecutor.execute(() -> {
                try {
                    socket.send(dp);
                } catch (Exception e) {
                    android.util.Log.e("OscClient", "send failed", e);
                }
            });
        } catch (Exception e) {
            log("SendRaw FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Build and send an OSC subscribe message.
     * Used for /meters subscription: /meters <subscribePath> <unused> <chnmeterid>
     * Format: /meters ,ssi <subscribePath> <unused> <chnmeterid>
     */
    public void sendSubscribe(String subscribePath, int chnmeterid) {
        byte[] packet = buildSubscribePacket(subscribePath, chnmeterid);
        sendRaw(packet);
    }

    private byte[] buildSubscribePacket(String subscribePath, int chnmeterid) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            // Address: /meters
            byte[] addrBytes = "/meters".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(addrBytes, 0, addrBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);

            // Type tag: ,ssi
            baos.write(44); // ','
            baos.write(115); // 's'
            baos.write(115); // 's'
            baos.write(105); // 'i'
            while (baos.size() % 4 != 0) baos.write(0);

            // String arg 1: subscription path (e.g. /meters/1)
            byte[] subPathBytes = subscribePath.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(subPathBytes, 0, subPathBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);

            // String arg 2: unused ( XR18 expects this, can be /none or /null)
            byte[] unusedBytes = "/none".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(unusedBytes, 0, unusedBytes.length);
            baos.write(0);
            while (baos.size() % 4 != 0) baos.write(0);

            // Int arg: chnmeterid
            int val = chnmeterid;
            baos.write((val >> 24) & 0xFF);
            baos.write((val >> 16) & 0xFF);
            baos.write((val >> 8) & 0xFF);
            baos.write(val & 0xFF);
        } catch (Exception e) { }
        return baos.toByteArray();
    }

    public void stop() {
        isRunning.set(false);
        closeSocket();
        if (recvExecutor != null) {
            recvExecutor.shutdownNow();
            recvExecutor = null;
        }
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
            sendExecutor = null;
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (Exception e) { }
    }

    private byte[] buildOscPacket(String address, Object[] args) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        // Address
        byte[] addrBytes = address.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        baos.write(addrBytes, 0, addrBytes.length);
        baos.write(0); // null terminator
        while (baos.size() % 4 != 0) baos.write(0);

        // Type tag
        baos.write(44); // ','
        for (Object arg : args) {
            if (arg instanceof Integer) baos.write(105);  // 'i'
            else if (arg instanceof Float) baos.write(102); // 'f'
            else if (arg instanceof String) baos.write(115); // 's'
        }
        while (baos.size() % 4 != 0) baos.write(0);

        // Arguments
        for (Object arg : args) {
            if (arg instanceof Integer) {
                int v = (Integer) arg;
                baos.write((v >> 24) & 0xFF);
                baos.write((v >> 16) & 0xFF);
                baos.write((v >> 8) & 0xFF);
                baos.write(v & 0xFF);
            } else if (arg instanceof Float) {
                int bits = Float.floatToIntBits((Float) arg);
                baos.write((bits >> 24) & 0xFF);
                baos.write((bits >> 16) & 0xFF);
                baos.write((bits >> 8) & 0xFF);
                baos.write(bits & 0xFF);
            } else if (arg instanceof String) {
                byte[] strBytes = ((String) arg).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                baos.write(strBytes, 0, strBytes.length);
                baos.write(0);
                while (baos.size() % 4 != 0) baos.write(0);
            }
        }

        return baos.toByteArray();
    }

    private void log(String msg) {
        if (logListener != null) {
            logListener.onLog("RECV", msg);
        }
    }
}