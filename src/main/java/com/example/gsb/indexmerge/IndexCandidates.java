package com.example.gsb.indexmerge;

import java.util.Objects;

/**
 * A named candidate stream pulled from one index for one query condition.
 * {@code size} is the exact candidate count and must equal the number of
 * document ids the cursor will produce.
 */
public record IndexCandidates(String indexName, CandidateCursor cursor, int size) {

    public IndexCandidates {
        Objects.requireNonNull(indexName, "indexName");
        Objects.requireNonNull(cursor, "cursor");
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
    }
}
