package uk.gemwire.camelot.util;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class DateUtils {
    public static String formatDuration(Duration duration) {
        return "some time";
    }

    private static List<String> splitInput(String str) {
        final var list = new ArrayList<String>();
        var builder = new StringBuilder();
        for (final var ch : str.toCharArray()) {
            builder.append(ch);
            if (!Character.isDigit(ch)) {
                list.add(builder.toString());
                builder = new StringBuilder();
            }
        }
        return list;
    }

    /**
     * Decodes a duration from an {@code input}, supporting multiple time specifiers (e.g. {@code 1w2d}).
     */
    public static Duration getDurationFromInput(String input) {
        final List<String> data = splitInput(input);
        Duration duration = Duration.ofSeconds(0);
        for (final String dt : data) {
            duration = duration.plusSeconds(decode(dt).toSeconds());
        }
        return duration;
    }

    /**
     * Decodes time from a string.
     *
     * @param time the time to decode
     * @return the decoded time.
     */
    public static Duration decode(@Nonnull final String time) {
        final var unit = switch (time.charAt(time.length() - 1)) {
            case 'n' -> ChronoUnit.NANOS;
            case 'l' -> ChronoUnit.MILLIS;
            case 's' -> ChronoUnit.SECONDS;
            case 'h' -> ChronoUnit.HOURS;
            case 'd' -> ChronoUnit.DAYS;
            case 'w' -> ChronoUnit.WEEKS;
            case 'M' -> ChronoUnit.MONTHS;
            case 'y' -> ChronoUnit.YEARS;
            default -> ChronoUnit.MINUTES;
        };
        final long tm = Long.parseLong(time.substring(0, time.length() - 1));
        return Duration.of(tm, unit);
    }
}
