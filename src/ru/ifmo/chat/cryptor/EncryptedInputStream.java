package ru.ifmo.chat.cryptor;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Decryption wrapper for any kind of InputStreams.
 * Supposed to use in pair with {@link EncryptedOutputStream}.
 * Both use the same keygen algorithm to generate a key for basic XOR encryption.
 */
public class EncryptedInputStream extends FilterInputStream {
    private final byte[] key = new byte[256];
    private int pos = -1;

    public EncryptedInputStream(InputStream in) {
        super(in);
    }

    /**
     * Decrypts one byte of incoming data.
     * Expects data in range [0,255] or -1 meaning the end of input data stream.
     *
     * @param data is a single byte of incoming data in the input stream. Expected to be in [0,255] or -1 meaning the
     *             end of input stream.
     * @return a single byte value of decrypted data using the same encryption key that was used while encoding, or -1
     * meaning the end of input stream.
     * @throws IllegalArgumentException in case the input param data has an invalid value.
     */
    private int decrypt(int data) {
        if (data < -1 || data > 255) {
            throw new IllegalArgumentException("data value '" + data + "' must be in [-1,255]");
        }
        if (data == -1) return data;
        int decrypted = data ^ key[pos];
        pos = (pos + 1) % key.length;
        return decrypted;
    }

    /**
     * Generates the key byte sequence for further decoding the input stream.
     * Is expected to use the first incoming byte of data as the seed for keygen.
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
    public int read() throws IOException {
        int data = super.read();
        if (pos < 0) {
            keygen(data);
            pos++;
            return read();
        }
        return decrypt(data);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) return 0;
        int c = read();
        if (c == -1) return -1;
        b[off] = (byte) c;
        int i = 1;
        try {
            for (; i < len; i++) {
                c = read();
                if (c == -1) break;
                b[off + i] = (byte) c;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return i;
    }
}
