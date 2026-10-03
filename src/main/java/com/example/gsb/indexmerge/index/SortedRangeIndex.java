package com.example.gsb.indexmerge.index;

import com.example.gsb.indexmerge.CandidateCursor;
import com.example.gsb.indexmerge.IndexCandidates;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Sorted range index: document ids are indexed by a comparable key and a range
 * query ({@code [from, to]}, inclusive) scans the ordered keys and returns the
 * matching document ids as an ordered cursor.
 */
public final class SortedRangeIndex<K extends Comparable<K>> implements IndexSource {

    private final String name;
    private final NavigableMap<K, List<Integer>> byKey = new TreeMap<>();

    public SortedRangeIndex(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    /** Indexes {@code docId} under the sortable {@code key}. */
    public void add(K key, int docId) {
        byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(docId);
    }

    /** Inclusive range scan over keys in {@code [from, to]}. */
    public IndexCandidates range(K from, K to) {
        if (from.compareTo(to) > 0) {
            throw new IllegalArgumentException("from (" + from + ") > to (" + to + ")");
        }
        NavigableMap<K, List<Integer>> slice = byKey.subMap(from, true, to, true);
        List<Integer> docIds = new ArrayList<>();
        for (List<Integer> postings : slice.values()) {
            docIds.addAll(postings);
        }
        docIds.sort(Integer::compareTo);
        List<Integer> distinct = dedupe(docIds);
        return new IndexCandidates(name, CandidateCursor.ofSorted(distinct), distinct.size());
    }

    /** Open-ended range scan over keys {@code >= from}. */
    public IndexCandidates from(K from) {
        if (byKey.isEmpty()) {
            return new IndexCandidates(name, CandidateCursor.ofSorted(List.of()), 0);
        }
        return range(from, byKey.lastKey());
    }

    private static List<Integer> dedupe(List<Integer> sorted) {
        List<Integer> out = new ArrayList<>(sorted.size());
        int last = Integer.MIN_VALUE;
        for (int id : sorted) {
            if (out.isEmpty() || id != last) {
                out.add(id);
                last = id;
            }
        }
        return out;
    }
}
