package ru.ifmo.chat;

/**
 * Describes an abstract Runnable process working in an infinite loop.
 * Initialized by calling {@link Worker#init()} method.
 * Followed by infinite execution of {@link Worker#loop()} method in an infinite loop until the process is not
 * terminated.
 * Termination followed by calling {@link Worker#stop()} method to properly end up the Thread.
 */
public abstract class Worker implements Runnable {
    @Override
    public void run() {
        try {
            init();
            while (!isInterrupted()) {
                loop();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Initialization procedures to properly start a Worker implementation
     *
     * @throws Exception may throw any kind of exceptions.
     */
    protected abstract void init() throws Exception;

    /**
     * Single loop iteration for a Worker implementation. Describes the main goal of a Worker implementation processed
     * in the infinite loop.
     *
     * @throws Exception may throw any kind of exceptions.
     */
    protected abstract void loop() throws Exception;

    /**
     * Termination procedures to properly stop a Worker implementation, e.g. release resources and so on.
     */
    protected abstract void stop() throws Exception;

    /**
     * Checks if the Thread running this Worker implementation is interrupted.
     * @return true if the Thread running this Worker implementation is already interrupted. Otherwise returns false.
     */
    protected boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }
}
