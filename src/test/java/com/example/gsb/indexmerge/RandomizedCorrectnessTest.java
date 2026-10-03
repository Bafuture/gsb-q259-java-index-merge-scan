package com.example.gsb.indexmerge;

import com.example.gsb.indexmerge.index.BitmapIndex;
import com.example.gsb.indexmerge.index.HashEqualityIndex;
import com.example.gsb.indexmerge.index.SortedRangeIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Randomized equivalence check: the merge result must always equal what you
 * get by pulling every full posting list and doing the set operation eagerly.
 */
class RandomizedCorrectnessTest {

    private static final int ITERATIONS = 200;
    private static final String[] CITIES = {"beijing", "shanghai", "shenzhen", "guangzhou"};
    private static final String[] TAGS = {"sale", "new", "vip", "none"};

    private final IndexMergeScanner scanner = new IndexMergeScanner();

    private record Document(String city, int score, String tag) {
    }

    private record IndexBundle(
            HashEqualityIndex<String> city,
            SortedRangeIndex<Integer> price,
            BitmapIndex<String> tag) {

        List<IndexCandidates> query(String cityValue, int lo, int hi, String tagValue) {
            return List.of(
                    city.lookup(cityValue),
                    price.range(lo, hi),
                    tag.lookup(tagValue));
        }
    }

    @Test
    void mergeMatchesEagerFullSetOperationsAcrossRandomData() {
        long seed = 20261003L;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            Random random = new Random(seed ^ ((long) iteration * 0x9E3779B97F4A7C15L));

            List<Document> docs = buildDocuments(random, random.nextInt(120));
            IndexBundle indexes = buildIndexes(docs);

            String city = CITIES[random.nextInt(CITIES.length)];
            String tag = TAGS[random.nextInt(TAGS.length)];
            int lo = random.nextInt(100);
            int hi = lo + random.nextInt(101 - lo);

            TreeSet<Integer> expectedUnion = bruteUnion(docs, city, lo, hi, tag);
            TreeSet<Integer> expectedIntersect = bruteIntersect(docs, city, lo, hi, tag);

            MergeResult union = scanner.union(indexes.query(city, lo, hi, tag));
            MergeResult intersect = scanner.intersect(indexes.query(city, lo, hi, tag));

            assertThat(union.docIds())
                    .as("union mismatch on iteration %d", iteration)
                    .isEqualTo(new ArrayList<>(expectedUnion));
            assertThat(intersect.docIds())
                    .as("intersect mismatch on iteration %d", iteration)
                    .isEqualTo(new ArrayList<>(expectedIntersect));
            assertThat(new HashSet<>(union.docIds())).hasSameSizeAs(union.docIds());
            assertThat(new HashSet<>(intersect.docIds())).hasSameSizeAs(intersect.docIds());
            assertAscending(union.docIds());
            assertAscending(intersect.docIds());
            assertStatsSanity(union, expectedUnion.size());
            assertStatsSanity(intersect, expectedIntersect.size());

            int parity = random.nextInt(2);
            MergeResult.FilteredResult filtered = union.filter(docId -> docId % 2 == parity);
            List<Integer> expectedFiltered = expectedUnion.stream()
                    .filter(docId -> docId % 2 == parity)
                    .toList();
            assertThat(filtered.docIds()).isEqualTo(expectedFiltered);
            assertThat(filtered.dropped()).isEqualTo(expectedUnion.size() - expectedFiltered.size());
            assertThat(union.stats().postFilterKept()).isEqualTo(expectedFiltered.size());
        }
    }

    private static TreeSet<Integer> bruteUnion(List<Document> docs, String city, int lo, int hi, String tag) {
        TreeSet<Integer> hits = new TreeSet<>();
        for (int docId = 0; docId < docs.size(); docId++) {
            Document doc = docs.get(docId);
            if (doc.city().equals(city)
                    || (doc.score() >= lo && doc.score() <= hi)
                    || doc.tag().equals(tag)) {
                hits.add(docId);
            }
        }
        return hits;
    }

    private static TreeSet<Integer> bruteIntersect(List<Document> docs, String city, int lo, int hi, String tag) {
        TreeSet<Integer> hits = new TreeSet<>();
        for (int docId = 0; docId < docs.size(); docId++) {
            Document doc = docs.get(docId);
            if (doc.city().equals(city)
                    && doc.score() >= lo && doc.score() <= hi
                    && doc.tag().equals(tag)) {
                hits.add(docId);
            }
        }
        return hits;
    }

    private static List<Document> buildDocuments(Random random, int docCount) {
        List<Document> docs = new ArrayList<>(docCount);
        for (int i = 0; i < docCount; i++) {
            docs.add(new Document(
                    CITIES[random.nextInt(CITIES.length)],
                    random.nextInt(100),
                    TAGS[random.nextInt(TAGS.length)]));
        }
        return docs;
    }

    private static IndexBundle buildIndexes(List<Document> docs) {
        HashEqualityIndex<String> city = new HashEqualityIndex<>("city");
        SortedRangeIndex<Integer> price = new SortedRangeIndex<>("price");
        BitmapIndex<String> tag = new BitmapIndex<>("tag");
        for (int docId = 0; docId < docs.size(); docId++) {
            Document doc = docs.get(docId);
            city.add(doc.city(), docId);
            price.add(doc.score(), docId);
            tag.add(doc.tag(), docId);
        }
        return new IndexBundle(city, price, tag);
    }

    private static void assertAscending(List<Integer> ids) {
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i)).isGreaterThan(ids.get(i - 1));
        }
    }

    private static void assertStatsSanity(MergeResult result, int expectedSize) {
        assertThat(result.stats().candidateCount()).isEqualTo(expectedSize);
        int inputCount = result.stats().indexCandidateCounts().size();
        assertThat(result.stats().mergeRounds()).isEqualTo(Math.max(0, inputCount - 1));
        if (inputCount > 1) {
            assertThat(result.stats().peakIntermediateSize()).isGreaterThanOrEqualTo(expectedSize);
        }
    }
}
