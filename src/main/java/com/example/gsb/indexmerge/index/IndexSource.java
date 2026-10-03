package com.example.gsb.indexmerge.index;

/**
 * Marker interface implemented by every index source. Indexes differ in how
 * they answer a condition (hash equality, sorted range, bitmap lookup), but
 * every query on them produces an {@code IndexCandidates} carrying the common
 * ordered {@code CandidateCursor} traversal interface.
 */
public interface IndexSource {

    /** Human-readable index name used in merge statistics. */
    String name();
}
