package com.mixer.wing.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * WING Device Discovery
 * 
 * Discovers WING consoles on the local network via UDP broadcast.
 * Sends "WING?" to port 2222 and parses "WING," responses.
 * 
 * Version: V1.0001
 */
public class WingDiscovery {
    
    /** Discovery broadcast address */
    public static final String BROADCAST_ADDR = "255.255.255.255";
    
    /** WING discovery port */
    public static final int DISCOVERY_PORT = 2222;
    
    /** Discovery timeout */
    public static final int TIMEOUT_MS = 3000;
    
    private static final String DISCOVERY_REQUEST = "WING?";
    private static final String DISCOVERY_RESPONSE_PREFIX = "WING,";
    
    private final List<WingDevice> foundDevices;
    
    public WingDiscovery() {
        this.foundDevices = new ArrayList<>();
    }
    
    /**
     * Discover all WING devices on the network
     * @return list of discovered devices
     * @throws IOException on network error
     */
    public List<WingDevice> discover() throws IOException {
        return discover(BROADCAST_ADDR);
    }
    
    /**
     * Discover WING devices on a specific broadcast address
     * @param broadcastAddr broadcast address (e.g., "192.168.1.255")
     * @return list of discovered devices
     * @throws IOException on network error
     */
    public List<WingDevice> discover(String broadcastAddr) throws IOException {
        foundDevices.clear();
        
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.setSoTimeout(TIMEOUT_MS);
        
        try {
            // Send discovery broadcast
            byte[] requestData = DISCOVERY_REQUEST.getBytes();
            InetAddress broadcast = InetAddress.getByName(broadcastAddr);
            DatagramPacket request = new DatagramPacket(
                requestData, 
                requestData.length,
                broadcast, 
                DISCOVERY_PORT
            );
            socket.send(request);
            
            // Collect responses
            byte[] buffer = new byte[1024];
            
            while (true) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    
                    String responseStr = new String(
                        response.getData(), 
                        response.getOffset(), 
                        response.getLength()
                    );
                    
                    WingDevice device = parseResponse(responseStr, response.getAddress());
                    if (device != null) {
                        foundDevices.add(device);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout - no more responses
                    break;
                }
            }
            
        } finally {
            socket.close();
        }
        
        return foundDevices;
    }
    
    /**
     * Parse WING discovery response
     * Format: WING,[ip],[name],[model],[serial],[firmware]
     */
    private WingDevice parseResponse(String response, InetAddress addr) {
        if (response == null || !response.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
            return null;
        }
        
        try {
            String[] parts = response.substring(5).split(",");
            if (parts.length < 5) {
                return null;
            }
            
            // parts[0] = ip (may be same as addr)
            String ip = parts[0].trim();
            if (ip.isEmpty()) {
                ip = addr.getHostAddress();
            }
            
            String name = parts[1].trim();
            String model = parts[2].trim();
            String serial = parts[3].trim();
            String firmware = parts[4].trim();
            
            return new WingDevice(ip, name, model, serial, firmware);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get list of devices found in last discovery
     */
    public List<WingDevice> getDevices() {
        return new ArrayList<>(foundDevices);
    }
    
    // ============================================================
    // Device info container
    // ============================================================
    
    public static class WingDevice {
        public final String ip;
        public final String name;
        public final String model;
        public final String serial;
        public final String firmware;
        
        public WingDevice(String ip, String name, String model, String serial, String firmware) {
            this.ip = ip;
            this.name = name;
            this.model = model;
            this.serial = serial;
            this.firmware = firmware;
        }
        
        @Override
        public String toString() {
            return String.format("%s [%s] %s FW:%s (%s)", 
                name.isEmpty() ? "WING" : name,
                ip, 
                model, 
                firmware,
                serial
            );
        }
        
        /**
         * Get short description
         */
        public String getShortDesc() {
            return (name.isEmpty() ? "WING" : name) + " @" + ip;
        }
    }
}