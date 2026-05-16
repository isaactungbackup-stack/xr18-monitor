package com.mixer.xr18.lib.data.osc;

/**
 * Immutable OSC message holding an address and typed arguments.
 */
public class OSCMessage {
    public final String address;
    public final Object[] args;

    public OSCMessage(String address, Object[] args) {
        this.address = address;
        this.args = args;
    }

    /** Parse from raw UDP datagram bytes. */
    public OSCMessage(byte[] data, int length) {
        this.address = parseAddress(data, length);
        this.args = parseArguments(data, length);
    }

    private static String parseAddress(byte[] data, int length) {
        if (length <= 0) return "";
        int end = 0;
        while (end < length && end < 1024 && data[end] != 0) {
            end++;
        }
        int safeEnd = Math.min(end, length);
        return new String(data, 0, safeEnd, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Object[] parseArguments(byte[] data, int length) {
        // Skip address
        int pos = 0;
        while (pos < length && data[pos] != 0) pos++;
        int addrEnd = pos;

        // Align to 4-byte boundary after address
        pos = (pos + 4) & 0x7FFFFFFC;
        int alignedPos = pos;

        // Find the type tag ','
        int commaIndex = -1;
        for (int i = alignedPos; i < length; i++) {
            if (data[i] == 0x2C) {
                commaIndex = i;
                break;
            }
        }
        if (commaIndex < 0) return new Object[0];

        // Type tag is at commaIndex+1, float data at (commaIndex + 4) & 0x7FFFFFFC
        int typeTag = data[commaIndex + 1] & 0xFF;
        pos = (commaIndex + 4) & 0x7FFFFFFC;

        java.util.ArrayList<Object> list = new java.util.ArrayList<>();
        // Single argument per message for XR18
        switch (typeTag) {
            case 'i': {
                int v = ((data[pos] & 0xFF) << 24) |
                        ((data[pos + 1] & 0xFF) << 16) |
                        ((data[pos + 2] & 0xFF) << 8) |
                        (data[pos + 3] & 0xFF);
                list.add(v);
                break;
            }
            case 'f': {
                // Float stored as big-endian IEEE 754
                int b0 = data[pos] & 0xFF;
                int b1 = data[pos + 1] & 0xFF;
                int b2 = data[pos + 2] & 0xFF;
                int b3 = data[pos + 3] & 0xFF;
                int bits = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
                list.add(Float.intBitsToFloat(bits));
                break;
            }
            case 'b': {
                // Blob: 4-byte big-endian size, then raw bytes
                if (pos + 4 <= length) {
                    int blobSize = ((data[pos] & 0xFF) << 24) |
                                   ((data[pos + 1] & 0xFF) << 16) |
                                   ((data[pos + 2] & 0xFF) << 8) |
                                   (data[pos + 3] & 0xFF);
                    byte[] blob = new byte[blobSize];
                    System.arraycopy(data, pos + 4, blob, 0, Math.min(blobSize, length - pos - 4));
                    list.add(blob);
                }
                break;
            }
            case 'T':
                list.add(Boolean.TRUE);
                break;
            case 'F':
                list.add(Boolean.FALSE);
                break;
            default:
                break;
        }
        return list.toArray();
    }
}