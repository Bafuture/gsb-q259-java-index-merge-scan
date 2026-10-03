package com.example.gsb.indexmerge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 合并扫描统计信息。 */
public final class MergeStats {

    private final Map<String, Integer> indexCandidateCounts = new LinkedHashMap<>();
    private int mergeRounds;
    private int peakIntermediateSize;
    private int preFilterCount;
    private int postFilterCount;

    void putCandidateCount(String name, int count) {
        indexCandidateCounts.merge(name, count, Integer::sum);
    }

    void recordRound(int intermediateSize) {
        mergeRounds++;
        peakIntermediateSize = Math.max(peakIntermediateSize, intermediateSize);
    }

    void setFilterCounts(int preFilterCount, int postFilterCount) {
        this.preFilterCount = preFilterCount;
        this.postFilterCount = postFilterCount;
    }

    /** 各索引条件命中的候选数（按扫描注册顺序）。 */
    public Map<String, Integer> indexCandidateCounts() {
        return Collections.unmodifiableMap(indexCandidateCounts);
    }

    /** 合并轮次：并集为弹出堆顶的次数，交集为两两归并的次数。 */
    public int mergeRounds() {
        return mergeRounds;
    }

    /** 归并过程中中间结果的峰值大小。 */
    public int peakIntermediateSize() {
        return peakIntermediateSize;
    }

    /** 二次过滤前的候选条数。 */
    public int preFilterCount() {
        return preFilterCount;
    }

    /** 二次过滤后的候选条数。 */
    public int postFilterCount() {
        return postFilterCount;
    }

    /** 二次过滤丢弃的条数。 */
    public int filteredOutCount() {
        return preFilterCount - postFilterCount;
    }

    @Override
    public String toString() {
        return "MergeStats{"
                + "indexCandidateCounts=" + indexCandidateCounts
                + ", mergeRounds=" + mergeRounds
                + ", peakIntermediateSize=" + peakIntermediateSize
                + ", preFilterCount=" + preFilterCount
                + ", postFilterCount=" + postFilterCount
                + ", filteredOutCount=" + filteredOutCount()
                + '}';
    }
}
