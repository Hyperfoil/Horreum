package io.hyperfoil.tools.horreum.action;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.svc.Util;

public final class ActionUtil {
    private static final Pattern FIND_EXPRESSIONS = Pattern.compile("\\$\\{([^}]*)\\}");

    private ActionUtil() {
    }

    static String replaceExpressions(String input, JsonNode payload) {
        if (input == null) {
            return null;
        }
        Matcher matcher = FIND_EXPRESSIONS.matcher(input);
        StringBuilder replaced = new StringBuilder();
        int lastMatch = 0;
        while (matcher.find()) {
            replaced.append(input, lastMatch, matcher.start());
            String path = matcher.group(1).trim();
            replaced.append(Util.findJsonPath(payload, path));
            lastMatch = matcher.end();
        }
        replaced.append(input.substring(lastMatch));
        return replaced.toString();
    }
}
