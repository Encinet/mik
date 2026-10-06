package org.encinet.mik.module.ai.knowledge.adapter.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.ICUFoldingFilter;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;

/** Unicode-aware analyzer suitable for mixed-language server knowledge. */
final class IcuKnowledgeAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new ICUTokenizer();
        TokenStream folded = new ICUFoldingFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, folded);
    }
}
