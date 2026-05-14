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

                    // Log raw bytes
                    StringBuilder hex = new StringBuilder();
                    StringBuilder ascii = new StringBuilder();
                    for (int i = 0; i < len; i++) {
                        int b = buffer[i] & 0xFF;
                        hex.append(String.format("%02X ", b));
                        ascii.append((b >= 0x20 && b <= 0x7E) ? (char) b : '.');
                    }
                    log("RAW[" + len + "] hex=" + hex.toString());
                    log("RAW[" + len + "] ascii=" + ascii.toString());

                    OSCMessage msg = new OSCMessage(buffer, len);
                    log("RECV addr=" + msg.address + " args.length=" + msg.args.length);

                    if (messageListener != null) {
                        messageListener.onMessage(msg);
                    }

                } catch (java.net.SocketTimeoutException e) {
                    // Normal — continue
                } catch (Exception e) {
                    log("Recv error: " + e.getMessage());
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
            android.util.Log.d("OscClient","sending: "+address);
            byte[] packet = buildOscPacket(address, args);

            StringBuilder hex = new StringBuilder();
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < packet.length; i++) {
                int b = packet[i] & 0xFF;
                hex.append(String.format("%02X ", b));
                ascii.append((b >= 0x20 && b <= 0x7E) ? (char) b : '.');
            }
            log("SEND[" + address + "] len=" + packet.length);
            log("  hex= " + hex.toString());
            log("  ascii=" + ascii.toString());

            InetAddress addr = InetAddress.getByName(mixerIp);
            DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, mixerPort);
            socket.send(dp);
            android.util.Log.d("OscClient", "SENDDONE " + address);
            log("SEND OK to " + mixerIp + ":" + mixerPort);

        } catch (Exception e) {
            log("Send FAILED: " + e.getMessage());
        }
    }

    public void stop() {
        isRunning.set(false);
        closeSocket();
        if (recvExecutor != null) {
            recvExecutor.shutdownNow();
            recvExecutor = null;
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