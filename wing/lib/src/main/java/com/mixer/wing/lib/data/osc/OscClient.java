package com.mixer.wing.lib.data.osc;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OSC UDP client for WING mixer communication.
 * WING uses OSC on port 10024.
 */
public class OscClient {
    private static final String TAG = "WingOscClient";

    private final String mixerIp;
    private final int mixerPort;

    private DatagramSocket socket;
    private ExecutorService recvExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public interface MessageListener {
        void onMessage(OSCMessage msg);
    }
    private MessageListener messageListener;

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
            log("SEND " + address + " (" + packet.length + " bytes)");
            InetAddress addr = InetAddress.getByName(mixerIp);
            DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, mixerPort);
            socket.send(dp);
        } catch (Exception e) {
            log("Send FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Send with string argument (used for subscriptions like /s~ /meters/1 1). */
    public void send(String address, String arg1, int arg2) {
        try {
            byte[] packet = buildOscPacket(address, arg1, arg2);
            log("SEND " + address + " " + arg1 + " " + arg2 + " (" + packet.length + " bytes)");
            InetAddress addr = InetAddress.getByName(mixerIp);
            DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, mixerPort);
            socket.send(dp);
        } catch (Exception e) {
            log("Send FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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

    private byte[] buildOscPacket(String address, Object... args) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        byte[] addrBytes = address.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        baos.write(addrBytes, 0, addrBytes.length);
        baos.write(0);
        while (baos.size() % 4 != 0) baos.write(0);

        baos.write(44); // ','
        for (Object arg : args) {
            if (arg instanceof Integer) baos.write(105);  // 'i'
            else if (arg instanceof Float) baos.write(102); // 'f'
            else if (arg instanceof String) baos.write(115); // 's'
        }
        while (baos.size() % 4 != 0) baos.write(0);

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
            logListener.onLog("OSC", msg);
        }
    }
}