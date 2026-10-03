package com.example.gsb.indexmerge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexMergeScannerTest {

    private final IndexMergeScanner scanner = new IndexMergeScanner();

    @Test
    void unionMergesMultipleSourcesInAscendingOrder() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 3, 7),
                Sources.of("b", 2, 3, 8),
                Sources.of("c", 0, 7, 9));

        MergeResult result = scanner.scan(sources, MergeMode.UNION, null);

        assertThat(result.docIds()).containsExactly(0, 1, 2, 3, 7, 8, 9);
    }

    @Test
    void unionDeduplicatesDocumentsAppearingInMultipleSources() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 5, 5, 10, 20),
                Sources.of("b", 10, 20, 30),
                Sources.of("c", 5, 20, 40));

        MergeResult result = scanner.scan(sources, MergeMode.UNION, null);

        assertThat(result.docIds()).containsExactly(5, 10, 20, 30, 40);
        assertThat(result.docIds()).doesNotHaveDuplicates();
    }

    @Test
    void intersectionKeepsOnlyDocumentsHitByEverySource() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 2, 3, 4, 5),
                Sources.of("b", 2, 4, 6, 8),
                Sources.of("c", 0, 2, 4, 7));

        MergeResult result = scanner.scan(sources, MergeMode.INTERSECTION, null);

        assertThat(result.docIds()).containsExactly(2, 4);
    }

    @Test
    void intersectionIsEmptyAsSoonAsAnySourceIsEmpty() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 2, 3),
                Sources.of("empty"),
                Sources.of("c", 2, 3));

        MergeResult result = scanner.scan(sources, MergeMode.INTERSECTION, null);

        assertThat(result.docIds()).isEmpty();
        assertThat(result.stats().mergeRounds()).isZero();
    }

    @Test
    void emptyInputProducesEmptyResult() {
        MergeResult union = scanner.scan(List.of(), MergeMode.UNION, null);
        MergeResult intersection = scanner.scan(List.of(), MergeMode.INTERSECTION, null);

        assertThat(union.docIds()).isEmpty();
        assertThat(intersection.docIds()).isEmpty();
    }

    @Test
    void intersectionAlwaysMergesSmallestRemainingSetsFirst() {
        int[] tiny = {1, 2, 3};
        int[] medium = {2, 4, 6, 8, 10};
        int[] huge = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        List<CandidateSource> sources = List.of(
                Sources.of("huge", huge),
                Sources.of("medium", medium),
                Sources.of("tiny", tiny));

        MergeResult result = scanner.scan(sources, MergeMode.INTERSECTION, null);

        assertThat(result.docIds()).containsExactly(2);
        // 三个集合需要两轮两两归并
        assertThat(result.stats().mergeRounds()).isEqualTo(2);
        // 先合并最小的两个（3 与 5 -> 1），任何中间结果都不超过最小集合
        assertThat(result.stats().peakIntermediateSize()).isLessThanOrEqualTo(tiny.length);
    }

    @Test
    void postFilterCountsKeptAndDroppedDocuments() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 2, 3, 4, 5, 6));

        MergeResult result = scanner.scan(sources, MergeMode.UNION, docId -> docId % 2 == 0);

        assertThat(result.docIds()).containsExactly(2, 4, 6);
        assertThat(result.stats().preFilterCount()).isEqualTo(6);
        assertThat(result.stats().postFilterCount()).isEqualTo(3);
        assertThat(result.stats().filteredOutCount()).isEqualTo(3);
    }

    @Test
    void postFilterDroppingEverythingLeavesEmptyResult() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 3, 5),
                Sources.of("b", 1, 3, 7));

        MergeResult result = scanner.scan(sources, MergeMode.INTERSECTION, docId -> docId > 100);

        assertThat(result.docIds()).isEmpty();
        assertThat(result.stats().preFilterCount()).isEqualTo(2);
        assertThat(result.stats().postFilterCount()).isZero();
        assertThat(result.stats().filteredOutCount()).isEqualTo(2);
    }

    @Test
    void statsReportPerIndexCandidateCounts() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 2),
                Sources.of("b", 3, 4, 5),
                Sources.of("c", 6));

        MergeResult result = scanner.scan(sources, MergeMode.UNION, null);

        assertThat(result.stats().indexCandidateCounts())
                .containsEntry("a", 2)
                .containsEntry("b", 3)
                .containsEntry("c", 1);
    }

    @Test
    void unionPeakIntermediateSizeIsBoundedByActiveSourceCount() {
        List<CandidateSource> sources = List.of(
                Sources.of("a", 1, 100, 200),
                Sources.of("b", 2, 101, 201),
                Sources.of("c", 3, 102, 202),
                Sources.of("d", 4, 103, 203));

        MergeResult result = scanner.scan(sources, MergeMode.UNION, null);

        assertThat(result.docIds()).hasSize(12);
        assertThat(result.stats().peakIntermediateSize()).isEqualTo(sources.size());
    }
}
