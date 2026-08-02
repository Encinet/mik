package org.encinet.mik.module.geyser;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Class-loader-neutral description of a Bedrock SimpleForm. */
public record BedrockSimpleForm(
        String title,
        String content,
        List<String> buttons,
        IntConsumer selected,
        Runnable closed
) {

    public BedrockSimpleForm {
        title = Objects.requireNonNull(title, "title");
        content = Objects.requireNonNull(content, "content");
        buttons = List.copyOf(Objects.requireNonNull(buttons, "buttons"));
        if (buttons.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Bedrock form buttons must not contain null");
        }
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(closed, "closed");
    }
}
