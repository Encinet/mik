use icu_casemap::CaseMapper;
use icu_segmenter::{WordSegmenter, options::WordBreakInvariantOptions};
use tantivy::tokenizer::{Token, TokenStream, Tokenizer};
use unicode_normalization::{UnicodeNormalization, char::is_combining_mark};

#[derive(Clone)]
pub(super) struct IcuTokenizer;
pub(super) struct IcuTokens {
    tokens: std::vec::IntoIter<Token>,
    current: Token,
}
impl Tokenizer for IcuTokenizer {
    type TokenStream<'a> = IcuTokens;
    fn token_stream<'a>(&'a mut self, text: &'a str) -> IcuTokens {
        let mut iterator =
            WordSegmenter::new_auto(WordBreakInvariantOptions::default()).segment_str(text);
        let mut previous = 0;
        let mut tokens = Vec::new();
        while let Some(end) = iterator.next() {
            if iterator.is_word_like() {
                let folded = CaseMapper::new().fold_string(&text[previous..end]);
                let term: String = folded.nfkd().filter(|c| !is_combining_mark(*c)).collect();
                if !term.is_empty() && term.len() <= 1024 {
                    tokens.push(Token {
                        offset_from: previous,
                        offset_to: end,
                        position: tokens.len(),
                        text: term,
                        position_length: 1,
                    });
                }
            }
            previous = end;
        }
        IcuTokens {
            tokens: tokens.into_iter(),
            current: Token::default(),
        }
    }
}
impl TokenStream for IcuTokens {
    fn advance(&mut self) -> bool {
        if let Some(token) = self.tokens.next() {
            self.current = token;
            true
        } else {
            false
        }
    }
    fn token(&self) -> &Token {
        &self.current
    }
    fn token_mut(&mut self) -> &mut Token {
        &mut self.current
    }
}
