package com.requestrecorder.core.queue;

/**
 * 采集队列运行统计快照。
 */
public class QueueStats {

    private final long submitted;
    private final long written;
    private final long failed;
    private final long dropped;
    private final long pending;
    private final int capacity;

    public QueueStats(long submitted, long written, long failed, long dropped, long pending, int capacity) {
        this.submitted = submitted;
        this.written = written;
        this.failed = failed;
        this.dropped = dropped;
        this.pending = pending;
        this.capacity = capacity;
    }

    public long getSubmitted() {
        return submitted;
    }

    public long getWritten() {
        return written;
    }

    public long getFailed() {
        return failed;
    }

    public long getDropped() {
        return dropped;
    }

    public long getPending() {
        return pending;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return "QueueStats{submitted=" + submitted + ", written=" + written + ", failed=" + failed
                + ", dropped=" + dropped + ", pending=" + pending + ", capacity=" + capacity + '}';
    }
}
