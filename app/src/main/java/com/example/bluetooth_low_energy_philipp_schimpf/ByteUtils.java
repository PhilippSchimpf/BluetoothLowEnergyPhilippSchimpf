package com.example.bluetooth_low_energy_philipp_schimpf;

public class ByteUtils {
    public static byte[] reverse(byte[] value) {
        int length = value.length;
        byte[] reversed = new byte[length];
        for (int i = 0; i < length; i++) {
            reversed[i] = value[length - (i + 1)];
        }
        return reversed;
    }
}
