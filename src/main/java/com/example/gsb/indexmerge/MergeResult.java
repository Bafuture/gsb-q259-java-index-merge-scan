package com.example.gsb.indexmerge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Outcome of a merge scan: the ordered, de-duplicated document ids plus the
 * statistics gathered while producing them. A non-indexed post filter can be
 * applied on top, which updates the statistics with kept/dropped counts.
 */
public final class MergeResult {

    private final List<Integer> docIds;
    private final MergeStats stats;

    MergeResult(List<Integer> docIds, MergeStats stats) {
        this.docIds = List.copyOf(docIds);
        this.stats = stats;
    }

    /** Merged document ids, ascending, without duplicates. */
    public List<Integer> docIds() {
        return docIds;
    }

    public MergeStats stats() {
        return stats;
    }

    /**
     * Applies a non-indexed filter to the merged candidates and returns the
     * surviving document ids. The shared {@link MergeStats} records how many
     * candidates entered the filter and how many were dropped.
     */
    public FilteredResult filter(IntPredicate nonIndexedFilter) {
        List<Integer> kept = new ArrayList<>();
        int dropped = 0;
        for (int docId : docIds) {
            if (nonIndexedFilter.test(docId)) {
                kept.add(docId);
            } else {
                dropped++;
            }
        }
        stats.recordPostFilter(kept.size(), dropped);
        return new FilteredResult(Collections.unmodifiableList(kept), stats);
    }

    /** Result of applying the non-indexed post filter. */
    public record FilteredResult(List<Integer> docIds, MergeStats stats) {

        public FilteredResult {
            docIds = List.copyOf(docIds);
        }

        public int kept() {
            return docIds.size();
        }

        public int dropped() {
            return stats.postFilterDropped();
        }
    }
}
