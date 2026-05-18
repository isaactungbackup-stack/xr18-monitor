package com.mixer.wing.app;

import com.mixer.wing.lib.data.osc.OscClient;
import com.mixer.wing.lib.data.osc.OSCMessage;
import com.mixer.wing.lib.data.repository.WingRepositoryImpl;
import com.mixer.wing.lib.domain.model.ChannelState;
import com.mixer.wing.lib.domain.model.MixerDevice;
import com.mixer.wing.lib.domain.model.MixerState;
import com.mixer.wing.lib.domain.repository.MixerRepository;
import com.mixer.wing.lib.domain.usecase.DiscoverMixersUseCase;
import com.mixer.wing.lib.domain.usecase.QueryChannelStatesUseCase;
import com.mixer.wing.lib.domain.usecase.ObserveMixerStateUseCase;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WINGDiscoveryHelper {
    private static final String TAG = "WINGDiscovery";

    private static final List<String> sentMessages = new ArrayList<>();
    private static final List<String> receivedMessages = new ArrayList<>();

    private static WingRepositoryImpl repository;
    private static OscClient oscClient;

    public interface LogConsumer {
        void accept(String msg);
    }
    private static LogConsumer logConsumer;

    public interface StateUpdateListener {
        void onStatesUpdated(ChannelState[] states);
    }
    private static StateUpdateListener stateUpdateListener;

    public static void setLogConsumer(LogConsumer consumer) {
        logConsumer = consumer;
    }

    public static void setStateUpdateListener(StateUpdateListener listener) {
        stateUpdateListener = listener;
        if (repository != null) {
            repository.setMixerStateListener(state -> {
                if (stateUpdateListener != null && state.getChannels() != null) {
                    stateUpdateListener.onStatesUpdated(state.getChannels());
                }
            });
        }
    }

    public static MixerDevice createDevice(String ip, String name, String model, String fw) {
        return new MixerDevice(ip, name, model, fw);
    }

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
                    addReceived("Timeout from " + ip + ":10024");
                    result[0] = false;
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

    public static void queryChannels(MixerDevice device, QueryCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (repository == null) {
                    repository = new WingRepositoryImpl();
                    repository.setDeviceIp(device.getIpAddress());
                }

                // Subscribe to state updates - pass through to UI listener
                repository.setMixerStateListener(state -> {
                    if (stateUpdateListener != null && state.getChannels() != null) {
                        stateUpdateListener.onStatesUpdated(state.getChannels());
                    }
                });

                // Trigger full channel query
                repository.queryChannelStates(device);

                // Give it 2 seconds to collect responses, then return current state
                Thread.sleep(2000);

                ChannelState[] states = repository.getMixerState().getChannels();
                callback.onResult(states != null ? states : new ChannelState[0]);

            } catch (Exception e) {
                addSent("Query error: " + e.getMessage());
                callback.onResult(null);
            } finally {
                executor.shutdownNow();
            }
        });
    }

    public interface QueryCallback {
        void onResult(ChannelState[] result);
    }

    public static void setMute(int channel, boolean muted) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (repository == null) {
                    repository = new WingRepositoryImpl();
                    repository.setDeviceIp("192.168.31.100");
                }
                repository.setMute(channel, muted);
                addSent("setMute CH" + channel + " = " + muted);
            } catch (Exception e) {
                addSent("setMute error: " + e.getMessage());
            } finally {
                executor.shutdownNow();
            }
        });
    }

    public static String getDebugMessages() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WING Debug ===\n");
        sb.append("=== Sent ===\n");
        for (String s : sentMessages) {
            sb.append(s).append("\n");
        }
        sb.append("=== Received ===\n");
        for (String s : receivedMessages) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

    private static byte[] buildOscPing() {
        // Simple OSC message: /xinfo
        String addr = "/xinfo";
        byte[] addrBytes = addr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int paddedLen = (addrBytes.length + 4) & 0x7FFFFFFC;
        byte[] msg = new byte[paddedLen + 4];
        System.arraycopy(addrBytes, 0, msg, 0, addrBytes.length);
        // OSC string padding already 0
        msg[addrBytes.length] = 0;
        int typeTagOffset = paddedLen;
        msg[typeTagOffset] = 0; // ',' type tag
        return msg;
    }

    public static void addSent(String msg) {
        sentMessages.add(msg);
        if (sentMessages.size() > 100) sentMessages.remove(0);
        if (logConsumer != null) logConsumer.accept("[SEND] " + msg);
    }

    private static void addReceived(String msg) {
        receivedMessages.add(msg);
        if (receivedMessages.size() > 100) receivedMessages.remove(0);
        if (logConsumer != null) logConsumer.accept("[RECV] " + msg);
    }

    private static int findNullTerminator(byte[] buf, int start, int len) {
        for (int i = start; i < len; i++) {
            if (buf[i] == 0) return i;
        }
        return len;
    }

    private static List<String> parseOSCStrings(byte[] buf, int offset, int len) {
        List<String> result = new ArrayList<>();
        int pos = offset;
        while (pos < len && buf[pos] != 0) {
            int start = pos;
            while (pos < len && buf[pos] != 0) pos++;
            result.add(new String(buf, start, pos - start, java.nio.charset.StandardCharsets.UTF_8));
            pos = (pos + 4) & 0x7FFFFFFC;
        }
        return result;
    }
}