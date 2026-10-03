package com.example.gsb.indexmerge;

import com.example.gsb.indexmerge.index.BitmapIndex;
import com.example.gsb.indexmerge.index.HashEqualityIndex;
import com.example.gsb.indexmerge.index.SortedRangeIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexSourcesTest {

    private final IndexMergeScanner scanner = new IndexMergeScanner();

    @Test
    void hashEqualityIndexReturnsOrderedCandidates() {
        HashEqualityIndex<String> index = new HashEqualityIndex<>("city");
        index.add("beijing", 7);
        index.add("beijing", 2);
        index.add("beijing", 2);
        index.add("shanghai", 5);

        IndexCandidates candidates = index.lookup("beijing");

        assertThat(candidates.size()).isEqualTo(2);
        assertThat(drain(candidates.cursor())).containsExactly(2, 7);
        assertThat(index.lookup("missing").size()).isZero();
    }

    @Test
    void sortedRangeIndexReturnsOrderedCandidatesForInclusiveRange() {
        SortedRangeIndex<Integer> index = new SortedRangeIndex<>("price");
        index.add(10, 1);
        index.add(20, 2);
        index.add(20, 3);
        index.add(30, 4);
        index.add(99, 5);

        IndexCandidates candidates = index.range(15, 30);

        assertThat(drain(candidates.cursor())).containsExactly(2, 3, 4);
        assertThat(drain(index.from(30).cursor())).containsExactly(4, 5);
    }

    @Test
    void bitmapIndexEnumeratesSetBitsAscending() {
        BitmapIndex<String> index = new BitmapIndex<>("tag");
        index.add("sale", 9);
        index.add("sale", 1);
        index.add("sale", 4);
        index.add("new", 2);

        IndexCandidates candidates = index.lookup("sale");

        assertThat(candidates.size()).isEqualTo(3);
        assertThat(drain(candidates.cursor())).containsExactly(1, 4, 9);
        assertThat(index.lookup("absent").size()).isZero();
    }

    @Test
    void mixedIndexSourcesIntersectThroughTheSameCursorInterface() {
        HashEqualityIndex<String> city = new HashEqualityIndex<>("city");
        SortedRangeIndex<Integer> price = new SortedRangeIndex<>("price");
        BitmapIndex<String> tag = new BitmapIndex<>("tag");

        // docs 1..8: city=beijing -> {1,2,3,4,5}, price in [10,30] -> {2,3,4,6}, tag=sale -> {3,4,7}
        for (int doc : new int[]{1, 2, 3, 4, 5}) {
            city.add("beijing", doc);
        }
        price.add(10, 2);
        price.add(20, 3);
        price.add(30, 4);
        price.add(25, 6);
        for (int doc : new int[]{3, 4, 7}) {
            tag.add("sale", doc);
        }

        MergeResult result = scanner.intersect(List.of(
                city.lookup("beijing"),
                price.range(10, 30),
                tag.lookup("sale")));

        assertThat(result.docIds()).containsExactly(3, 4);
        assertThat(result.stats().indexCandidateCounts())
                .containsEntry("city", 5)
                .containsEntry("price", 4)
                .containsEntry("tag", 3);

        MergeResult union = scanner.union(List.of(
                city.lookup("beijing"),
                price.range(10, 30),
                tag.lookup("sale")));
        assertThat(union.docIds()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    private static List<Integer> drain(CandidateCursor cursor) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        while (cursor.hasNext()) {
            out.add(cursor.next());
        }
        return out;
    }
}
