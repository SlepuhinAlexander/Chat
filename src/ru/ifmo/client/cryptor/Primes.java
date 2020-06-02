package ru.ifmo.client.cryptor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;


/**
 * Utility class to store a sorted array of prime numbers and provide methods to check if the number is prime or to
 * find the closest prime number to a given number.
 * <p>
 * Stores the pre-calculated prime numbers in the Primes.dat file. Creates and initializes this file if it is not
 * found. In case of corrupted data in the file, would work incorrectly.
 * The first load of this class might take considerable time to initialize and fill up the Primes.dat file by
 * calculating the prime numbers.
 */
public class Primes {
    private static final int[] PRIMES;
    private static final Path FILE = Paths.get("resources/Primes.dat");

    static {
        int[] thePrimes = {};
        if (!Files.exists(FILE)) init();
        try {
            thePrimes = Files.lines(FILE).mapToInt(Integer::parseInt).toArray();
        } catch (IOException e) {
            System.out.println("Failed to read from file " + FILE.toAbsolutePath());
            e.printStackTrace();
        }
        PRIMES = thePrimes;
    }

    /**
     * Creates and fills up the Primes.dat file to store the prime numbers list for consequent uses.
     * Is called only if the Primes.dat file does not exist, which is supposed to happen only once on the first load
     * of this class.
     */
    private static void init() {
        if (Files.exists(FILE)) return;
        try {
            Path path = Files.createFile(FILE);
        } catch (IOException e) {
            System.out.println("Failed to create file " + FILE.toAbsolutePath());
            e.printStackTrace();
        }
        try (BufferedWriter writer = Files.newBufferedWriter(FILE)) {
            LinkedList<Integer> primes = new LinkedList<>();
            primes.add(2);
            writer.write(primes.getLast().toString());
            writer.newLine();
            primes.add(3);
            writer.write(primes.getLast().toString());
            writer.newLine();
            int sqrt;
            boolean flag = true;
            outer:
            for (int i = 3; i < 16_777_216; i += 2) {
                if (flag) i += 2;
                flag = !flag;
                sqrt = (int) Math.sqrt(i);
                for (int prime : primes) {
                    if (prime > sqrt) break;
                    if (i % prime == 0) continue outer;
                }
                primes.add(i);
                writer.write(primes.getLast().toString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Failed to write to file " + FILE.toAbsolutePath());
            e.printStackTrace();
        }
    }

    /**
     * Provides a sorted int array of prime numbers stored in this class.
     *
     * @return a copy of the stored int array of prime numbers.
     */
    public static int[] getPrimes() {
        return PRIMES.clone();
    }

    /**
     * Provides a sorted int array of first N prime numbers
     *
     * @param len returned array max length. Returns an empty array if len param is negative or 0.
     * @return a copy of stored int array of prime numbers starting from the first and limited by len param or primes
     * array length.
     */
    public static int[] getPrimes(int len) {
        return len <= 0 ? new int[]{} : Arrays.copyOf(PRIMES, Math.min(len, PRIMES.length));
    }

    /**
     * Gives the length of the stored array of prime numbers.
     *
     * @return the length of the stored array of prime numbers.
     */
    public static int length() {
        return PRIMES.length;
    }

    /**
     * Provides the biggest known and stored prime number
     *
     * @return the last and the biggest prime number known for this class
     */
    public static int getBiggest() {
        return PRIMES[PRIMES.length - 1];
    }

    /**
     * Checks if the given number is prime.
     *
     * @param num an integer number to check. Supposed to be positive.
     * @return true if the stored array of prime numbers contains the given num param. Otherwise returns false.
     * Returns false if the given num param is less than 1. Returns false if the given num param is too big and unknown
     * for this class even if it is actually a prime number.
     */
    public static boolean isPrime(int num) {
        return num > 1 && num <= getBiggest() && Arrays.binarySearch(PRIMES, num) >= 0;
    }

    /**
     * Provides the closest prime number strictly bigger than the provided num param.
     *
     * @param num the number for which the closest bigger prime number is searched. Supposed to be greater than 1 and
     *            not too big.
     * @return the closest prime number known for this class that is strictly bigger than the provided num param.
     * @throws IllegalArgumentException if the provided num param is less or equal 1, or is too big to find the upper
     *                                  prime for it.
     */
    public static int upperPrime(int num) {
        if (num <= 1) throw new IllegalArgumentException("given number " + num + " must be greater than 1");
        if (num > PRIMES[PRIMES.length - 2]) throw new IllegalArgumentException("given number " + num + " is too big");
        int pos = Arrays.binarySearch(PRIMES, num);
        return pos >= 0 ? PRIMES[pos + 1] : PRIMES[-pos - 1];
    }

    /**
     * Provides the closest prime number strictly smaller than the provided num param.
     *
     * @param num the number for which the closest smaller prime number is searched. Supposed to be greater than 2 and
     *            not too big.
     * @return the closest prime number known for this class that is strictly smaller than the provided num param.
     * @throws IllegalArgumentException if the provided num param is less or equal 2, or is too big to find the lower
     *                                  prime for it.
     */
    public static int lowerPrime(int num) {
        if (num <= 2) throw new IllegalArgumentException("given number " + num + " must be greater than 2");
        if (num > getBiggest()) throw new IllegalArgumentException("given number " + num + " is too big");
        int pos = Arrays.binarySearch(PRIMES, num);
        return pos >= 0 ? PRIMES[pos - 1] : PRIMES[-pos - 2];
    }
}
