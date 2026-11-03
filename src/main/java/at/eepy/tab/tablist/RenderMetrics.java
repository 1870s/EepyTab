package at.eepy.tab.tablist;

/**
 * Lightweight counters for /eepytab debug. Only touched on the main thread.
 * Averages are exponential moving averages, so they follow recent load.
 */
public final class RenderMetrics {

    private static final double SMOOTHING = 0.05;

    private double averageRenderNanos;
    private double averageTickNanos;
    private long maxTickNanos;
    private long packetsSent;
    private long packetsSkipped;
    private long renders;

    void recordRender(long nanos) {
        averageRenderNanos = renders++ == 0 ? nanos : averageRenderNanos + (nanos - averageRenderNanos) * SMOOTHING;
    }

    void recordTick(long nanos) {
        averageTickNanos = averageTickNanos == 0 ? nanos : averageTickNanos + (nanos - averageTickNanos) * SMOOTHING;
        maxTickNanos = Math.max(maxTickNanos, nanos);
    }

    void sent() {
        packetsSent++;
    }

    void skipped() {
        packetsSkipped++;
    }

    public double averageRenderMicros() {
        return averageRenderNanos / 1_000.0;
    }

    public double averageTickMillis() {
        return averageTickNanos / 1_000_000.0;
    }

    public double maxTickMillis() {
        return maxTickNanos / 1_000_000.0;
    }

    public long packetsSent() {
        return packetsSent;
    }

    public long packetsSkipped() {
        return packetsSkipped;
    }

    public long renders() {
        return renders;
    }
}
