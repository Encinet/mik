package org.encinet.mik.module.ai.tool.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolPack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Deterministic local tools that do not access the network or Bukkit. */
public final class UtilityToolPack {
    private UtilityToolPack() {
    }

    public static AiToolPack create() {
        JsonObject expression = new JsonObject();
        expression.addProperty("type", "string");
        expression.addProperty("description",
                "Arithmetic expression using + - * / % ^, parentheses, pi, e, and "
                        + "sqrt/abs/sin/cos/tan/ln/log/floor/ceil/round");
        JsonObject properties = new JsonObject();
        properties.add("expression", expression);
        JsonArray required = new JsonArray();
        required.add("expression");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        AiTool calculator = new LocalTool("calculate",
                "Evaluate a deterministic arithmetic expression locally.", schema);
        return new AiToolPack("utility",
                "Deterministic local calculations with no network or game-state access.",
                List.of("calculate", "calculator", "math", "计算", "計算", "数学",
                        "算数", "계산", "calcul", "rechnen", "calcular"),
                List.of(calculator));
    }

    private record LocalTool(String name, String description, JsonObject parameters)
            implements AiTool {
        private LocalTool {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            parameters = parameters.deepCopy();
        }

        @Override
        public JsonObject parameters() {
            return parameters.deepCopy();
        }

        @Override
        public CompletableFuture<String> execute(JsonObject arguments) {
            JsonElement value = arguments.get("expression");
            if (value == null || !value.isJsonPrimitive()) {
                return CompletableFuture.completedFuture(error(
                        "invalid_expression", "expression must be a string"));
            }
            String expression = value.getAsString();
            try {
                double answer = ExpressionCalculator.evaluate(expression);
                JsonObject result = new JsonObject();
                result.addProperty("ok", true);
                result.addProperty("expression", expression);
                result.addProperty("result", answer);
                return CompletableFuture.completedFuture(result.toString());
            } catch (IllegalArgumentException error) {
                return CompletableFuture.completedFuture(error(
                        "invalid_expression", error.getMessage()));
            }
        }

        private static String error(String code, String message) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", code);
            result.addProperty("message", message);
            return result.toString();
        }
    }
}
