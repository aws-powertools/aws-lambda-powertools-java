package software.amazon.lambda.powertools.logging.internal;

public class JsonUtils {

    protected static void serializeAttribute(StringBuilder builder, String attr, String value, boolean notBegin) {
        if (value != null) {
            if (notBegin) {
                builder.append(", ");
            }
            builder.append("\"").append(attr).append("\": ");
            boolean isString = isString(value);
            if (isString) builder.append("\"");
            builder.append(value);
            if (isString) builder.append("\"");
        }
    }

    protected static void serializeAttributeAsString(StringBuilder builder, String attr, String value, boolean notBegin) {
        if (value != null) {
            if (notBegin) {
                builder.append(", ");
            }
            builder.append("\"")
                    .append(attr)
                    .append("\": \"")
                    .append(value)
                    .append("\"");
        }
    }

    protected static void serializeAttribute(StringBuilder builder, String attr, String value) {
        serializeAttribute(builder, attr, value, true);
    }

    protected static void serializeAttributeAsString(StringBuilder builder, String attr, String value) {
        serializeAttributeAsString(builder, attr, value, true);
    }

    /**
     * As MDC is a Map<String, String>, we need to check the type to output numbers and booleans correctly (without quotes)
     */
    private static boolean isString(String str) {
        if (str == null) {
            return true;
        }
        if (str.equals("true") || str.equals("false")) {
            return false; // boolean
        }
        return !isNumeric(str); // number
    }

    /**
     * Taken from commons-lang3 NumberUtils to avoid include the library
     */
    private static boolean isNumeric(final String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        if (str.charAt(str.length() - 1) == '.') {
            return false;
        }
        if (str.charAt(0) == '-') {
            if (str.length() == 1) {
                return false;
            }
            return withDecimalsParsing(str, 1);
        }
        return withDecimalsParsing(str, 0);
    }

    /**
     * Taken from commons-lang3 NumberUtils
     */
    private static boolean withDecimalsParsing(final String str, final int beginIdx) {
        int decimalPoints = 0;
        for (int i = beginIdx; i < str.length(); i++) {
            final boolean isDecimalPoint = str.charAt(i) == '.';
            if (isDecimalPoint) {
                decimalPoints++;
            }
            if (decimalPoints > 1) {
                return false;
            }
            if (!isDecimalPoint && !Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
