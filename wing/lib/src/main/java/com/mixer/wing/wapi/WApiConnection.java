package com.mixer.wing.wapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WING wapi TCP Connection Manager
 * 
 * Manages TCP connection to WING console on port 2222.
 * Handles encoding/decoding, send/receive, and subscription callbacks.
 * 
 * Version: V1.0001
 */
public class WApiConnection {
    
    private static final String TAG = "WApiConnection";
    
    /** Default WING port */
    public static final int DEFAULT_PORT = 2222;
    
    /** Connection timeout (ms) */
    public static final int CONNECT_TIMEOUT = 5000;
    
    /** Socket read timeout (ms) */
    public static final int READ_TIMEOUT = 3000;
    
    /** Inactivity timeout before connection considered dead (ms) */
    public static final int INACTIVITY_TIMEOUT = 10000;
    
    private final String host;
    private final int port;
    
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    
    private final WApiBinaryEncoder encoder;
    private final WApiBinaryDecoder decoder;
    
    private final AtomicBoolean connected;
    private final AtomicInteger subscriptionId;
    
    private final CopyOnWriteArrayList<WApiCallback> callbacks;
    private final ExecutorService callbackExecutor;
    private final ExecutorService readerExecutor;
    
    private final AtomicBoolean running;
    
    /**
     * Create a new WING API connection
     * @param host WING console IP address
     * @param port TCP port (default 2222)
     */
    public WApiConnection(String host, int port) {
        this.host = host;
        this.port = port;
        this.encoder = new WApiBinaryEncoder();
        this.decoder = new WApiBinaryDecoder();
        this.connected = new AtomicBoolean(false);
        this.subscriptionId = new AtomicInteger(0);
        this.callbacks = new CopyOnWriteArrayList<>();
        this.callbackExecutor = Executors.newCachedThreadPool();
        this.readerExecutor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);
    }
    
    public WApiConnection(String host) {
        this(host, DEFAULT_PORT);
    }
    
    /**
     * Open connection to WING console
     * @return version string on success
     * @throws WApiException if connection fails
     */
    public String open() throws WApiException {
        if (connected.get()) {
            return "already connected";
        }
        
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            socket.setSoTimeout(READ_TIMEOUT);
            
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            
            // Send wOpen
            byte[] openReq = encoder.encodeOpen();
            outputStream.write(openReq);
            outputStream.flush();
            
            // Read wOpen response
            byte[] response = readResponse();
            WApiBinaryDecoder.WApiResponse resp = decoder.decode(response);
            
            if (resp == null || !resp.isSuccess()) {
                close();
                throw new WApiException("wOpen failed: " + (resp != null ? resp.toString() : "null response"));
            }
            
            connected.set(true);
            running.set(true);
            
            // Start reader thread for subscriptions
            readerExecutor.execute(this::readerLoop);
            
            String version = resp.value != null ? resp.value.toString() : "unknown";
            return version;
            
        } catch (IOException e) {
            close();
            throw new WApiException("Connection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Close connection to WING console
     */
    public void close() {
        running.set(false);
        connected.set(false);
        
        try {
            if (outputStream != null && socket != null && socket.isConnected()) {
                try {
                    byte[] closeReq = encoder.encodeClose();
                    outputStream.write(closeReq);
                    outputStream.flush();
                } catch (IOException ignored) {
                    // Ignore errors during close
                }
            }
        } catch (Exception ignored) {}
        
        try {
            if (inputStream != null) inputStream.close();
        } catch (Exception ignored) {}
        
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}
        
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        
        inputStream = null;
        outputStream = null;
        socket = null;
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected.get() && socket != null && socket.isConnected();
    }
    
    // ============================================================
    // wGet operations
    // ============================================================
    
    /**
     * Get float value from WING
     * @param token parameter token
     * @return float value
     * @throws WApiException on error
     */
    public float getFloat(int token) throws WApiException {
        checkConnected();
        
        try {
            byte[] req = encoder.encodeGetFloat(token);
            outputStream.write(req);
            outputStream.flush();
            
            byte[] resp = readResponse();
            WApiBinaryDecoder.WApiResponse wresp = decoder.decode(resp);
            
            if (wresp == null || !wresp.isSuccess()) {
                throw new WApiException("getFloat failed: " + wresp);
            }
            
            return wresp.getFloatValue();
            
        } catch (SocketTimeoutException e) {
            throw new WApiException("Read timeout", e);
        } catch (IOException e) {
            connected.set(false);
            throw new WApiException("IO error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get int value from WING
     * @param token parameter token
     * @return int value
     * @throws WApiException on error
     */
    public int getInt(int token) throws WApiException {
        checkConnected();
        
        try {
            byte[] req = encoder.encodeGetInt(token);
            outputStream.write(req);
            outputStream.flush();
            
            byte[] resp = readResponse();
            WApiBinaryDecoder.WApiResponse wresp = decoder.decode(resp);
            
            if (wresp == null || !wresp.isSuccess()) {
                throw new WApiException("getInt failed: " + wresp);
            }
            
            return wresp.getIntValue();
            
        } catch (SocketTimeoutException e) {
            throw new WApiException("Read timeout", e);
        } catch (IOException e) {
            connected.set(false);
            throw new WApiException("IO error: " + e.getMessage(), e);
        }
    }
    
    // ============================================================
    // wSet operations
    // ============================================================
    
    /**
     * Set float value on WING
     * @param token parameter token
     * @param value value to set
     * @throws WApiException on error
     */
    public void setFloat(int token, float value) throws WApiException {
        checkConnected();
        
        try {
            byte[] req = encoder.encodeSetFloat(token, value);
            outputStream.write(req);
            outputStream.flush();
            
            byte[] resp = readResponse();
            WApiBinaryDecoder.WApiResponse wresp = decoder.decode(resp);
            
            if (wresp == null || !wresp.isSuccess()) {
                throw new WApiException("setFloat failed: " + wresp);
            }
            
        } catch (SocketTimeoutException e) {
            throw new WApiException("Read timeout", e);
        } catch (IOException e) {
            connected.set(false);
            throw new WApiException("IO error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Set int value on WING
     * @param token parameter token
     * @param value value to set
     * @throws WApiException on error
     */
    public void setInt(int token, int value) throws WApiException {
        checkConnected();
        
        try {
            byte[] req = encoder.encodeSetInt(token, value);
            outputStream.write(req);
            outputStream.flush();
            
            byte[] resp = readResponse();
            WApiBinaryDecoder.WApiResponse wresp = decoder.decode(resp);
            
            if (wresp == null || !wresp.isSuccess()) {
                throw new WApiException("setInt failed: " + wresp);
            }
            
        } catch (SocketTimeoutException e) {
            throw new WApiException("Read timeout", e);
        } catch (IOException e) {
            connected.set(false);
            throw new WApiException("IO error: " + e.getMessage(), e);
        }
    }
    
    // ============================================================
    // wSubscribe operations
    // ============================================================
    
    /**
     * Subscribe to parameter changes
     * @param token parameter token
     * @param interval update interval in ms
     * @return subscription ID
     * @throws WApiException on error
     */
    public int subscribe(int token, int interval) throws WApiException {
        checkConnected();
        
        try {
            byte[] req = encoder.encodeSubscribe(token, interval);
            outputStream.write(req);
            outputStream.flush();
            
            byte[] resp = readResponse();
            WApiBinaryDecoder.WApiResponse wresp = decoder.decode(resp);
            
            if (wresp == null || !wresp.isSuccess()) {
                throw new WApiException("subscribe failed: " + wresp);
            }
            
            int subId = wresp.getIntValue();
            subscriptionId.set(subId);
            return subId;
            
        } catch (SocketTimeoutException e) {
            throw new WApiException("Read timeout", e);
        } catch (IOException e) {
            connected.set(false);
            throw new WApiException("IO error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Renew subscription
     * @param subscriptionId subscription ID from subscribe()
     * @throws WApiException on error
     */
    public void renew(int subscriptionId) throws WApiException {
        checkConnected();
        
        try {
            byte[] req = encoder.encodeRenew(subscriptionId);
            outputStream.write(req);
            outputStream.flush();
            
            byte[] resp = readResponse();
            WApiBinaryDecoder.WApiResponse wresp = decoder.decode(resp);
            
            if (wresp == null || !wresp.isSuccess()) {
                throw new WApiException("renew failed: " + wresp);
            }
            
        } catch (SocketTimeoutException e) {
            throw new WApiException("Read timeout", e);
        } catch (IOException e) {
            connected.set(false);
            throw new WApiException("IO error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Register callback for subscription updates
     */
    public void addCallback(WApiCallback callback) {
        callbacks.add(callback);
    }
    
    public void removeCallback(WApiCallback callback) {
        callbacks.remove(callback);
    }
    
    // ============================================================
    // Internal methods
    // ============================================================
    
    private void checkConnected() throws WApiException {
        if (!isConnected()) {
            throw new WApiException("Not connected to WING");
        }
    }
    
    private byte[] readResponse() throws IOException {
        // Read until we get ESCAPE end marker
        byte[] buf = new byte[512];
        int pos = 0;
        boolean foundEnd = false;
        
        while (pos < buf.length) {
            int b = inputStream.read();
            if (b == -1) {
                throw new IOException("Connection closed");
            }
            buf[pos++] = (byte) b;
            
            if (pos > 0 && buf[pos - 1] == WApiBinaryEncoder.ESCAPE) {
                // Check if this is an end marker (not a start)
                if (pos > 5) {
                    foundEnd = true;
                    break;
                }
            }
        }
        
        if (!foundEnd) {
            throw new IOException("Response incomplete");
        }
        
        byte[] result = new byte[pos];
        System.arraycopy(buf, 0, result, 0, pos);
        return result;
    }
    
    private void readerLoop() {
        while (running.get() && isConnected()) {
            try {
                byte[] data = readResponse();
                WApiBinaryDecoder.WApiResponse resp = decoder.decode(data);
                
                if (resp != null && resp.isSuccess()) {
                    for (WApiCallback cb : callbacks) {
                        callbackExecutor.execute(() -> cb.onUpdate(resp));
                    }
                }
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue
            } catch (Exception e) {
                if (running.get()) {
                    connected.set(false);
                }
                break;
            }
        }
    }
    
    /**
     * Get the connected host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Get the port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Shutdown and cleanup
     */
    public void shutdown() {
        close();
        callbackExecutor.shutdown();
        readerExecutor.shutdown();
        try {
            callbackExecutor.awaitTermination(1, TimeUnit.SECONDS);
            readerExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }
    
    /**
     * Callback interface for subscription updates
     */
    public interface WApiCallback {
        void onUpdate(WApiBinaryDecoder.WApiResponse response);
    }
}