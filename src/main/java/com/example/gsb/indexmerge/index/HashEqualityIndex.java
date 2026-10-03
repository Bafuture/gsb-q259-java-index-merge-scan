package com.example.gsb.indexmerge.index;

import com.example.gsb.indexmerge.CandidateCursor;
import com.example.gsb.indexmerge.IndexCandidates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory hash equality index: maps an indexed value to the document ids
 * that contain it. A lookup returns the posting list as an ordered cursor.
 */
public final class HashEqualityIndex<K> implements IndexSource {

    private final String name;
    private final Map<K, List<Integer>> postings = new HashMap<>();

    public HashEqualityIndex(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    /** Adds a document id to the posting list of {@code value}. */
    public void add(K value, int docId) {
        postings.computeIfAbsent(value, k -> new ArrayList<>()).add(docId);
    }

    /** Equality lookup: ordered candidates for the documents containing {@code value}. */
    public IndexCandidates lookup(K value) {
        List<Integer> docIds = postings.getOrDefault(value, List.of()).stream()
                .sorted()
                .distinct()
                .toList();
        return new IndexCandidates(name, CandidateCursor.ofSorted(docIds), docIds.size());
    }
}
