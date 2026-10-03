package com.example.gsb.indexmerge;

import java.util.List;

/** 合并扫描结果：有序且去重的文档号列表与统计信息。 */
public record MergeResult(List<Integer> docIds, MergeStats stats) {
}
