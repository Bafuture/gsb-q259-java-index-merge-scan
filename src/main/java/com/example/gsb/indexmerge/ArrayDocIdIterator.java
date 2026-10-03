package com.example.gsb.indexmerge;

import java.util.Arrays;

/** 基于有序文档号数组的迭代器，{@link #advance(int)} 使用二分查找。 */
public final class ArrayDocIdIterator implements DocIdIterator {

    private final int[] docs;
    private int pos = -1;

    public ArrayDocIdIterator(int[] docs) {
        this.docs = docs;
    }

    @Override
    public int next() {
        return pos + 1 < docs.length ? docs[++pos] : NO_MORE_DOCS;
    }

    @Override
    public int advance(int target) {
        int from = pos + 1;
        if (from >= docs.length) {
            pos = docs.length;
            return NO_MORE_DOCS;
        }
        int idx = Arrays.binarySearch(docs, from, docs.length, target);
        if (idx < 0) {
            idx = -idx - 1;
        }
        if (idx >= docs.length) {
            pos = docs.length;
            return NO_MORE_DOCS;
        }
        pos = idx;
        return docs[pos];
    }
}
