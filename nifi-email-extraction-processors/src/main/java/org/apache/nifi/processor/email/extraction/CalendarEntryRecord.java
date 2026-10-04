package org.apache.nifi.processor.email.extraction;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroSchema;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;

import java.util.List;

/**
 * A calendar entry extracted from an iCalendar stream. See {@link EmailMessage} for how the
 * schema is derived.
 *
 * <p>The three date fields carry an explicit schema because Avro's reflect API maps a bare
 * {@code long} to a plain Avro long. The processors write epoch milliseconds, so the fields are
 * annotated to keep the timestamp-millis logical type the .avsc files used to declare.
 */
public class CalendarEntryRecord {
    private static final String TIMESTAMP_MILLIS = "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}";
    private static final String NULLABLE_TIMESTAMP_MILLIS = "[\"null\"," + TIMESTAMP_MILLIS + "]";

    public static final Schema SCHEMA = ReflectData.get().getSchema(CalendarEntryRecord.class);

    String name;
    @Nullable
    String description;
    @Nullable
    @AvroSchema(NULLABLE_TIMESTAMP_MILLIS)
    Long date_created;
    @AvroSchema(TIMESTAMP_MILLIS)
    long start_date;
    @AvroSchema(TIMESTAMP_MILLIS)
    long end_date;
    @Nullable
    String summary;
    @Nullable
    String url;
    List<String> attendees;
}
