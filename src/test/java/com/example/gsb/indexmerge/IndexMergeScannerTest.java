package com.example.gsb.indexmerge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexMergeScannerTest {

    private final IndexMergeScanner scanner = new IndexMergeScanner();

    private static IndexCandidates candidates(String name, int... docIds) {
        List<Integer> ids = java.util.Arrays.stream(docIds).boxed().toList();
        return new IndexCandidates(name, CandidateCursor.ofSorted(ids), ids.size());
    }

    @Test
    void unionProducesAscendingDeduplicatedResult() {
        MergeResult result = scanner.union(List.of(
                candidates("idxA", 1, 3, 5, 7),
                candidates("idxB", 2, 3, 6),
                candidates("idxC", 5, 8, 9)));

        assertThat(result.docIds()).containsExactly(1, 2, 3, 5, 6, 7, 8, 9);
        assertThat(result.stats().mode()).isEqualTo(MergeStats.Mode.UNION);
        assertThat(result.stats().indexCandidateCounts())
                .containsEntry("idxA", 4)
                .containsEntry("idxB", 3)
                .containsEntry("idxC", 3);
        assertThat(result.stats().mergeRounds()).isEqualTo(2);
        assertThat(result.stats().candidateCount()).isEqualTo(8);
    }

    @Test
    void intersectProducesAscendingCommonDocuments() {
        MergeResult result = scanner.intersect(List.of(
                candidates("idxA", 1, 2, 3, 5, 8),
                candidates("idxB", 2, 3, 8, 13),
                candidates("idxC", 3, 5, 8)));

        assertThat(result.docIds()).containsExactly(3, 8);
        assertThat(result.stats().mode()).isEqualTo(MergeStats.Mode.INTERSECT);
        assertThat(result.stats().mergeRounds()).isEqualTo(2);
    }

    @Test
    void emptyInputListYieldsEmptyResult() {
        MergeResult union = scanner.union(List.of());
        MergeResult intersect = scanner.intersect(List.of());

        assertThat(union.docIds()).isEmpty();
        assertThat(intersect.docIds()).isEmpty();
        assertThat(union.stats().mergeRounds()).isZero();
        assertThat(union.stats().candidateCount()).isZero();
    }

    @Test
    void singleInputIsReturnedAsIsWithoutMergeRounds() {
        MergeResult result = scanner.union(List.of(candidates("only", 2, 4, 6)));

        assertThat(result.docIds()).containsExactly(2, 4, 6);
        assertThat(result.stats().mergeRounds()).isZero();
        assertThat(result.stats().peakIntermediateSize()).isZero();
        assertThat(result.stats().indexCandidateCounts()).containsEntry("only", 3);
    }

    @Test
    void intersectAdvancesSmallestRemainingSetFirst() {
        // Sizes 100, 50, 2 where the 2-set is contained in the others.
        // Smallest-first: intersect(2, 50) -> <=2, then with 100 -> peak stays <= 2.
        // Left-to-right order would materialize an intermediate of size 50.
        int[] big = new int[100];
        int[] mid = new int[50];
        for (int i = 0; i < 100; i++) {
            big[i] = i;
        }
        for (int i = 0; i < 50; i++) {
            mid[i] = i * 2;
        }
        MergeResult result = scanner.intersect(List.of(
                candidates("big", big),
                candidates("mid", mid),
                candidates("tiny", 10, 20)));

        assertThat(result.docIds()).containsExactly(10, 20);
        assertThat(result.stats().peakIntermediateSize()).isLessThanOrEqualTo(2);
    }

    @Test
    void unionAlsoMergesSmallestFirst() {
        MergeResult result = scanner.union(List.of(
                candidates("a", 1, 2, 3, 4, 5, 6, 7, 8),
                candidates("b", 100),
                candidates("c", 200)));

        // Two rounds: (1+1) then (2+8); peak intermediate is the final result.
        assertThat(result.stats().mergeRounds()).isEqualTo(2);
        assertThat(result.docIds()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 100, 200);
    }

    @Test
    void postFilterDropsNonMatchingCandidatesAndCountsThem() {
        MergeResult merged = scanner.union(List.of(
                candidates("idxA", 1, 2, 3, 4),
                candidates("idxB", 3, 5)));

        MergeResult.FilteredResult filtered = merged.filter(docId -> docId % 2 == 0);

        assertThat(filtered.docIds()).containsExactly(2, 4);
        assertThat(filtered.kept()).isEqualTo(2);
        assertThat(filtered.dropped()).isEqualTo(3);
        assertThat(merged.stats().candidateCount()).isEqualTo(5);
        assertThat(merged.stats().postFilterKept()).isEqualTo(2);
        assertThat(merged.stats().postFilterDropped()).isEqualTo(3);
    }

    @Test
    void mismatchedDeclaredSizeIsRejected() {
        IndexCandidates bad = new IndexCandidates("bad", CandidateCursor.ofSorted(List.of(1, 2)), 5);
        assertThatThrownBy(() -> scanner.union(List.of(bad)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad");
    }

    @Test
    void unorderedOrDuplicateStreamIsRejected() {
        IndexCandidates bad = new IndexCandidates("bad", CandidateCursor.ofSorted(List.of(3, 1)), 2);
        assertThatThrownBy(() -> scanner.intersect(List.of(bad)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
