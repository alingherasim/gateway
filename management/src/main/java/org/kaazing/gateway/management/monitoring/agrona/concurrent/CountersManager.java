package org.kaazing.gateway.management.monitoring.agrona.concurrent;

import java.nio.ByteOrder;
import java.util.Deque;
import java.util.LinkedList;

import org.kaazing.gateway.management.monitoring.agrona.BitUtil;

public class CountersManager {

    public static final int METADATA_LENGTH = BitUtil.CACHE_LINE_LENGTH * 4;
    public static final int LABEL_LENGTH = BitUtil.CACHE_LINE_LENGTH * 2;
    public static final int COUNTER_LENGTH = BitUtil.CACHE_LINE_LENGTH * 2;
    public static final int UNREGISTERED_LABEL_LENGTH = -1;

    private final AtomicBuffer labelsBuffer;
    private final AtomicBuffer countersBuffer;
    private final AtomicBuffer metadataBuffer;
    private final Deque<Integer> freeList = new LinkedList<>();
    
    private int idHighWaterMark = -1;

    public CountersManager(AtomicBuffer labelsBuffer, AtomicBuffer countersBuffer, AtomicBuffer metadataBuffer) {
        this.metadataBuffer = metadataBuffer;
        this.labelsBuffer = labelsBuffer;
        this.countersBuffer = countersBuffer;

        countersBuffer.verifyAlignment();
    }

    /**
     * Create a new counter with the underlying buffers
     * @param label - the label of the new counter
     * @param metadata - the metadata associated with the counter
     * @return the new counter
     */
    public AtomicCounter newCounter(final String label, final String metadata) {
        return new AtomicCounter(countersBuffer, allocateNewCounter(label, metadata), this);
    }

    /**
     * Free the counter identified by counterId.
     *
     * @param counterId the counter to freed
     */
    public void free(final int counterId) {
        labelsBuffer.putInt(labelOffset(counterId), UNREGISTERED_LABEL_LENGTH);
        metadataBuffer.putInt(metadataOffset(counterId), UNREGISTERED_LABEL_LENGTH);
        freeList.push(counterId);
    }

    /**
     * The offset in the counter buffer for a given id.
     *
     * @param id for which the offset should be provided.
     * @return the offset in the counter buffer.
     */
    public static int counterOffset(int id) {
        return id * COUNTER_LENGTH;
    }

    /**
     * Set an {@link AtomicCounter} value based on counterId.
     *
     * @param counterId to be set.
     * @param value to set for the counter.
     */
    public void setCounterValue(final int counterId, final long value) {
        countersBuffer.putLongOrdered(counterOffset(counterId), value);
    }

    private int allocateNewCounter(final String label, final String metadata) {
        int counterId = getValidCounterId();
        putLabel(counterId, label);
        putMetadata(counterId, metadata);

        return counterId;
    }

    private int getValidCounterId() {
        final int counterId = getNextCounterId();

        if ((counterOffset(counterId) + COUNTER_LENGTH) > countersBuffer.capacity()) {
            throw new IllegalArgumentException("Unable to allocate counter, counter buffer is full");
        }

        return counterId;
    }

    private int getNextCounterId() {
        if (freeList.isEmpty()) {
            return ++idHighWaterMark;
        }

        final int counterId = freeList.pop();
        countersBuffer.putLongOrdered(counterOffset(counterId), 0L);

        return counterId;
    }

    private void putMetadata(int counterId, String metadata) {
        final int metadataOffset = metadataOffset(counterId);

        if ((metadataOffset + METADATA_LENGTH) > metadataBuffer.capacity()) {
            throw new IllegalArgumentException("Unable to allocate counter, metadata buffer is full");
        }

        metadataBuffer.putStringUtf8(metadataOffset, metadata, ByteOrder.nativeOrder(), METADATA_LENGTH - BitUtil.SIZE_OF_INT);
    }
    
    private void putLabel(int counterId, String label) {
        final int labelsOffset = labelOffset(counterId);

        if ((labelsOffset + LABEL_LENGTH) > labelsBuffer.capacity()) {
            throw new IllegalArgumentException("Unable to allocate counter, labels buffer is full");
        }

        labelsBuffer.putStringUtf8(labelsOffset, label, ByteOrder.nativeOrder(), LABEL_LENGTH - BitUtil.SIZE_OF_INT);
    }

    private int labelOffset(final int counterId) {
        return counterId * LABEL_LENGTH;
    }

    private int metadataOffset(final int counterId) {
        return counterId * METADATA_LENGTH;
    }

}
