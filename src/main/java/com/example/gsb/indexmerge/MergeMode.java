package com.example.gsb.indexmerge;

/** 候选集合并方式。 */
public enum MergeMode {
    /** 并集：任一索引命中即保留。 */
    UNION,
    /** 交集：所有索引都命中才保留。 */
    INTERSECTION
}
