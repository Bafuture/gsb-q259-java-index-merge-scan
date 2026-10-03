package com.example.gsb.indexmerge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Merges ordered candidate sets pulled from multiple indexes into a single
 * ordered, de-duplicated result.
 *
 * <p>Merging always advances the currently smallest remaining candidate set
 * first (Huffman-style): the two smallest sets are merged, the intermediate
 * result is pushed back, and the loop repeats. For intersections this keeps
 * intermediate results bounded by the smallest inputs instead of letting them
 * grow toward the largest one.
 */
public final class IndexMergeScanner {

    /** Union of all candidate sets: ascending, de-duplicated. */
    public MergeResult union(List<IndexCandidates> inputs) {
        return merge(inputs, MergeStats.Mode.UNION);
    }

    /** Intersection of all candidate sets: ascending, de-duplicated. */
    public MergeResult intersect(List<IndexCandidates> inputs) {
        return merge(inputs, MergeStats.Mode.INTERSECT);
    }

    private MergeResult merge(List<IndexCandidates> inputs, MergeStats.Mode mode) {
        MergeStats stats = new MergeStats(mode);
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(Node::size));
        for (IndexCandidates input : inputs) {
            List<Integer> ids = drain(input);
            stats.recordIndexCandidates(input.indexName(), ids.size());
            queue.add(new Node(ids));
        }

        if (queue.isEmpty()) {
            stats.recordCandidateCount(0);
            return new MergeResult(List.of(), stats);
        }

        while (queue.size() > 1) {
            Node smallest = queue.poll();
            Node nextSmallest = queue.poll();
            List<Integer> merged = mode == MergeStats.Mode.UNION
                    ? unionTwo(smallest.ids(), nextSmallest.ids())
                    : intersectTwo(smallest.ids(), nextSmallest.ids());
            stats.recordMergeRound(merged.size());
            queue.add(new Node(merged));
        }

        List<Integer> result = queue.poll().ids();
        stats.recordCandidateCount(result.size());
        return new MergeResult(result, stats);
    }

    private static List<Integer> drain(IndexCandidates input) {
        CandidateCursor cursor = input.cursor();
        List<Integer> ids = new ArrayList<>(input.size());
        while (cursor.hasNext()) {
            ids.add(cursor.next());
        }
        if (ids.size() != input.size()) {
            throw new IllegalStateException("index '" + input.indexName() + "' declared "
                    + input.size() + " candidates but produced " + ids.size());
        }
        for (int i = 1; i < ids.size(); i++) {
            if (ids.get(i) <= ids.get(i - 1)) {
                throw new IllegalArgumentException("index '" + input.indexName()
                        + "' produced a non-ascending or duplicate candidate stream");
            }
        }
        return ids;
    }

    private static List<Integer> unionTwo(List<Integer> left, List<Integer> right) {
        List<Integer> out = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            int a = left.get(i);
            int b = right.get(j);
            if (a < b) {
                out.add(a);
                i++;
            } else if (b < a) {
                out.add(b);
                j++;
            } else {
                out.add(a);
                i++;
                j++;
            }
        }
        out.addAll(left.subList(i, left.size()));
        out.addAll(right.subList(j, right.size()));
        return out;
    }

    private static List<Integer> intersectTwo(List<Integer> left, List<Integer> right) {
        List<Integer> out = new ArrayList<>(Math.min(left.size(), right.size()));
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            int a = left.get(i);
            int b = right.get(j);
            if (a < b) {
                i++;
            } else if (b < a) {
                j++;
            } else {
                out.add(a);
                i++;
                j++;
            }
        }
        return out;
    }

    private record Node(List<Integer> ids) {
        int size() {
            return ids.size();
        }
    }
}
