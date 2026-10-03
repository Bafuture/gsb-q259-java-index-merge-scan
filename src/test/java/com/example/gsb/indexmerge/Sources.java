package com.example.gsb.indexmerge;

/** 测试辅助：从有序数组构造候选集。 */
final class Sources {

    private Sources() {
    }

    static CandidateSource of(String name, int... docs) {
        int[] copy = docs.clone();
        java.util.Arrays.sort(copy);
        return new CandidateSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int size() {
                return copy.length;
            }

            @Override
            public DocIdIterator iterator() {
                return new ArrayDocIdIterator(copy.clone());
            }
        };
    }
}
