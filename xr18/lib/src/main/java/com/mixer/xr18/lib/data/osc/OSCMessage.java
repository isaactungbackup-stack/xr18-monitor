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
        int end = 0;
        while (end < length && data[end] != 0) {
            end++;
        }
        return new String(data, 0, end, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Object[] parseArguments(byte[] data, int length) {
        // Skip address
        int pos = 0;
        while (pos < length && data[pos] != 0) pos++;

        // Align to 4-byte boundary after address
        pos = (pos + 4) & 0x7FFFFFFC;

        // Find the type tag ','
        int commaIndex = -1;
        for (int i = pos; i < length; i++) {
            if (data[i] == 0x2C) {
                commaIndex = i;
                break;
            }
        }
        if (commaIndex < 0) return new Object[0];

        // Align to first argument (4-byte aligned after ',')
        pos = commaIndex + 1;  // type tag is right after comma, not 4 bytes later

        java.util.ArrayList<Object> list = new java.util.ArrayList<>();
        while (pos + 4 <= length) {
            int typeTag = data[pos] & 0xFF;
            if (typeTag == 0) break; // stop on zero padding

            switch (typeTag) {
                case 'i': {
                    int v = ((data[pos + 1] & 0xFF) << 24) |
                            ((data[pos + 2] & 0xFF) << 16) |
                            ((data[pos + 3] & 0xFF) << 8) |
                            (data[pos + 4] & 0xFF);
                    list.add(v);
                    break;
                }
                case 'f': {
                    // Float stored as little-endian IEEE 754
                    int b0 = data[pos + 1] & 0xFF;
                    int b1 = data[pos + 2] & 0xFF;
                    int b2 = data[pos + 3] & 0xFF;
                    int b3 = data[pos + 4] & 0xFF;
                    int bits = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
                    list.add(Float.intBitsToFloat(bits));
                    break;
                }
                case 'T':
                    list.add(Boolean.TRUE);
                    break;
                case 'F':
                    list.add(Boolean.FALSE);
                    break;
                default:
                    pos += 4; // advance but don't add
                    continue;
            }
            pos = (pos + 4) & 0x7FFFFFFC;
        }
        return list.toArray();
    }
}