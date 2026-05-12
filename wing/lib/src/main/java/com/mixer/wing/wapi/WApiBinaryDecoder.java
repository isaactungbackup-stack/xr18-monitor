package com.mixer.wing.wapi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * WING wapi Binary Protocol Decoder
 * 
 * Decodes binary responses from WING console.
 * Handles escape sequences (0xdf boundaries) and parses payloads.
 * 
 * Version: V1.0001
 */
public class WApiBinaryDecoder {
    
    public static final byte ESCAPE = (byte) 0xdf;
    
    // Response status codes
    public static final int WSUCCESS = 0;
    public static final int WERROR = -1;
    public static final int WTOKEN = -2;      // Invalid token
    public static final int WNODE = -3;       // Node error
    public static final int WTYPE = -4;       // Type mismatch
    public static final int WSEND_TCP_ERROR = -5;  // TCP error
    
    private final ByteBuffer buffer;
    
    public WApiBinaryDecoder() {
        this.buffer = ByteBuffer.allocate(4096);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    
    /**
     * Parse a single response message
     * @param data raw bytes from socket
     * @return parsed response, or null if incomplete
     */
    public WApiResponse decode(byte[] data) {
        if (data == null || data.length < 5) {
            return null;
        }
        
        try {
            buffer.clear();
            buffer.put(data);
            buffer.flip();
            
            // Find first ESCAPE
            if (buffer.get() != ESCAPE) {
                return new WApiResponse(WERROR, "Missing start escape");
            }
            
            byte channel = buffer.get();
            short len = buffer.getShort();
            
            // Validate length
            if (len < 1 || len > buffer.remaining()) {
                return new WApiResponse(WERROR, "Invalid length: " + len);
            }
            
            byte command = buffer.get();
            byte[] payload = new byte[len - 1];
            buffer.get(payload);
            
            // Verify end ESCAPE
            if (buffer.hasRemaining() && buffer.get() != ESCAPE) {
                return new WApiResponse(WERROR, "Missing end escape");
            }
            
            return parsePayload(command, payload);
            
        } catch (Exception e) {
            return new WApiResponse(WERROR, "Decode error: " + e.getMessage());
        }
    }
    
    /**
     * Parse multiple messages from a stream
     * @param data raw bytes (may contain multiple ESCAPE-delimited messages)
     * @return list of parsed responses
     */
    public List<WApiResponse> decodeAll(byte[] data) {
        List<WApiResponse> responses = new ArrayList<>();
        List<byte[]> messages = splitMessages(data);
        
        for (byte[] msg : messages) {
            WApiResponse resp = decode(msg);
            if (resp != null) {
                responses.add(resp);
            }
        }
        
        return responses;
    }
    
    /**
     * Split raw stream into individual messages
     */
    private List<byte[]> splitMessages(byte[] data) {
        List<byte[]> messages = new ArrayList<>();
        List<Byte> current = new ArrayList<>();
        
        for (byte b : data) {
            if (b == ESCAPE) {
                if (!current.isEmpty()) {
                    // This is an end marker
                    byte[] msg = new byte[current.size()];
                    for (int i = 0; i < current.size(); i++) {
                        msg[i] = current.get(i);
                    }
                    messages.add(msg);
                    current.clear();
                }
                // Start new message
                current.add(b);
            } else {
                current.add(b);
            }
        }
        
        return messages;
    }
    
    /**
     * Parse response payload based on command type
     */
    private WApiResponse parsePayload(byte command, byte[] payload) {
        ByteBuffer pb = ByteBuffer.wrap(payload);
        pb.order(ByteOrder.LITTLE_ENDIAN);
        
        switch (command) {
            case 0x01: // wOpen response (version string)
                String version = new String(payload);
                return new WApiResponse(WSUCCESS, version);
                
            case 0x03: // wGet response (float value)
                if (payload.length >= 4) {
                    float fval = pb.getFloat();
                    return new WApiResponse(WSUCCESS, fval);
                }
                return new WApiResponse(WERROR, "Payload too short for float");
                
            case 0x04: // wSet response (success/fail)
                if (payload.length >= 4) {
                    int status = pb.getInt();
                    return new WApiResponse(status, null);
                }
                return new WApiResponse(WERROR, "Payload too short for status");
                
            case 0x05: // wSubscribe response (subscription ID)
                if (payload.length >= 4) {
                    int subId = pb.getInt();
                    return new WApiResponse(WSUCCESS, subId);
                }
                return new WApiResponse(WERROR, "Payload too short for subscription");
                
            default:
                return new WApiResponse(WSUCCESS, payload);
        }
    }
    
    /**
     * Extract float value from GET response payload
     */
    public static float extractFloat(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return 0f;
        }
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getFloat();
    }
    
    /**
     * Extract int value from GET response payload
     */
    public static int extractInt(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return 0;
        }
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt();
    }
    
    /**
     * WING wapi Response container
     */
    public static class WApiResponse {
        public final int status;
        public final Object value;
        
        public WApiResponse(int status, Object value) {
            this.status = status;
            this.value = value;
        }
        
        public boolean isSuccess() {
            return status == WSUCCESS;
        }
        
        public float getFloatValue() {
            if (value instanceof Float) return (Float) value;
            if (value instanceof Number) return ((Number) value).floatValue();
            return 0f;
        }
        
        public int getIntValue() {
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Number) return ((Number) value).intValue();
            return 0;
        }
        
        @Override
        public String toString() {
            if (status == WSUCCESS) {
                return "OK: " + value;
            }
            return "ERR[" + status + "]: " + value;
        }
    }
}