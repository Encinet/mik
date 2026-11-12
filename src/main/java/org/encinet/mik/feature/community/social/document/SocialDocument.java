package org.encinet.mik.feature.community.social.document;

import org.encinet.mik.shared.i18n.Language;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Platform-neutral, structured content returned by social features. */
public record SocialDocument(
        Moderation moderation,
        String title,
        Tone tone,
        List<Block> blocks,
        Language language
) {

    public SocialDocument {
        moderation = Objects.requireNonNull(moderation, "moderation");
        title = singleLine(Objects.requireNonNull(title, "title"));
        tone = Objects.requireNonNull(tone, "tone");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        language = Objects.requireNonNull(language, "language");
        if (title.isEmpty() && blocks.stream().allMatch(block -> block.plainText().isBlank())) {
            throw new IllegalArgumentException("document must contain visible content");
        }
    }

    public SocialDocument(Moderation moderation, String title, Tone tone, List<Block> blocks) {
        this(moderation, title, tone, blocks, Language.DEFAULT);
    }

    public static SocialDocument of(Moderation moderation, String title, Tone tone, Block... blocks) {
        return new SocialDocument(moderation, title, tone, List.of(blocks));
    }

    public static Paragraph paragraph(String text) {
        return new Paragraph(text);
    }

    /** A short reply has no decorative heading. */
    public static SocialDocument message(Moderation moderation, Tone tone, Block... blocks) {
        return of(moderation, "", tone, blocks);
    }

    public static Instruction instruction(String explanation, String invocation) {
        return new Instruction(explanation, invocation);
    }

    public static Fields fields(Field... fields) {
        return new Fields(List.of(fields));
    }

    public static ItemList orderedList(String... items) {
        return new ItemList(true, List.of(items));
    }

    public static ItemList unorderedList(String... items) {
        return new ItemList(false, List.of(items));
    }

    public static Image image(String alternativeText, URI url, int width, int height) {
        return new Image(alternativeText, new RemoteImage(url), width, height);
    }

    public static Image image(
            String alternativeText,
            String mediaType,
            byte[] data,
            int width,
            int height
    ) {
        Objects.requireNonNull(data, "data");
        return new Image(alternativeText,
                new EmbeddedImage(mediaType, Base64.getEncoder().encodeToString(data)),
                width, height);
    }

    /** Carries the resolved response language through the shared outbound pipeline. */
    public SocialDocument localized(Language resolvedLanguage) {
        Language checked = Objects.requireNonNull(resolvedLanguage, "resolvedLanguage");
        return checked == language
                ? this : new SocialDocument(moderation, title, tone, blocks, checked);
    }

    /** A deterministic unformatted representation used by renderers and tests. */
    public String plainText() {
        StringBuilder result = new StringBuilder(title);
        for (Block block : blocks) {
            String text = block.plainText();
            if (!text.isBlank()) {
                if (!result.isEmpty()) result.append("\n\n");
                result.append(text);
            }
        }
        return result.toString();
    }

    /** Every reply declares its trust at creation; there is no default or later override. */
    public enum Moderation {
        TRUSTED,
        REQUIRED
    }

    public enum Tone {
        NEUTRAL,
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    public sealed interface Block permits Paragraph, Fields, ItemList, Image, Instruction {
        String plainText();
    }

    /** Display-only instruction: explanation and invocation must remain together. */
    public record Instruction(String explanation, String invocation) implements Block {
        public Instruction {
            explanation = normalizeMultiline(Objects.requireNonNull(explanation, "explanation"));
            invocation = Objects.requireNonNull(invocation, "invocation").strip();
            if (explanation.isBlank()) {
                throw new IllegalArgumentException("instruction must explain the command");
            }
            if (invocation.isEmpty() || invocation.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("command must be a nonempty single line");
            }
        }

        @Override
        public String plainText() {
            return explanation + "\n\n" + invocation;
        }
    }

    public record Paragraph(String text) implements Block {
        public Paragraph {
            text = normalizeMultiline(Objects.requireNonNull(text, "text"));
        }

        @Override
        public String plainText() {
            return text;
        }
    }

    public record Field(String label, String value) {
        public Field {
            label = singleLine(Objects.requireNonNull(label, "label"));
            value = normalizeMultiline(Objects.requireNonNull(value, "value"));
            if (label.isEmpty()) {
                throw new IllegalArgumentException("field label must not be blank");
            }
        }
    }

    public record Fields(List<Field> fields, boolean omitted) implements Block {
        public Fields(List<Field> fields) {
            this(fields, false);
        }

        public Fields {
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("fields must not be empty");
            }
        }

        @Override
        public String plainText() {
            List<String> lines = new ArrayList<>(fields.size());
            for (Field field : fields) {
                lines.add(field.label() + ": " + field.value());
            }
            if (omitted) lines.add("…");
            return String.join("\n", lines);
        }
    }

    public record ItemList(boolean ordered, List<String> items) implements Block {
        public ItemList {
            Objects.requireNonNull(items, "items");
            List<String> clean = new ArrayList<>(items.size());
            for (String item : items) {
                String normalized = normalizeMultiline(Objects.requireNonNull(item, "item"));
                if (!normalized.isBlank()) {
                    clean.add(normalized);
                }
            }
            if (clean.isEmpty()) {
                throw new IllegalArgumentException("list items must not be empty");
            }
            items = List.copyOf(clean);
        }

        @Override
        public String plainText() {
            List<String> lines = new ArrayList<>(items.size());
            for (int index = 0; index < items.size(); index++) {
                lines.add((ordered ? (index + 1) + ". " : "- ") + items.get(index));
            }
            return String.join("\n", lines);
        }
    }

    public sealed interface ImageSource permits RemoteImage, EmbeddedImage {
    }

    public record RemoteImage(URI url) implements ImageSource {
        public RemoteImage {
            url = Objects.requireNonNull(url, "url").normalize();
            String scheme = url.getScheme();
            if (!url.isAbsolute() || url.getHost() == null
                    || (!"https".equalsIgnoreCase(scheme)
                    && !"http".equalsIgnoreCase(scheme))
                    || url.getUserInfo() != null || url.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "image URL must be an absolute HTTP(S) URL without credentials or fragment");
            }
        }
    }

    public record EmbeddedImage(String mediaType, String base64Data) implements ImageSource {
        private static final int MAXIMUM_BASE64_LENGTH = 14_000_000;

        public EmbeddedImage {
            mediaType = singleLine(Objects.requireNonNull(mediaType, "mediaType"))
                    .toLowerCase(java.util.Locale.ROOT);
            if (!mediaType.matches("image/(png|jpeg|gif|webp)")) {
                throw new IllegalArgumentException("unsupported embedded image media type");
            }
            base64Data = Objects.requireNonNull(base64Data, "base64Data");
            if (base64Data.isBlank() || base64Data.length() > MAXIMUM_BASE64_LENGTH) {
                throw new IllegalArgumentException("embedded image data has an invalid length");
            }
            try {
                Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("embedded image data is not valid Base64", error);
            }
        }
    }

    /** An image that adapters may render inline or upload as native media. */
    public record Image(String alternativeText, ImageSource source, int width, int height)
            implements Block {
        public Image {
            alternativeText = singleLine(Objects.requireNonNull(
                    alternativeText, "alternativeText"));
            if (alternativeText.isEmpty()) {
                throw new IllegalArgumentException("image alternativeText must not be blank");
            }
            source = Objects.requireNonNull(source, "source");
            if (width < 1 || width > 4_096 || height < 1 || height > 4_096) {
                throw new IllegalArgumentException(
                        "image dimensions must be between 1 and 4096 pixels");
            }
        }

        @Override
        public String plainText() {
            return source instanceof RemoteImage remote
                    ? alternativeText + ": " + remote.url().toASCIIString()
                    : '[' + alternativeText + ']';
        }
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').strip();
    }

    private static String normalizeMultiline(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

}
