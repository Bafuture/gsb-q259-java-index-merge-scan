package com.example.gsb.indexmerge;

/**
 * 一个索引条件命中的候选集：暴露统一的遍历接口与候选总数，
 * 供合并扫描器混用不同索引来源。
 */
public interface CandidateSource {

    /** 候选集名称（通常是索引名 + 条件描述），用于统计。 */
    String name();

    /** 候选文档号数量。 */
    int size();

    /** 返回一个新的升序遍历器。 */
    DocIdIterator iterator();
}
