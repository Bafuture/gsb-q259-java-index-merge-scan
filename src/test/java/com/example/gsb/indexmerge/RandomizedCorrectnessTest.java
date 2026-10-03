package com.example.gsb.indexmerge;

import com.example.gsb.indexmerge.index.BitmapIndex;
import com.example.gsb.indexmerge.index.HashEqualityIndex;
import com.example.gsb.indexmerge.index.SortedRangeIndex;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 随机数据正确性验证：合并扫描结果必须与"逐个取全集再用集合语义合并"
 * 的暴力结果完全一致。
 */
class RandomizedCorrectnessTest {

    private static final int DOC_SPACE = 500;

    private final IndexMergeScanner scanner = new IndexMergeScanner();

    @RepeatedTest(100)
    void unionMatchesBruteForceOverFullSets() {
        Random random = new Random(System.nanoTime());
        List<TreeSet<Integer>> fullSets = randomSets(random);
        List<CandidateSource> sources = toSources(fullSets);

        IntPredicate filter = random.nextBoolean() ? d -> d % 3 != 0 : null;
        MergeResult result = scanner.scan(sources, MergeMode.UNION, filter);

        TreeSet<Integer> expected = new TreeSet<>();
        fullSets.forEach(expected::addAll);
        if (filter != null) {
            expected.removeIf(d -> !filter.test(d));
        }

        assertThat(result.docIds()).containsExactlyElementsOf(expected);
        assertThat(result.stats().preFilterCount()).isEqualTo(
                filter == null ? expected.size() : expected.size() + result.stats().filteredOutCount());
    }

    @RepeatedTest(100)
    void intersectionMatchesBruteForceOverFullSets() {
        Random random = new Random(System.nanoTime());
        List<TreeSet<Integer>> fullSets = randomSets(random);
        List<CandidateSource> sources = toSources(fullSets);

        IntPredicate filter = random.nextBoolean() ? d -> (d & 1) == 0 : null;
        MergeResult result = scanner.scan(sources, MergeMode.INTERSECTION, filter);

        TreeSet<Integer> expected = new TreeSet<>(fullSets.get(0));
        for (int i = 1; i < fullSets.size(); i++) {
            expected.retainAll(fullSets.get(i));
        }
        if (filter != null) {
            expected.removeIf(d -> !filter.test(d));
        }

        assertThat(result.docIds()).containsExactlyElementsOf(expected);
        assertThat(result.stats().filteredOutCount())
                .isEqualTo(result.stats().preFilterCount() - result.stats().postFilterCount());
    }

    @Test
    void mixedIndexKindsMatchBruteForceOnRandomData() {
        for (long seed = 0; seed < 50; seed++) {
            Random random = new Random(seed);

            // 随机生成哈希等值索引
            HashEqualityIndex<Integer> hashIndex = new HashEqualityIndex<>("hash");
            int keys = 1 + random.nextInt(5);
            List<TreeSet<Integer>> hashSets = new ArrayList<>();
            for (int k = 0; k < keys; k++) {
                TreeSet<Integer> set = randomSet(random);
                hashSets.add(set);
                hashIndex.put(k, set.stream().mapToInt(Integer::intValue).toArray());
            }

            // 随机生成排序范围索引
            int n = 1 + random.nextInt(80);
            long[] values = new long[n];
            int[] docIds = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = random.nextInt(1000);
                docIds[i] = random.nextInt(DOC_SPACE);
            }
            SortedRangeIndex rangeIndex = new SortedRangeIndex("range");
            rangeIndex.build(values, docIds);
            long lower = random.nextInt(1000);
            long upper = lower + random.nextInt(1000 - (int) lower);

            // 随机生成位图索引
            BitmapIndex bitmapIndex = new BitmapIndex("bitmap");
            TreeSet<Integer> bitmapSet = randomSet(random);
            bitmapSet.forEach(bitmapIndex::mark);

            // 暴力全集
            TreeSet<Integer> hashFull = hashSets.get(random.nextInt(keys));
            TreeSet<Integer> rangeFull = new TreeSet<>();
            for (int i = 0; i < n; i++) {
                if (values[i] >= lower && values[i] <= upper) {
                    rangeFull.add(docIds[i]);
                }
            }

            int hashKey = hashSets.indexOf(hashFull);
            List<CandidateSource> sources = List.of(
                    hashIndex.scan(hashKey),
                    rangeIndex.scan(lower, upper),
                    bitmapIndex.scan());

            TreeSet<Integer> expectedUnion = new TreeSet<>();
            expectedUnion.addAll(hashFull);
            expectedUnion.addAll(rangeFull);
            expectedUnion.addAll(bitmapSet);
            MergeResult union = scanner.scan(sources, MergeMode.UNION, null);
            assertThat(union.docIds()).containsExactlyElementsOf(expectedUnion);

            TreeSet<Integer> expectedIntersection = new TreeSet<>(hashFull);
            expectedIntersection.retainAll(rangeFull);
            expectedIntersection.retainAll(bitmapSet);
            MergeResult intersection = scanner.scan(sources, MergeMode.INTERSECTION, null);
            assertThat(intersection.docIds()).containsExactlyElementsOf(expectedIntersection);
        }
    }

    private static List<TreeSet<Integer>> randomSets(Random random) {
        int count = 1 + random.nextInt(6);
        List<TreeSet<Integer>> sets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sets.add(randomSet(random));
        }
        return sets;
    }

    private static TreeSet<Integer> randomSet(Random random) {
        TreeSet<Integer> set = new TreeSet<>();
        int size = random.nextInt(120);
        for (int i = 0; i < size; i++) {
            set.add(random.nextInt(DOC_SPACE));
        }
        return set;
    }

    private static List<CandidateSource> toSources(List<TreeSet<Integer>> fullSets) {
        List<CandidateSource> sources = new ArrayList<>();
        for (int i = 0; i < fullSets.size(); i++) {
            int[] docs = fullSets.get(i).stream().mapToInt(Integer::intValue).toArray();
            sources.add(Sources.of("idx-" + i, docs));
        }
        return sources;
    }
}
