package fr.hoteldeville.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Couleurs (&c, &#RRGGBB, <#RRGGBB>, #RRGGBB), durées et formats. */
public final class Text {

    private static final Pattern HEX_BRACKET = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern HEX_BARE = Pattern.compile("(?<![&\\w])#([A-Fa-f0-9]{6})");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private static String unitDay = "j", unitHour = "h", unitMinute = "min", unitSecond = "s";
    private static DecimalFormat numberFormat = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.FRANCE));

    private Text() {}

    public static void setUnits(String day, String hour, String minute, String second) {
        unitDay = day;
        unitHour = hour;
        unitMinute = minute;
        unitSecond = second;
    }

    public static void setNumberFormat(String pattern) {
        try {
            numberFormat = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.FRANCE));
        } catch (IllegalArgumentException ignored) {
            numberFormat = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        }
    }

    public static String number(double value) {
        return numberFormat.format(value);
    }

    /** Convertit une chaîne colorée en Component. */
    public static Component color(String input) {
        if (input == null) return Component.empty();
        String s = input.replace('§', '&');
        s = HEX_BRACKET.matcher(s).replaceAll("&#$1");
        s = HEX_BARE.matcher(s).replaceAll("&#$1");
        return SERIALIZER.deserialize(s);
    }

    /** Component pour nom/lore d'item : italique désactivé par défaut. */
    public static Component item(String input) {
        return color(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemList(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) out.add(item(line));
        return out;
    }

    /** Remplace des paires {clé} -> valeur. */
    public static String rep(String s, String... kv) {
        if (s == null) return "";
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return s;
    }

    public static List<String> rep(List<String> lines, String... kv) {
        List<String> out = new ArrayList<>();
        for (String l : lines) out.add(rep(l, kv));
        return out;
    }

    /** "1d12h30m15s" ou "90" (secondes) -> secondes. */
    public static long parseSeconds(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return 0;
        if (s.matches("\\d+")) return Long.parseLong(s);
        Matcher m = DURATION.matcher(s);
        long total = 0;
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            switch (m.group(2).charAt(0)) {
                case 'd' -> total += v * 86400;
                case 'h' -> total += v * 3600;
                case 'm' -> total += v * 60;
                default -> total += v;
            }
        }
        return total;
    }

    public static String formatDuration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(unitDay).append(' ');
        if (h > 0) sb.append(h).append(unitHour).append(' ');
        if (m > 0) sb.append(m).append(unitMinute).append(' ');
        if (s > 0 || sb.length() == 0) sb.append(s).append(unitSecond);
        return sb.toString().trim();
    }

    /** "NETHERITE_INGOT" -> "Netherite Ingot". */
    public static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.toLowerCase(Locale.ROOT).replace(':', ' ').split("[_ ]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
