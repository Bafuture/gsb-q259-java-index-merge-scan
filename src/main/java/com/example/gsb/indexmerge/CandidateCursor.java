package com.example.gsb.indexmerge;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * Unified traversal interface over an ordered (document id ascending) candidate set.
 * Every index source (hash equality, sorted range, bitmap, ...) exposes the same
 * cursor so the merge scanner does not care where candidates come from.
 */
public interface CandidateCursor {

    boolean hasNext();

    /** Returns the next document id; throws {@link NoSuchElementException} when exhausted. */
    int next();

    /**
     * Number of candidate document ids that have not yet been consumed.
     * Must be exact: the merge planner uses it to always advance the smallest
     * remaining set first and thereby keep intermediate results small.
     */
    int remaining();

    /** Cursor over a defensively copied ascending list of document ids. */
    static CandidateCursor ofSorted(List<Integer> sortedDocIds) {
        int[] ids = sortedDocIds.stream().mapToInt(Integer::intValue).toArray();
        return new ArrayCursor(ids);
    }

    /** Cursor over a primitive ascending stream with an exact known length. */
    static CandidateCursor ofStream(PrimitiveIterator.OfInt ascendingIterator, int length) {
        int[] ids = new int[length];
        for (int i = 0; i < length; i++) {
            ids[i] = ascendingIterator.nextInt();
        }
        return new ArrayCursor(ids);
    }

    final class ArrayCursor implements CandidateCursor {
        private final int[] ids;
        private int pos;

        ArrayCursor(int[] ids) {
            this.ids = ids;
        }

        @Override
        public boolean hasNext() {
            return pos < ids.length;
        }

        @Override
        public int next() {
            if (pos >= ids.length) {
                throw new NoSuchElementException();
            }
            return ids[pos++];
        }

        @Override
        public int remaining() {
            return ids.length - pos;
        }
    }
}
