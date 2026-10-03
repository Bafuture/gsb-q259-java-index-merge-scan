package com.example.gsb.indexmerge;

/**
 * 统一的候选文档号遍历接口。
 *
 * <p>所有实现都必须按文档号升序返回，且不重复。文档号为非负整数；
 * {@link #NO_MORE_DOCS} 表示已经遍历完毕。
 */
public interface DocIdIterator {

    /** 遍历结束的哨兵值。 */
    int NO_MORE_DOCS = -1;

    /**
     * 推进到下一个文档号。
     *
     * @return 下一个文档号，或 {@link #NO_MORE_DOCS} 表示已耗尽
     */
    int next();

    /**
     * 快速推进到第一个不小于 {@code target} 的文档号（类似 Lucene 的 advance）。
     *
     * @param target 目标文档号
     * @return 第一个 &ge; {@code target} 的文档号，或 {@link #NO_MORE_DOCS}
     */
    int advance(int target);
}
