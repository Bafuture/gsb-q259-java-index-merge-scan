package com.example.gsb.indexmerge.index;

import com.example.gsb.indexmerge.CandidateCursor;
import com.example.gsb.indexmerge.IndexCandidates;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;

/**
 * Bitmap index: each indexed value has a bit set whose nth bit marks document
 * n. A lookup enumerates the set bits in ascending order as a cursor.
 */
public final class BitmapIndex<K> implements IndexSource {

    private final String name;
    private final Map<K, BitSet> bitmaps = new HashMap<>();

    public BitmapIndex(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    /** Marks {@code docId} as containing {@code value}. */
    public void add(K value, int docId) {
        if (docId < 0) {
            throw new IllegalArgumentException("docId must be >= 0");
        }
        bitmaps.computeIfAbsent(value, k -> new BitSet()).set(docId);
    }

    /** Bitmap lookup: candidates are the set bits (ascending document ids). */
    public IndexCandidates lookup(K value) {
        BitSet bits = bitmaps.get(value);
        if (bits == null) {
            return new IndexCandidates(name, CandidateCursor.ofSorted(List.of()), 0);
        }
        int cardinality = bits.cardinality();
        PrimitiveIterator.OfInt setBits = bits.stream().iterator();
        return new IndexCandidates(name, CandidateCursor.ofStream(setBits, cardinality), cardinality);
    }
}
