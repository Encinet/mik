use fluent_bundle::{FluentArgs, FluentResource, concurrent::FluentBundle};
use std::{collections::HashMap, sync::LazyLock};

pub(super) struct Locale {
    tag: &'static str,
    bundle: FluentBundle<FluentResource>,
}

macro_rules! catalogs {
    ($($tag:literal => $file:literal),+ $(,)?) => {
        [$(( $tag, include_str!(concat!("../../../locales/social/", $file, ".ftl")) )),+]
    }
}

static LOCALES: LazyLock<HashMap<&'static str, Locale>> = LazyLock::new(|| {
    catalogs! {
        "de-DE" => "de_de", "es-ES" => "es_es", "fr-FR" => "fr_fr",
        "it-IT" => "it_it", "ja-JP" => "ja_jp", "ko-KR" => "ko_kr", "lzh" => "lzh",
        "nl-NL" => "nl_nl", "pt-BR" => "pt_br", "ru-RU" => "ru_ru", "th-TH" => "th_th",
        "uk-UA" => "uk_ua", "zh-CN" => "zh_cn", "zh-HK" => "zh_hk", "zh-TW" => "zh_tw",
    }
    .into_iter()
    .map(|(tag, source)| (tag, Locale::bundled(tag, source)))
    .collect()
});

static DEFAULT_LOCALE: LazyLock<Locale> =
    LazyLock::new(|| Locale::bundled("en-US", include_str!("../../../locales/social/en_us.ftl")));

impl Locale {
    #[expect(
        clippy::expect_used,
        reason = "This only loads bundled catalogs and their static locale tags"
    )]
    fn bundled(tag: &'static str, source: &'static str) -> Self {
        let mut bundle = FluentBundle::new_concurrent(vec![tag.parse().expect("Fluent locale")]);
        bundle.set_use_isolating(false);
        bundle
            .add_resource(FluentResource::try_new(source.into()).expect("Fluent social catalog"))
            .expect("Unique social message IDs");
        Self { tag, bundle }
    }

    pub(super) fn supports(tag: &str) -> bool {
        tag == DEFAULT_LOCALE.tag || LOCALES.contains_key(tag)
    }

    pub(super) fn get(tag: &str) -> &'static Self {
        LOCALES.get(tag).unwrap_or_else(|| &DEFAULT_LOCALE)
    }

    pub(super) fn all() -> impl Iterator<Item = &'static Self> {
        LOCALES.values().chain(std::iter::once(&*DEFAULT_LOCALE))
    }

    pub(super) fn tag(&self) -> &'static str {
        self.tag
    }

    pub(super) fn text(&self, id: &str) -> String {
        self.format(id, None)
    }

    pub(super) fn format(&self, id: &str, args: impl Into<Option<FluentArgs<'static>>>) -> String {
        let args = args.into();
        for locale in [self, &DEFAULT_LOCALE] {
            if let Some(pattern) = locale
                .bundle
                .get_message(id)
                .and_then(|message| message.value())
            {
                let mut errors = Vec::new();
                let text = locale
                    .bundle
                    .format_pattern(pattern, args.as_ref(), &mut errors);
                if errors.is_empty() {
                    return text.into_owned();
                }
            }
        }
        id.into()
    }
}

pub(super) fn arguments(
    values: impl IntoIterator<Item = fluent_bundle::FluentValue<'static>>,
) -> FluentArgs<'static> {
    values
        .into_iter()
        .enumerate()
        .map(|(index, value)| (format!("arg{index}"), value))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::Locale;

    #[test]
    fn ai_reply_labels_need_no_fallback_or_variables() {
        for locale in Locale::all() {
            for key in ["ai-question-button-unavailable", "ai-suggested-questions"] {
                let message = locale
                    .bundle
                    .get_message(key)
                    .and_then(|message| message.value())
                    .unwrap_or_else(|| panic!("missing {key} for {}", locale.tag()));
                let mut errors = Vec::new();
                let text = locale.bundle.format_pattern(message, None, &mut errors);
                assert!(
                    errors.is_empty() && !text.trim().is_empty(),
                    "invalid {key} for {}: {errors:?}",
                    locale.tag()
                );
            }
        }
    }
}
