package com.mixer.wing.lib.data.osc;

/**
 * Immutable OSC message holding an address and typed arguments.
 * Supports both big-endian and little-endian meter blobs.
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
        int pos = 0;
        while (pos < length && data[pos] != 0) pos++;
        int addrEnd = pos;

        pos = (pos + 4) & 0x7FFFFFFC;
        int alignedPos = pos;

        int commaIndex = -1;
        for (int i = alignedPos; i < length; i++) {
            if (data[i] == 0x2C) {
                commaIndex = i;
                break;
            }
        }
        if (commaIndex < 0) return new Object[0];

        int typeTag = data[commaIndex + 1] & 0xFF;
        pos = (commaIndex + 4) & 0x7FFFFFFC;

        java.util.ArrayList<Object> list = new java.util.ArrayList<>();
        switch (typeTag) {
            case 'i': {
                if (pos + 4 <= length) {
                    int v = ((data[pos] & 0xFF) << 24) |
                            ((data[pos + 1] & 0xFF) << 16) |
                            ((data[pos + 2] & 0xFF) << 8) |
                            (data[pos + 3] & 0xFF);
                    list.add(v);
                }
                break;
            }
            case 'f': {
                if (pos + 4 <= length) {
                    int b0 = data[pos] & 0xFF;
                    int b1 = data[pos + 1] & 0xFF;
                    int b2 = data[pos + 2] & 0xFF;
                    int b3 = data[pos + 3] & 0xFF;
                    int bits = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
                    list.add(Float.intBitsToFloat(bits));
                }
                break;
            }
            case 'b': {
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
            case 's': {
                if (pos < length) {
                    int end2 = pos;
                    while (end2 < length && data[end2] != 0) end2++;
                    list.add(new String(data, pos, Math.min(end2 - pos, length - pos), java.nio.charset.StandardCharsets.UTF_8));
                }
                break;
            }
            default:
                break;
        }
        return list.toArray();
    }
}