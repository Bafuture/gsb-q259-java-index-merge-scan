package com.example.gsb.indexmerge.index;

import com.example.gsb.indexmerge.CandidateSource;
import com.example.gsb.indexmerge.DocIdIterator;

import java.util.BitSet;

/**
 * 位图索引：用 {@link BitSet} 表示某类标签/布尔条件的命中文档集合。
 */
public final class BitmapIndex {

    private final String name;
    private final BitSet bits = new BitSet();
    private int cardinality;

    public BitmapIndex(String name) {
        this.name = name;
    }

    /** 标记命中文档。 */
    public void mark(int docId) {
        if (!bits.get(docId)) {
            cardinality++;
        }
        bits.set(docId);
    }

    /** 返回该位图全部命中文档的候选集（文档号升序）。 */
    public CandidateSource scan() {
        return new CandidateSource() {
            @Override
            public String name() {
                return name + "[bitmap]";
            }

            @Override
            public int size() {
                return cardinality;
            }

            @Override
            public DocIdIterator iterator() {
                return new BitmapDocIdIterator((BitSet) bits.clone());
            }
        };
    }

    private static final class BitmapDocIdIterator implements DocIdIterator {

        private final BitSet bits;
        private int current = -1;
        private boolean exhausted;

        private BitmapDocIdIterator(BitSet bits) {
            this.bits = bits;
        }

        @Override
        public int next() {
            if (exhausted) {
                return NO_MORE_DOCS;
            }
            int nextBit = bits.nextSetBit(current + 1);
            current = Math.max(nextBit, current);
            if (nextBit == NO_MORE_DOCS) {
                exhausted = true;
            }
            return nextBit;
        }

        @Override
        public int advance(int target) {
            if (exhausted) {
                return NO_MORE_DOCS;
            }
            int nextBit = bits.nextSetBit(Math.max(target, current + 1));
            if (nextBit == NO_MORE_DOCS) {
                exhausted = true;
            } else {
                current = nextBit;
            }
            return nextBit;
        }
    }
}
