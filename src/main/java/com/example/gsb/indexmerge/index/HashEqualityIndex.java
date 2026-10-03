package com.example.gsb.indexmerge.index;

import com.example.gsb.indexmerge.ArrayDocIdIterator;
import com.example.gsb.indexmerge.CandidateSource;
import com.example.gsb.indexmerge.DocIdIterator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 哈希等值索引：key -&gt; 有序文档号数组，支持等值条件（如 term 查询）。
 */
public final class HashEqualityIndex<K> {

    private final String name;
    private final Map<K, int[]> postings = new HashMap<>();

    public HashEqualityIndex(String name) {
        this.name = name;
    }

    /** 用某个 key 对应的全部命中文档号构建（或覆盖）倒排链。 */
    public void put(K key, int... docIds) {
        int[] copy = docIds.clone();
        Arrays.sort(copy);
        postings.put(key, dedupSorted(copy));
    }

    /** 等值查询，返回统一接口的候选集；未命中返回空候选集。 */
    public CandidateSource scan(K key) {
        int[] docs = postings.getOrDefault(key, new int[0]);
        String sourceName = name + "[key=" + key + "]";
        return new CandidateSource() {
            @Override
            public String name() {
                return sourceName;
            }

            @Override
            public int size() {
                return docs.length;
            }

            @Override
            public DocIdIterator iterator() {
                return new ArrayDocIdIterator(docs.clone());
            }
        };
    }

    static int[] dedupSorted(int[] sorted) {
        int n = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (n == 0 || sorted[i] != sorted[n - 1]) {
                sorted[n++] = sorted[i];
            }
        }
        return n == sorted.length ? sorted : Arrays.copyOf(sorted, n);
    }
}
