package com.example.gsb.indexmerge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statistics collected while merging candidate sets and applying the
 * non-indexed post filter.
 */
public final class MergeStats {

    public enum Mode {
        UNION,
        INTERSECT
    }

    private final Mode mode;
    private final Map<String, Integer> indexCandidateCounts = new LinkedHashMap<>();
    private int mergeRounds;
    private int peakIntermediateSize;
    private int candidateCount = -1;
    private int postFilterDropped = -1;
    private int postFilterKept = -1;

    MergeStats(Mode mode) {
        this.mode = mode;
    }

    void recordIndexCandidates(String indexName, int count) {
        indexCandidateCounts.merge(indexName, count, Integer::sum);
    }

    void recordMergeRound(int mergedSize) {
        mergeRounds++;
        peakIntermediateSize = Math.max(peakIntermediateSize, mergedSize);
    }

    void recordCandidateCount(int count) {
        this.candidateCount = count;
    }

    void recordPostFilter(int kept, int dropped) {
        this.postFilterKept = kept;
        this.postFilterDropped = dropped;
    }

    public Mode mode() {
        return mode;
    }

    /** Candidate count contributed by each index, in the order the indexes were merged. */
    public Map<String, Integer> indexCandidateCounts() {
        return Collections.unmodifiableMap(indexCandidateCounts);
    }

    /** Number of pairwise merge rounds executed (smallest-remaining-first). */
    public int mergeRounds() {
        return mergeRounds;
    }

    /** Largest intermediate (or final) candidate list materialized during the merge. */
    public int peakIntermediateSize() {
        return peakIntermediateSize;
    }

    /** Number of merged candidates before the non-indexed post filter ran. */
    public int candidateCount() {
        return candidateCount;
    }

    /** Number of candidates dropped by the non-indexed post filter; -1 if it never ran. */
    public int postFilterDropped() {
        return postFilterDropped;
    }

    /** Number of candidates kept by the non-indexed post filter; -1 if it never ran. */
    public int postFilterKept() {
        return postFilterKept;
    }

    @Override
    public String toString() {
        return "MergeStats{mode=" + mode
                + ", indexCandidateCounts=" + indexCandidateCounts
                + ", mergeRounds=" + mergeRounds
                + ", peakIntermediateSize=" + peakIntermediateSize
                + ", candidateCount=" + candidateCount
                + ", postFilterKept=" + postFilterKept
                + ", postFilterDropped=" + postFilterDropped
                + '}';
    }
}
