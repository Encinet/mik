package org.encinet.mik.module.ai.tool.utility;

import java.util.Locale;

/** Small deterministic parser; it evaluates arithmetic without executing code. */
final class ExpressionCalculator {
    private final String expression;
    private int index;

    private ExpressionCalculator(String expression) {
        this.expression = expression;
    }

    static double evaluate(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > 300) {
            throw new IllegalArgumentException("expression must contain 1-300 characters");
        }
        ExpressionCalculator parser = new ExpressionCalculator(expression);
        double value = parser.expression();
        parser.whitespace();
        if (parser.index != expression.length()) {
            throw new IllegalArgumentException(
                    "unexpected token at character " + (parser.index + 1));
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("result is not finite");
        }
        return value;
    }

    private double expression() {
        double value = term();
        while (true) {
            if (take('+')) {
                value += term();
            } else if (take('-')) {
                value -= term();
            } else {
                return value;
            }
        }
    }

    private double term() {
        double value = power();
        while (true) {
            if (take('*')) {
                value *= power();
            } else if (take('/')) {
                value /= power();
            } else if (take('%')) {
                value %= power();
            } else {
                return value;
            }
        }
    }

    private double power() {
        double value = unary();
        if (take('^')) {
            value = Math.pow(value, power());
        }
        return value;
    }

    private double unary() {
        if (take('+')) {
            return unary();
        }
        if (take('-')) {
            return -unary();
        }
        return primary();
    }

    private double primary() {
        if (take('(')) {
            double value = expression();
            require(')');
            return value;
        }
        whitespace();
        if (index < expression.length()
                && (Character.isLetter(expression.charAt(index)))) {
            String identifier = identifier();
            if ("pi".equals(identifier)) {
                return Math.PI;
            }
            if ("e".equals(identifier)) {
                return Math.E;
            }
            require('(');
            double argument = expression();
            require(')');
            return function(identifier, argument);
        }
        return number();
    }

    private double number() {
        whitespace();
        int start = index;
        boolean exponent = false;
        while (index < expression.length()) {
            char character = expression.charAt(index);
            if (Character.isDigit(character) || character == '.') {
                index++;
            } else if ((character == 'e' || character == 'E') && !exponent) {
                exponent = true;
                index++;
                if (index < expression.length()
                        && (expression.charAt(index) == '+' || expression.charAt(index) == '-')) {
                    index++;
                }
            } else {
                break;
            }
        }
        if (start == index) {
            throw new IllegalArgumentException(
                    "number expected at character " + (index + 1));
        }
        try {
            return Double.parseDouble(expression.substring(start, index));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid number", error);
        }
    }

    private String identifier() {
        int start = index;
        while (index < expression.length()
                && Character.isLetter(expression.charAt(index))) {
            index++;
        }
        return expression.substring(start, index).toLowerCase(Locale.ROOT);
    }

    private static double function(String name, double value) {
        return switch (name) {
            case "sqrt" -> Math.sqrt(value);
            case "abs" -> Math.abs(value);
            case "sin" -> Math.sin(value);
            case "cos" -> Math.cos(value);
            case "tan" -> Math.tan(value);
            case "ln" -> Math.log(value);
            case "log" -> Math.log10(value);
            case "floor" -> Math.floor(value);
            case "ceil" -> Math.ceil(value);
            case "round" -> Math.rint(value);
            default -> throw new IllegalArgumentException("unknown function: " + name);
        };
    }

    private boolean take(char expected) {
        whitespace();
        if (index < expression.length() && expression.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!take(expected)) {
            throw new IllegalArgumentException("'" + expected
                    + "' expected at character " + (index + 1));
        }
    }

    private void whitespace() {
        while (index < expression.length()
                && Character.isWhitespace(expression.charAt(index))) {
            index++;
        }
    }
}
