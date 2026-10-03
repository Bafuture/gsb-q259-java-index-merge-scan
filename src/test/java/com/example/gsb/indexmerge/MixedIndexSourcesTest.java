package com.example.gsb.indexmerge;

import com.example.gsb.indexmerge.index.BitmapIndex;
import com.example.gsb.indexmerge.index.HashEqualityIndex;
import com.example.gsb.indexmerge.index.SortedRangeIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 哈希等值、排序范围、位图三种索引来源混用的合并扫描。 */
class MixedIndexSourcesTest {

    private final IndexMergeScanner scanner = new IndexMergeScanner();

    private HashEqualityIndex<String> cityIndex;
    private SortedRangeIndex ageIndex;
    private BitmapIndex vipIndex;

    @BeforeEach
    void setUp() {
        // 文档 0..9，城市：偶数=北京，奇数=上海
        cityIndex = new HashEqualityIndex<>("city");
        cityIndex.put("北京", 0, 2, 4, 6, 8);
        cityIndex.put("上海", 1, 3, 5, 7, 9);

        // 年龄 = docId * 10
        long[] ages = new long[10];
        int[] docs = new int[10];
        for (int i = 0; i < 10; i++) {
            ages[i] = i * 10L;
            docs[i] = i;
        }
        ageIndex = new SortedRangeIndex("age");
        ageIndex.build(ages, docs);

        // VIP：3 的倍数
        vipIndex = new BitmapIndex("vip");
        for (int i = 0; i < 10; i += 3) {
            vipIndex.mark(i);
        }
    }

    @Test
    void intersectionAcrossThreeIndexKinds() {
        // 北京 AND 年龄 [20, 70] AND VIP -> {0,2,4,6,8} ∩ {2..7} ∩ {0,3,6,9} = {6}
        MergeResult result = scanner.scan(List.of(
                cityIndex.scan("北京"),
                ageIndex.scan(20, 70),
                vipIndex.scan()), MergeMode.INTERSECTION, null);

        assertThat(result.docIds()).containsExactly(6);
        assertThat(result.stats().indexCandidateCounts()).hasSize(3);
    }

    @Test
    void unionAcrossThreeIndexKinds() {
        // 上海 OR 年龄 [0, 10] OR VIP -> {1,3,5,7,9} ∪ {0,1} ∪ {0,3,6,9} = {0,1,3,5,6,7,9}
        MergeResult result = scanner.scan(List.of(
                cityIndex.scan("上海"),
                ageIndex.scan(0, 10),
                vipIndex.scan()), MergeMode.UNION, null);

        assertThat(result.docIds()).containsExactly(0, 1, 3, 5, 6, 7, 9);
    }

    @Test
    void mixedIntersectionWithResidualFilter() {
        // 北京 AND 年龄 [0, 90] -> {0,2,4,6,8}，再过滤掉 docId < 4
        MergeResult result = scanner.scan(List.of(
                cityIndex.scan("北京"),
                ageIndex.scan(0, 90)), MergeMode.INTERSECTION, docId -> docId >= 4);

        assertThat(result.docIds()).containsExactly(4, 6, 8);
        assertThat(result.stats().preFilterCount()).isEqualTo(5);
        assertThat(result.stats().postFilterCount()).isEqualTo(3);
        assertThat(result.stats().filteredOutCount()).isEqualTo(2);
    }

    @Test
    void missingEqualityKeyYieldsEmptyCandidateSet() {
        MergeResult result = scanner.scan(List.of(
                cityIndex.scan("广州"),
                vipIndex.scan()), MergeMode.INTERSECTION, null);

        assertThat(result.docIds()).isEmpty();
        assertThat(result.stats().indexCandidateCounts())
                .containsEntry("city[key=广州]", 0);
    }
}
