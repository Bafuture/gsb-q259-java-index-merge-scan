package com.example.gsb.indexmerge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/**
 * 候选索引合并扫描器。
 *
 * <p>把多个索引条件命中的有序候选集合并成一个有序、去重的结果：
 * <ul>
 *   <li>{@link MergeMode#UNION}：基于最小堆的 k 路归并，流式产出，天然去重；</li>
 *   <li>{@link MergeMode#INTERSECTION}：按当前剩余长度从小到大两两归并，
 *       每一轮总是合并最小的两个候选集，使中间结果大小被最小候选集约束，避免膨胀。</li>
 * </ul>
 * 合并完成后可选地用非索引条件做二次过滤，并统计过滤前后的条数。
 */
public final class IndexMergeScanner {

    /**
     * 执行合并扫描。
     *
     * @param sources    各索引条件命中的候选集（允许混用不同索引来源）
     * @param mode       并集或交集
     * @param postFilter 非索引条件的二次过滤，{@code null} 表示不过滤
     * @return 有序去重的文档号与统计信息
     */
    public MergeResult scan(List<? extends CandidateSource> sources,
                            MergeMode mode,
                            IntPredicate postFilter) {
        MergeStats stats = new MergeStats();
        for (CandidateSource source : sources) {
            stats.putCandidateCount(source.name(), source.size());
        }

        List<Integer> merged = switch (mode) {
            case UNION -> union(sources, stats);
            case INTERSECTION -> intersection(sources, stats);
        };

        List<Integer> result = merged;
        if (postFilter != null) {
            result = new ArrayList<>(merged.size());
            for (int docId : merged) {
                if (postFilter.test(docId)) {
                    result.add(docId);
                }
            }
        }
        stats.setFilterCounts(merged.size(), result.size());
        return new MergeResult(result, stats);
    }

    /** 并集：最小堆 k 路归并，跳过与上次输出相同的文档号。 */
    private List<Integer> union(List<? extends CandidateSource> sources, MergeStats stats) {
        record Head(int docId, DocIdIterator iterator) {
        }
        PriorityQueue<Head> heap = new PriorityQueue<>(Comparator.comparingInt(Head::docId));
        for (CandidateSource source : sources) {
            DocIdIterator iterator = source.iterator();
            int docId = iterator.next();
            if (docId != DocIdIterator.NO_MORE_DOCS) {
                heap.offer(new Head(docId, iterator));
            }
        }

        List<Integer> out = new ArrayList<>();
        int lastEmitted = DocIdIterator.NO_MORE_DOCS;
        while (!heap.isEmpty()) {
            stats.recordRound(heap.size());
            Head head = heap.poll();
            if (head.docId() != lastEmitted) {
                out.add(head.docId());
                lastEmitted = head.docId();
            }
            int next = head.iterator().next();
            if (next != DocIdIterator.NO_MORE_DOCS) {
                heap.offer(new Head(next, head.iterator()));
            }
        }
        return out;
    }

    /**
     * 交集：按当前剩余长度从小到大两两归并。
     * 每轮弹出最小的两个候选集求交，结果重新入队，因此任何中间结果
     * 都不超过当前最小候选集的长度。
     */
    private List<Integer> intersection(List<? extends CandidateSource> sources, MergeStats stats) {
        PriorityQueue<int[]> queue = new PriorityQueue<>(Comparator.comparingInt(a -> a.length));
        for (CandidateSource source : sources) {
            int[] docs = drain(source.iterator());
            if (docs.length == 0) {
                return List.of();
            }
            queue.offer(docs);
        }
        if (queue.isEmpty()) {
            return List.of();
        }

        while (queue.size() > 1) {
            int[] smallest = queue.poll();
            int[] second = queue.poll();
            int[] merged = intersectPair(smallest, second);
            stats.recordRound(merged.length);
            if (merged.length == 0) {
                return List.of();
            }
            queue.offer(merged);
        }

        int[] result = queue.poll();
        List<Integer> out = new ArrayList<>(result.length);
        for (int docId : result) {
            out.add(docId);
        }
        return out;
    }

    /** 双指针求两个有序数组的交集。 */
    private static int[] intersectPair(int[] a, int[] b) {
        int[] tmp = new int[Math.min(a.length, b.length)];
        int i = 0;
        int j = 0;
        int n = 0;
        while (i < a.length && j < b.length) {
            if (a[i] < b[j]) {
                i++;
            } else if (a[i] > b[j]) {
                j++;
            } else {
                tmp[n++] = a[i];
                i++;
                j++;
            }
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    private static int[] drain(DocIdIterator iterator) {
        List<Integer> buf = new ArrayList<>();
        for (int docId = iterator.next();
             docId != DocIdIterator.NO_MORE_DOCS;
             docId = iterator.next()) {
            buf.add(docId);
        }
        int[] out = new int[buf.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.get(i);
        }
        return out;
    }
}
