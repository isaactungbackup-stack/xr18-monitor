package com.mixer.wing.lib.data.discovery;

import com.mixer.wing.lib.domain.model.MixerDevice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LAN discovery for WING mixers via UDP broadcast.
 * WING uses the same /xinfo discovery mechanism as XR18.
 */
public class WingDiscoveryClient {
    private final int broadcastPort;
    private final long timeoutMs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class DiscoveryResult {
        public final String ipAddress;
        public final String deviceName;
        public final String model;
        public final String firmwareVersion;

        public DiscoveryResult(String ipAddress, String deviceName, String model, String firmwareVersion) {
            this.ipAddress = ipAddress;
            this.deviceName = deviceName;
            this.model = model;
            this.firmwareVersion = firmwareVersion;
        }
    }

    public interface DiscoveryCallback {
        void onResult(List<DiscoveryResult> results);
    }

    public WingDiscoveryClient(int broadcastPort, long timeoutMs) {
        this.broadcastPort = broadcastPort;
        this.timeoutMs = timeoutMs;
    }

    public WingDiscoveryClient() {
        this(10024, 3000);
    }

    public void discover(DiscoveryCallback callback) {
        executor.submit(() -> {
            List<DiscoveryResult> results = new ArrayList<>();
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
                    triggerData, triggerData.length, broadcastAddr, broadcastPort
                );
                socket.send(trigger);

                byte[] buffer = new byte[2048];
                long deadline = System.currentTimeMillis() + timeoutMs;

                while (System.currentTimeMillis() < deadline) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                        socket.receive(pkt);
                        DiscoveryResult result = parseXInfo(buffer, pkt.getLength(), pkt.getAddress().getHostAddress());
                        if (result != null) results.add(result);
                    } catch (java.net.SocketTimeoutException e) {
                        break;
                    }
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (socket != null) socket.close();
            }
            callback.onResult(results);
        });
    }

    private DiscoveryResult parseXInfo(byte[] data, int length, String sourceIp) {
        try {
            if (sourceIp == null) return null;

            int addrEnd = findNullTerminator(data, 0, length);
            String address = new String(data, 0, addrEnd, java.nio.charset.StandardCharsets.UTF_8);
            if (!address.equals("/xinfo")) return null;

            int typeTagOffset = (addrEnd + 4) & 0x7FFFFFFC;
            if (typeTagOffset >= length || data[typeTagOffset] != 0x2C) return null;

            List<String> strings = parseOSCStrings(data, typeTagOffset + 1, length);
            if (strings.size() < 3) return null;

            return new DiscoveryResult(sourceIp, strings.get(0), strings.get(1), strings.get(2));
        } catch (Exception e) {
            return null;
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

    public void shutdown() {
        executor.shutdownNow();
    }
}