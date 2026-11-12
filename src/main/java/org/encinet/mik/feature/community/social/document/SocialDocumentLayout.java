package org.encinet.mik.feature.community.social.document;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import static org.encinet.mik.feature.community.social.document.SocialDocument.*;

/** Fits one semantic layout against every actual representation that may be sent. */
public final class SocialDocumentLayout {
    private SocialDocumentLayout() { }

    public static SocialDocument fit(SocialDocument document, int maximumLength, ToIntFunction<SocialDocument> encodedLength) {
        if (maximumLength < 16) throw new IllegalArgumentException("maximumLength must be at least 16");
        int budget = maximumLength;
        while (true) {
            SocialDocument bounded = limit(document, budget);
            int length = encodedLength.applyAsInt(bounded);
            if (length <= maximumLength) return bounded;
            if (budget == 1) return SocialDocument.message(document.moderation(), document.tone(), paragraph("…")).localized(document.language());
            budget = Math.max(1, (int) Math.min(budget - 1, (long) budget * maximumLength / length));
        }
    }

    /** Bounds the semantic result before a platform adds its own markup. */
    public static SocialDocument limit(SocialDocument document, int maximumLength) {
        if (maximumLength < 1) {
            throw new IllegalArgumentException("maximumLength must be positive");
        }
        String plain = document.plainText();
        if (plain.length() <= maximumLength) {
            return document;
        }
        // An instruction includes the object and consequences, not just its command.
        List<Block> actions = new ArrayList<>();
        int actionLength = 0;
        for (Block block : document.blocks()) {
            if (block instanceof Instruction instruction) {
                int cost = instruction.plainText().length() + (actions.isEmpty() ? 0 : 2);
                if (actionLength + cost <= maximumLength) {
                    actions.add(instruction);
                    actionLength += cost;
                }
            }
        }
        int contentBudget = maximumLength - actionLength - (actions.isEmpty() ? 0 : 2);
        String boundedTitle = contentBudget > 0 ? limitText(document.title(), contentBudget) : "";
        int used = boundedTitle.length();
        List<Block> bounded = new ArrayList<>();
        boolean exhausted = false;
        for (Block original : document.blocks()) {
            if (original instanceof Instruction && actions.contains(original)) {
                bounded.add(original);
                continue;
            }
            if (exhausted) continue;
            Block block = original instanceof Instruction ? paragraph("…") : original;
            int separator = used == 0 ? 0 : 2;
            int available = contentBudget - used - separator;
            if (available <= 0) { exhausted = true; continue; }
            Block selected = fit(block, available);
            bounded.add(selected);
            used += separator + selected.plainText().length();
            exhausted = selected != block;
        }
        if (boundedTitle.isEmpty() && bounded.isEmpty()) bounded.add(paragraph("…"));
        return new SocialDocument(document.moderation(), boundedTitle, document.tone(), bounded, document.language());
    }

    private static Block fit(Block block, int budget) {
        if (block.plainText().length() <= budget) return block;
        return switch (block) {
            case Paragraph paragraph -> paragraph(limitText(paragraph.text(), budget));
            case Fields fields -> {
                List<Field> kept = new ArrayList<>();
                int used = 0;
                for (Field field : fields.fields()) {
                    int cost = field.label().length() + 2 + field.value().length() + (kept.isEmpty() ? 0 : 1);
                    if (used + cost + 2 > budget) break;
                    kept.add(field);
                    used += cost;
                }
                // Never cut an identifier, metric or command in the middle.
                yield kept.isEmpty() ? paragraph("…") : new Fields(kept, true);
            }
            case ItemList list -> {
                List<String> kept = new ArrayList<>();
                int used = 0;
                for (String item : list.items()) {
                    int prefix = list.ordered() ? Integer.toString(kept.size() + 1).length() + 2 : 2;
                    int cost = prefix + item.length() + (kept.isEmpty() ? 0 : 1);
                    int marker = (list.ordered() ? Integer.toString(kept.size() + 2).length() + 2 : 2) + 2;
                    if (used + cost + marker > budget) break;
                    kept.add(item);
                    used += cost;
                }
                int marker = (list.ordered() ? Integer.toString(kept.size() + 1).length() + 2 : 2) + 1;
                if (budget >= used + marker + (kept.isEmpty() ? 0 : 1)) kept.add("…");
                yield kept.isEmpty() ? paragraph("…") : new ItemList(list.ordered(), kept);
            }
            case Image _, Instruction _ -> paragraph("…");
        };
    }

    private static String limitText(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        int end = Math.max(0, maximumLength - 1);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end).stripTrailing() + '…';
    }
}
