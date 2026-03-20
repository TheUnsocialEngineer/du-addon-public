/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.StringHelper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.jackywacky.addon.DupersUnitedPublicAddon;

/**
 * Listens for chat game messages (e.g. ChatGames plugin maths puzzles) and
 * sends the correct answer as fast as possible to win.
 */
public class ChatGames extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> mathsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("maths-only")
        .description("Only answer maths puzzles; ignore trivia, unscramble, reaction, etc.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wordGames = sgGeneral.add(new BoolSetting.Builder()
        .name("word-games")
        .description("Answer 'write out the word' challenges (e.g. CHAT GAMES prompts).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Log when an answer was sent (client-side).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Minimum seconds between sending answers (stops feedback loop / spam).")
        .defaultValue(2)
        .min(1)
        .max(30)
        .sliderRange(1, 10)
        .build()
    );

    /** Last time we sent an answer (for cooldown). */
    private long lastAnswerTime;

    /** Strips Minecraft color codes and finds a substring that looks like a math expression. */
    private static final Pattern MATH_CANDIDATE = Pattern.compile(
        "\\d+(?:\\.\\d+)?(?:\\s*[+\\-*/]\\s*(?:\\d+(?:\\.\\d+)?|\\([\\d\\s+\\-*/.()]+\\))\\s*)*"
    );

    /** Safe chars for expression (digits, operators, parentheses, spaces, dot). */
    private static final Pattern SAFE_EXPR = Pattern.compile("^[\\d\\s+\\-*/().]+$");

    /** Matches "write out the word: 'X'" for any word X in single or double quotes (case insensitive). */
    private static final Pattern WRITE_OUT_WORD = Pattern.compile(
        "write\\s+out\\s+the\\s+word\\s*:?\\s*['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    public ChatGames() {
        super(DupersUnitedPublicAddon.CATEGORY_UTILITIES, "chat-games", "Listens for chat game messages (e.g. ChatGames plugin) and answers maths puzzles and word challenges as fast as possible.");
    }

    @EventHandler(priority = 1000) // run early so we react before other handlers
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String raw = event.getMessage().getString();
        String plain = StringHelper.stripTextFormat(raw);

        // Never treat our own feedback as a new puzzle (stops feedback loop when chat-feedback is on)
        if (plain.contains("Answered:") || plain.contains("Sent word:")) return;

        // "Write out the word" challenges (e.g. "You have 20 seconds to write out the word: 'Reply'" — works for any word in quotes)
        if (wordGames.get()) {
            String word = extractWriteOutWord(plain);
            if (word != null) {
                if (!tryConsumeCooldown()) return;
                ChatUtils.sendPlayerMsg(word, false);
                if (chatFeedback.get()) info("Sent word: %s", word);
                return;
            }
        }

        if (!mathsOnly.get()) return; // no maths requested; word game already handled above
        // Try "solve for x" symbol puzzles (e.g. ⭐+⭐+⭐=30, 🎵+🎵+🎵=75, ⭐+🎵+x=89)
        Double symbolX = trySolveForX(plain);
        if (symbolX != null) {
            if (!tryConsumeCooldown()) return;
            String answer = formatAnswer(symbolX);
            ChatUtils.sendPlayerMsg(answer, false);
            if (chatFeedback.get()) info("Solved for x: %s", answer);
            return;
        }

        // Fallback: plain arithmetic expression (e.g. "What is 5 + 3?")
        String expr = extractMathExpression(plain);
        if (expr == null) return;

        Double result = evaluateMath(expr);
        if (result == null) return;

        if (!tryConsumeCooldown()) return;
        String answer = formatAnswer(result);
        ChatUtils.sendPlayerMsg(answer, false);
        if (chatFeedback.get()) info("Answered: %s = %s", expr.trim(), answer);
    }

    /** Returns true if cooldown has passed (and consumes it); false to skip sending. */
    private boolean tryConsumeCooldown() {
        long now = System.currentTimeMillis();
        if (lastAnswerTime != 0 && (now - lastAnswerTime) < cooldown.get() * 1000L) return false;
        lastAnswerTime = now;
        return true;
    }

    /**
     * If the message is a "write out the word" challenge, returns the quoted word to send (any word in single or double quotes); otherwise null.
     */
    private String extractWriteOutWord(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = WRITE_OUT_WORD.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Finds the first substring that looks like a math expression (contains numbers and operators).
     */
    private String extractMathExpression(String text) {
        if (text == null || text.length() < 3) return null;
        Matcher m = MATH_CANDIDATE.matcher(text);
        while (m.find()) {
            String candidate = m.group();
            if (!SAFE_EXPR.matcher(candidate).matches()) continue;
            if (!candidate.matches(".*[+\\-*/].*")) continue;
            if (candidate.length() > 120) continue; // avoid huge expressions
            return candidate;
        }
        return null;
    }

    /**
     * Evaluates a simple math expression: numbers, + - * / and parentheses.
     * Returns null if invalid or unsafe.
     */
    private Double evaluateMath(String expr) {
        if (expr == null || expr.isEmpty()) return null;
        expr = expr.trim();
        try {
            return new ExprParser(expr).parse();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatAnswer(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        String s = String.valueOf(value);
        if (s.contains("E")) return s;
        int i = s.indexOf('.');
        if (i >= 0) {
            int j = s.length() - 1;
            while (j > i && s.charAt(j) == '0') j--;
            if (j == i) return s.substring(0, i);
            return s.substring(0, j + 1);
        }
        return s;
    }

    /** Finds each "LHS = number" in text (works across lines or in one line). */
    private static final Pattern EQUATION_PAIR = Pattern.compile("([^=]+?)\\s*=\\s*(-?\\d+(?:\\.\\d+)?)(?:\\s|$|\n)");

    /**
     * Tries to parse "solve for x" style puzzles: multiple equations with symbols (e.g. ⭐, 🎵)
     * and variable x. Example: ⭐+⭐+⭐=30, 🎵+🎵+🎵=75, ⭐+🎵+x=89 → x=54.
     * Returns the value of x, or null if not this format / unsolvable.
     */
    private Double trySolveForX(String text) {
        if (text == null || text.length() < 10) return null;
        String lower = text.toLowerCase();
        if (!lower.contains("solve for") || !text.contains("=")) return null;
        // Collect all "LHS = number" pairs (handles newlines or multiple on one line)
        List<Equation> equations = new ArrayList<>();
        Matcher matcher = EQUATION_PAIR.matcher(text);
        while (matcher.find()) {
            String lhs = matcher.group(1).trim();
            double rhs = Double.parseDouble(matcher.group(2));
            if (lhs.isEmpty()) continue;
            String[] parts = lhs.split("\\s*\\+\\s*");
            List<String> symbols = new ArrayList<>();
            for (String p : parts) {
                String sym = p.trim();
                if (sym.isEmpty()) continue;
                symbols.add(sym);
            }
            if (symbols.isEmpty()) continue;
            equations.add(new Equation(symbols, rhs));
        }
        if (equations.isEmpty()) return null;

        Map<String, Double> known = new HashMap<>();
        boolean hasX = equations.stream().anyMatch(eq -> eq.symbols.stream().anyMatch(s -> isVariableX(s)));
        if (!hasX) return null;

        // Solve by substitution: resolve same-symbol equations first, then equation containing x
        for (int round = 0; round < equations.size() + 2; round++) {
            boolean progress = false;
            for (Equation eq : equations) {
                if (eq.symbols.size() == 1) {
                    String sym = eq.symbols.get(0);
                    if (!known.containsKey(sym)) {
                        known.put(sym, eq.rhs);
                        progress = true;
                    }
                    continue;
                }
                Set<String> unique = new HashSet<>(eq.symbols);
                if (unique.size() == 1) {
                    String sym = unique.iterator().next();
                    if (!known.containsKey(sym)) {
                        known.put(sym, eq.rhs / eq.symbols.size());
                        progress = true;
                    }
                    continue;
                }
                // Equation has multiple symbol types; if it contains x and rest known, solve for x
                if (eq.symbols.stream().noneMatch(ChatGames::isVariableX)) continue;
                double sumKnown = 0;
                int xCount = 0;
                boolean allOthersKnown = true;
                for (String s : eq.symbols) {
                    if (isVariableX(s)) {
                        xCount++;
                    } else {
                        Double v = known.get(s);
                        if (v == null) {
                            allOthersKnown = false;
                            break;
                        }
                        sumKnown += v;
                    }
                }
                if (xCount != 1 || !allOthersKnown) continue;
                double x = eq.rhs - sumKnown;
                if (!Double.isFinite(x)) continue;
                return x;
            }
            if (!progress) break;
        }
        return null;
    }

    private static boolean isVariableX(String s) {
        return "x".equalsIgnoreCase(s.trim());
    }

    private static final class Equation {
        final List<String> symbols;
        final double rhs;

        Equation(List<String> symbols, double rhs) {
            this.symbols = symbols;
            this.rhs = rhs;
        }
    }

    /**
     * Minimal recursive-descent parser for + - * / ( ) and numbers.
     * No script engine; fast and safe.
     */
    private static final class ExprParser {
        private final String s;
        private int i;

        ExprParser(String s) {
            this.s = s;
            this.i = 0;
        }

        double parse() {
            double v = parseExpr();
            if (i < s.length()) throw new IllegalArgumentException("leftover: " + s.substring(i));
            return v;
        }

        private double parseExpr() {
            double left = parseTerm();
            while (i < s.length()) {
                skipSpaces();
                if (i >= s.length()) break;
                char c = s.charAt(i);
                if (c == '+') {
                    i++;
                    left += parseTerm();
                } else if (c == '-') {
                    i++;
                    left -= parseTerm();
                } else break;
            }
            return left;
        }

        private double parseTerm() {
            double left = parseFactor();
            while (i < s.length()) {
                skipSpaces();
                if (i >= s.length()) break;
                char c = s.charAt(i);
                if (c == '*') {
                    i++;
                    left *= parseFactor();
                } else if (c == '/') {
                    i++;
                    double right = parseFactor();
                    if (right == 0) throw new ArithmeticException("division by zero");
                    left /= right;
                } else break;
            }
            return left;
        }

        private double parseFactor() {
            skipSpaces();
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            if (s.charAt(i) == '(') {
                i++;
                double v = parseExpr();
                skipSpaces();
                if (i >= s.length() || s.charAt(i) != ')') throw new IllegalArgumentException("missing )");
                i++;
                return v;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (i == start) throw new IllegalArgumentException("expected number at " + start);
            return Double.parseDouble(s.substring(start, i).trim());
        }

        private void skipSpaces() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
