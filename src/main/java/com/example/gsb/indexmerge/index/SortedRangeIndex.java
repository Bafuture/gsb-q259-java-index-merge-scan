package com.example.gsb.indexmerge.index;

import com.example.gsb.indexmerge.CandidateSource;
import com.example.gsb.indexmerge.DocIdIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 排序范围索引：按字段值有序存储，支持范围条件（如 [lower, upper]）。
 *
 * <p>底层是按值升序排列的 (value, docId) 记录；范围扫描命中的文档号
 * 在返回前重新排序为文档号升序，满足统一候选集契约。
 */
public final class SortedRangeIndex {

    private final String name;
    private long[] values = new long[0];
    private int[] docIds = new int[0];

    public SortedRangeIndex(String name) {
        this.name = name;
    }

    /** 批量构建：记录按值排序后写入。 */
    public void build(long[] valueColumn, int[] docIdColumn) {
        if (valueColumn.length != docIdColumn.length) {
            throw new IllegalArgumentException("values and docIds must have the same length");
        }
        Integer[] order = new Integer[valueColumn.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Long.compare(valueColumn[a], valueColumn[b]));
        values = new long[valueColumn.length];
        docIds = new int[docIdColumn.length];
        for (int i = 0; i < order.length; i++) {
            values[i] = valueColumn[order[i]];
            docIds[i] = docIdColumn[order[i]];
        }
    }

    /** 闭区间范围扫描，返回文档号升序、去重的候选集。 */
    public CandidateSource scan(long lower, long upper) {
        int from = lowerBound(values, lower);
        int to = upperBound(values, upper);
        List<Integer> hit = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            hit.add(docIds[i]);
        }
        hit.sort(Integer::compare);
        int[] docs = new int[hit.size()];
        int n = 0;
        for (int docId : hit) {
            if (n == 0 || docId != docs[n - 1]) {
                docs[n++] = docId;
            }
        }
        if (n < docs.length) {
            docs = Arrays.copyOf(docs, n);
        }
        int[] result = docs;
        String sourceName = name + "[range=" + lower + ".." + upper + "]";
        return new CandidateSource() {
            @Override
            public String name() {
                return sourceName;
            }

            @Override
            public int size() {
                return result.length;
            }

            @Override
            public DocIdIterator iterator() {
                return new com.example.gsb.indexmerge.ArrayDocIdIterator(result.clone());
            }
        };
    }

    private static int lowerBound(long[] sorted, long target) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int upperBound(long[] sorted, long target) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
