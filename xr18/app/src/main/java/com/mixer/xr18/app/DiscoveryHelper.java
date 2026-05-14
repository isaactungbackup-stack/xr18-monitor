package com.mixer.xr18.app;

import com.mixer.xr18.lib.data.osc.OscClient;
import com.mixer.xr18.lib.data.osc.OSCMessage;
import com.mixer.xr18.lib.data.repository.XR18RepositoryImpl;
import com.mixer.xr18.lib.domain.model.ChannelState;
import com.mixer.xr18.lib.domain.model.MixerDevice;
import com.mixer.xr18.lib.domain.model.MixerState;
import com.mixer.xr18.lib.domain.repository.MixerRepository;
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase;
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase;
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase;
import com.mixer.xr18.lib.presentation.Xr18ViewModel;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class for discovery and connection operations.
 * Provides both sync (blocking) and async callback-based APIs for Java interop.
 */
public class DiscoveryHelper {
    private static final String TAG = "XR18Discovery";

    // Store messages for display
    private static final List<String> sentMessages = new ArrayList<>();
    private static final List<String> receivedMessages = new ArrayList<>();

    private static Xr18ViewModel viewModel;
    private static XR18RepositoryImpl repository;

    /** Java Consumer interface for log updates — set from Java Activity. */
    public interface LogConsumer {
        void accept(String msg);
    }
    private static LogConsumer logConsumer;

    public static void setLogConsumer(LogConsumer consumer) {
        logConsumer = consumer;
    }

    public static MixerDevice createDevice(String ip, String name, String model, String fw) {
        return new MixerDevice(ip, name, model, fw);
    }

    /**
     * Synchronously connect to an IP address (blocking, max ~4s).
     * @return true if XR18 responded on port 10024 or 10023
     */
    public static boolean connectToIpSync(final String ip) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = { false };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(2000);

                byte[] pingMsg = buildOscPing();
                InetAddress addr = InetAddress.getByName(ip);

                // Try port 10024 first
                DatagramPacket pkt = new DatagramPacket(pingMsg, pingMsg.length, addr, 10024);
                socket.send(pkt);
                addSent("/xinfo to 10024");

                byte[] buffer = new byte[4096];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(response);
                    result[0] = true;
                    addReceived("Got response from " + ip);
                } catch (java.net.SocketTimeoutException e) {
                    addReceived("Timeout from " + ip + ":10024 - trying 10023");
                    // Try port 10023
                    DatagramPacket pkt2 = new DatagramPacket(pingMsg, pingMsg.length, addr, 10023);
                    socket.send(pkt2);
                    addSent("/xinfo to 10023");
                    try {
                        socket.receive(response);
                        result[0] = true;
                        addReceived("Got response from " + ip + ":10023");
                    } catch (java.net.SocketTimeoutException e2) {
                        addReceived("Timeout from " + ip + ":10023");
                        result[0] = false;
                    }
                }
            } catch (Exception e) {
                addReceived("Connect error: " + e.getMessage());
                result[0] = false;
            } finally {
                if (socket != null) socket.close();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            return false;
        }
        executor.shutdownNow();
        return result[0];
    }

    /**
     * Synchronously discover XR18 devices on the LAN (blocking, max ~3s).
     * @return list of discovered MixerDevices
     */
    public static List<MixerDevice> discoverSync() {
        sentMessages.clear();
        receivedMessages.clear();

        final CountDownLatch latch = new CountDownLatch(1);
        final List<MixerDevice>[] result = new List[1];
        result[0] = new ArrayList<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(3000);
                socket.setBroadcast(true);

                byte[] triggerData = new byte[1];
                InetAddress broadcastAddr = InetAddress.getByAddress(
                    new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }
                );
                DatagramPacket trigger = new DatagramPacket(
                    triggerData, triggerData.length, broadcastAddr, 10024
                );
                socket.send(trigger);
                addSent("broadcast to 10024");

                byte[] buffer = new byte[2048];
                long deadline = System.currentTimeMillis() + 3000;
                List<MixerDevice> foundDevices = new ArrayList<>();

                while (System.currentTimeMillis() < deadline) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                        socket.receive(pkt);

                        String srcIP = pkt.getAddress().getHostAddress();
                        int pktLen = pkt.getLength();
                        int addrEnd = findNullTerminator(buffer, 0, pktLen);
                        String address = new String(buffer, 0, addrEnd, java.nio.charset.StandardCharsets.UTF_8);

                        addReceived("from " + srcIP + ": " + address);

                        if (address.equals("/xinfo")) {
                            int typeTagOffset = (addrEnd + 4) & 0x7FFFFFFC;
                            if (typeTagOffset < pktLen && buffer[typeTagOffset] == 0x2C) {
                                List<String> strings = parseOSCStrings(buffer, typeTagOffset + 1, pktLen);
                                if (strings.size() >= 3) {
                                    MixerDevice device = new MixerDevice(
                                        srcIP, strings.get(0), strings.get(1), strings.get(2)
                                    );
                                    foundDevices.add(device);
                                }
                            }
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        break;
                    }
                }

                result[0] = foundDevices;

            } catch (Exception e) {
                result[0] = new ArrayList<>();
            } finally {
                if (socket != null) socket.close();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            return new ArrayList<>();
        }
        executor.shutdownNow();
        return result[0];
    }

    /**
     * Async query of all channel states. Results delivered via callback.
     */
    public static void queryChannels(MixerDevice device, QueryCallback callback) {
        sentMessages.clear();
        receivedMessages.clear();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                repository = new XR18RepositoryImpl();

                repository.setOscMessageListener(new MixerRepository.OscMessageListener() {
                    @Override
                    public void onOscMessage(String type, String message) {
                        if ("SEND".equals(type)) addSent(message);
                        else addReceived(message);
                    }
                });

                repository.setMixerStateListener(new MixerRepository.MixerStateListener() {
                    @Override
                    public void onMixerStateChanged(MixerState state) {
                        // State updated — will be retrieved after sleep
                    }
                });

                repository.queryChannelStates(device);

                // Wait for responses to arrive
                try { Thread.sleep(5000); } catch (InterruptedException e) { }

                addSent("Query complete");

                // Extract channel states from repository's current state
                ChannelState[] result = null;
                try {
                    MixerState currentState = repository.getMixerState();
                    if (currentState != null && currentState.channels != null) {
                        result = currentState.channels.toArray(new ChannelState[0]);
                        addReceived("getMixerState returned " + result.length + " channels");
                        // Log first channel's fader value
                        if (result.length > 0) {
                            addReceived("CH1 fader=" + result[0].fader + " muted=" + result[0].muted);
                        }
                    } else {
                        addReceived("getMixerState returned null or empty");
                    }
                } catch (Exception ex) {
                    addReceived("Could not get mixer state: " + ex.getMessage());
                }
                callback.onResult(result);

            } catch (Exception e) {
                addReceived("Query error: " + e.getMessage());
                callback.onResult(null);
            }
        });
    }

    public static String getDebugMessages() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SENT ===\n");
        List<String> sent = sentMessages;
        for (int i = Math.max(0, sent.size() - 20); i < sent.size(); i++) {
            sb.append(sent.get(i)).append("\n");
        }
        sb.append("\n=== RECEIVED ===\n");
        for (int i = Math.max(0, receivedMessages.size() - 20); i < receivedMessages.size(); i++) {
            sb.append(receivedMessages.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static void addSent(String msg) {
        android.util.Log.d(TAG, "SEND: " + msg);
        sentMessages.add("[SEND] " + msg);
        if (sentMessages.size() > 100) sentMessages.remove(0);
        if (logConsumer != null) logConsumer.accept("[SEND] " + msg);
    }

    private static void addReceived(String msg) {
        android.util.Log.d(TAG, "RECV: " + msg);
        receivedMessages.add("[RECV] " + msg);
        if (receivedMessages.size() > 100) receivedMessages.remove(0);
        if (logConsumer != null) logConsumer.accept("[RECV] " + msg);
    }

    private static byte[] buildOscPing() {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String addr = "/xinfo";
        byte[] addrBytes = addr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        baos.write(addrBytes, 0, addrBytes.length);
        baos.write(0);
        while (baos.size() % 4 != 0) baos.write(0);
        baos.write(0);  // empty type tag
        baos.write(0);
        while (baos.size() % 4 != 0) baos.write(0);
        return baos.toByteArray();
    }

    private static int findNullTerminator(byte[] data, int start, int end) {
        int i = start;
        while (i < end && data[i] != 0) i++;
        return Math.min(i, end);
    }

    private static List<String> parseOSCStrings(byte[] data, int start, int length) {
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
}