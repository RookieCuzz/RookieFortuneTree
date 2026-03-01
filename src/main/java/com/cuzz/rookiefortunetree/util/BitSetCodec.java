package com.cuzz.rookiefortunetree.util;

import java.util.Base64;
import java.util.BitSet;

public final class BitSetCodec {
    private BitSetCodec() {
    }

    public static String toBase64(BitSet bits) {
        if (bits == null || bits.isEmpty()) {
            return "";
        }
        byte[] bytes = bits.toByteArray();
        if (bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static BitSet fromBase64(String value) {
        if (value == null || value.isBlank()) {
            return new BitSet();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value.trim());
            if (bytes.length == 0) {
                return new BitSet();
            }
            return BitSet.valueOf(bytes);
        } catch (IllegalArgumentException ignored) {
            return new BitSet();
        }
    }
}

