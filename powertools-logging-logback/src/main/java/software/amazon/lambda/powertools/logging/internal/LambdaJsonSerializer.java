package software.amazon.lambda.powertools.logging.internal;

import ch.qos.logback.classic.Level;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

import static software.amazon.lambda.powertools.logging.internal.JsonUtils.serializeAttribute;

/**
 * This class will serialize the log events in json.<br/>
 *
 * Inspired from the ElasticSearch Serializer {@link co.elastic.logging.EcsJsonSerializer}, this class doesn't use
 * any JSON (de)serialization library (Jackson, Gson, etc.) to avoid the dependency
 */
public class LambdaJsonSerializer {
    protected static final String TIMESTAMP_ATTR_NAME = "timestamp";
    protected static final String LEVEL_ATTR_NAME = "level";
    protected static final String FORMATTED_MESSAGE_ATTR_NAME = "message";
    protected static final String THREAD_ATTR_NAME = "thread";
    protected static final String THREAD_ID_ATTR_NAME = "thread_id";
    protected static final String THREAD_PRIORITY_ATTR_NAME = "thread_priority";
    protected static final String EXCEPTION_MSG_ATTR_NAME = "message";
    protected static final String EXCEPTION_CLASS_ATTR_NAME = "name";
    protected static final String EXCEPTION_STACK_ATTR_NAME = "stack";
    protected static final String EXCEPTION_ATTR_NAME = "error";


    public static void serializeObjectStart(StringBuilder builder) {
        builder.append('{');
    }

    public static void serializeObjectEnd(StringBuilder builder) {
        builder.append("}\n");
    }

    public static void serializeTimestamp(StringBuilder builder, long timestamp, String timestampFormat, String timestampFormatTimezoneId) {
        String formattedTimestamp;
        if (timestampFormat == null || timestamp < 0) {
            formattedTimestamp = String.valueOf(timestamp);
        } else {
            Date date = new Date(timestamp);
            DateFormat format = new SimpleDateFormat(timestampFormat);

            if (timestampFormatTimezoneId != null) {
                TimeZone tz = TimeZone.getTimeZone(timestampFormatTimezoneId);
                format.setTimeZone(tz);
            }
            formattedTimestamp = format.format(date);
        }
        serializeAttribute(builder, TIMESTAMP_ATTR_NAME, formattedTimestamp);
    }

    public static void serializeThreadName(StringBuilder builder, String threadName) {
        if (threadName != null) {
            serializeAttribute(builder, THREAD_ATTR_NAME, threadName);
        }
    }

    public static void serializeLogLevel(StringBuilder builder, Level level) {
        serializeAttribute(builder, LEVEL_ATTR_NAME, level.toString(), false);
    }

    public static void serializeFormattedMessage(StringBuilder builder, String formattedMessage) {
        serializeAttribute(builder, FORMATTED_MESSAGE_ATTR_NAME, formattedMessage.replaceAll("\"", Matcher.quoteReplacement("\\\"")));
    }

    public static void serializeException(StringBuilder builder, String className, String message, String stackTrace) {
        builder.append("\"").append(EXCEPTION_ATTR_NAME).append("\": {");
        serializeAttribute(builder, EXCEPTION_MSG_ATTR_NAME, message, false);
        serializeAttribute(builder, EXCEPTION_CLASS_ATTR_NAME, className);
        serializeAttribute(builder, EXCEPTION_STACK_ATTR_NAME, stackTrace);
        builder.append("},");
    }

    public static void serializeException(StringBuilder builder, Throwable throwable) {
        serializeException(builder, throwable.getClass().getName(), throwable.getMessage(), Arrays.toString(throwable.getStackTrace()));
    }

    public static void serializeThreadId(StringBuilder builder, String threadId) {
        serializeAttribute(builder, THREAD_ID_ATTR_NAME, threadId);
    }

    public static void serializeThreadPriority(StringBuilder builder, String threadPriority) {
        serializeAttribute(builder, THREAD_PRIORITY_ATTR_NAME, threadPriority);
    }

    public static void serializePowertools(StringBuilder builder, Map<String, String> mdc) {
        TreeMap<String, String> sortedMap = new TreeMap<>(mdc);
        sortedMap.forEach((k, v) ->
                serializeAttribute(builder, k, v));
    }

}
