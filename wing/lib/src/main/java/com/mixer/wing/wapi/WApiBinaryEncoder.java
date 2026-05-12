package com.mixer.wing.wapi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WING wapi Binary Protocol Encoder
 * 
 * Encodes wapi requests into binary format for TCP transmission.
 * Format: [0xdf][channel][length_le][command][payload][0xdf]
 * 
 * Version: V1.0001
 */
public class WApiBinaryEncoder {
    
    /** Escape byte marking message boundaries */
    public static final byte ESCAPE = (byte) 0xdf;
    
    /** Channel 1 - Audio Engine & Control */
    public static final byte CHANNEL_CONTROL = 0x01;
    
    /** Channel 3 - Meter Data Requests */
    public static final byte CHANNEL_METER = 0x03;
    
    // Command opcodes
    public static final byte CMD_OPEN = 0x01;
    public static final byte CMD_CLOSE = 0x02;
    public static final byte CMD_GET = 0x03;
    public static final byte CMD_SET = 0x04;
    public static final byte CMD_SUBSCRIBE = 0x05;
    public static final byte CMD_RENEW = 0x06;
    public static final byte CMD_GET_NODE = 0x07;
    public static final byte CMD_SET_NODE = 0x08;
    
    private final ByteBuffer buffer;
    
    public WApiBinaryEncoder() {
        this.buffer = ByteBuffer.allocate(512);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    
    /**
     * Encode wOpen request
     * Establishes connection to WING console
     */
    public byte[] encodeOpen() {
        buffer.clear();
        writeMessage(CHANNEL_CONTROL, CMD_OPEN, new byte[0]);
        return extractBytes();
    }
    
    /**
     * Encode wClose request
     * Closes connection to WING console
     */
    public byte[] encodeClose() {
        buffer.clear();
        writeMessage(CHANNEL_CONTROL, CMD_CLOSE, new byte[0]);
        return extractBytes();
    }
    
    /**
     * Encode wGetFloat request
     * @param token 32-bit token identifying the parameter
     * @return encoded request packet
     */
    public byte[] encodeGetFloat(int token) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(token);
        writeMessage(CHANNEL_CONTROL, CMD_GET, payload.array());
        return extractBytes();
    }
    
    /**
     * Encode wGetInt request
     * @param token 32-bit token identifying the parameter
     * @return encoded request packet
     */
    public byte[] encodeGetInt(int token) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(token);
        writeMessage(CHANNEL_CONTROL, CMD_GET, payload.array());
        return extractBytes();
    }
    
    /**
     * Encode wSetFloat request
     * @param token 32-bit token identifying the parameter
     * @param value float value to set
     * @return encoded request packet
     */
    public byte[] encodeSetFloat(int token, float value) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(8);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(token);
        payload.putFloat(value);
        writeMessage(CHANNEL_CONTROL, CMD_SET, payload.array());
        return extractBytes();
    }
    
    /**
     * Encode wSetInt request
     * @param token 32-bit token identifying the parameter
     * @param value int value to set
     * @return encoded request packet
     */
    public byte[] encodeSetInt(int token, int value) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(8);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(token);
        payload.putInt(value);
        writeMessage(CHANNEL_CONTROL, CMD_SET, payload.array());
        return extractBytes();
    }
    
    /**
     * Encode wSubscribe request
     * Subscribes to parameter change notifications
     * @param token 32-bit token identifying the parameter to subscribe
     * @param interval subscription interval in ms
     * @return encoded request packet
     */
    public byte[] encodeSubscribe(int token, int interval) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(8);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(token);
        payload.putInt(interval);
        writeMessage(CHANNEL_CONTROL, CMD_SUBSCRIBE, payload.array());
        return extractBytes();
    }
    
    /**
     * Encode wRenew request
     * Extends subscription lifetime
     * @param token 32-bit token (upper 16 bits = subscription id)
     * @return encoded request packet
     */
    public byte[] encodeRenew(int token) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(token);
        writeMessage(CHANNEL_CONTROL, CMD_RENEW, payload.array());
        return extractBytes();
    }
    
    /**
     * Encode request with multiple tokens (batch read)
     * @param tokens array of tokens to read
     * @return encoded request packet
     */
    public byte[] encodeBatchGet(int[] tokens) {
        buffer.clear();
        ByteBuffer payload = ByteBuffer.allocate(tokens.length * 4);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        for (int token : tokens) {
            payload.putInt(token);
        }
        writeMessage(CHANNEL_CONTROL, CMD_GET, payload.array());
        return extractBytes();
    }
    
    /**
     * Write a complete message: ESCAPE + channel + length + command + payload + ESCAPE
     */
    private void writeMessage(byte channel, byte command, byte[] payload) {
        buffer.put(ESCAPE);
        buffer.put(channel);
        // Length: command (1) + payload
        short len = (short) (1 + payload.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(len);
        buffer.put(command);
        if (payload.length > 0) {
            buffer.put(payload);
        }
        buffer.put(ESCAPE);
    }
    
    private byte[] extractBytes() {
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }
    
    /**
     * Escape bytes in payload (for data that might contain 0xdf)
     * Not typically needed for token/value data but available for completeness
     */
    public static byte[] escapeData(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte b : data) {
            if (b == ESCAPE) {
                out.write(ESCAPE); // Double escape for 0xdf
            } else {
                out.write(b);
            }
        }
        return out.toByteArray();
    }
}