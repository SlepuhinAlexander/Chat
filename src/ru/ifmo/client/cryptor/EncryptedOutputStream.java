package ru.ifmo.client.cryptor;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Encryption wrapper for any kind of OutputStreams.
 * Supposed to use in pair with {@link EncryptedInputStream}.
 * Both use the same keygen algorithm to generate a key for basic XOR encryption.
 */
public class EncryptedOutputStream extends FilterOutputStream {
    private final byte[] key = new byte[256];
    private int pos = -1;

    public EncryptedOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Encrypts one byte of outgoing data.
     * Expects dta in range [0, 255].
     *
     * @param data is a single byte of outgoing dta in the output steam. Expected to be in [0,255]
     * @return a single byte of encrypted data using the same encryption key that would be used for further decryption
     * in {@link EncryptedInputStream}.
     * @throws IllegalArgumentException in case the input param data has an invalid value
     */
    private int encrypt(int data) {
        if (data < 0 || data > 255) throw new IllegalArgumentException("data value '" + data + "' must be in [0,255]");
        int encrypted = data ^ key[pos];
        pos = (pos + 1) % key.length;
        return encrypted;
    }

    /**
     * Generates the key byte sequence for further encoding the output stream.
     * Is expected to use the first outgoing byte of data as the seed for keygen.
     *
     * @param seed byte value to initialize the keygen algorithm. Expected to be in [0,255].
     * @throws IllegalArgumentException in case the seed param has an invalid value.
     */
    private void keygen(int seed) {
        if (seed < 0 || seed > 255) throw new IllegalArgumentException("seed value '" + seed + "' must be in [0,255]");
        int notSeed = ~seed << 24 >>> 24;
        seed = seed * notSeed + seed * seed;
        if (seed <= 2) seed = 3;
        seed = seed % Primes.getBiggest();
        int lower = Primes.lowerPrime(seed);
        int notLower = ~lower << 8 >>> 8;
        int upper = Primes.upperPrime(seed);
        int notUpper = ~upper << 8 >>> 8;
        StringBuilder builder = new StringBuilder();
        long keygen = ((long) lower * notLower * upper * notUpper) << 1 >>> 1;
        do {
            builder.append(Long.toString(keygen, 32));
            keygen >>= 1;
        } while (keygen > 0);
        System.arraycopy(builder.toString().getBytes(), 0, key, 0, 256);
        pos = 0;
    }

    @Override
    public void write(int b) throws IOException {
        if (pos < 0) {
            keygen(b);
            super.write(b);
            pos++;
        }
        super.write(encrypt(b));
    }
}
