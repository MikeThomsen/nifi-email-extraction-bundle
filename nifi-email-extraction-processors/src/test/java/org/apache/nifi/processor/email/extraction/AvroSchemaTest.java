package org.apache.nifi.processor.email.extraction;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record classes induce their schemas from their fields, which means a change to a field
 * silently changes the schema the processors write. These tests pin the schemas against the
 * .avsc files the generated model module used to produce, so that the move away from code
 * generation cannot quietly alter the contract.
 */
public class AvroSchemaTest {
    /**
     * Reduces a schema to names and types, dropping the things that are allowed to differ:
     * the avro.java.string hint the old code generator emitted, the java-class hint the reflect
     * API emits for collection fields, defaults, and the order of the fields.
     */
    private static String signature(Schema schema) {
        switch (schema.getType()) {
            case RECORD:
                return schema.getFullName() + "{" + schema.getFields().stream()
                        .sorted((a, b) -> a.name().compareTo(b.name()))
                        .map(f -> f.name() + ":" + signature(f.schema()))
                        .collect(Collectors.joining(",")) + "}";
            case UNION:
                return "union[" + schema.getTypes().stream()
                        .map(AvroSchemaTest::signature)
                        .sorted()
                        .collect(Collectors.joining("|")) + "]";
            case ARRAY:
                return "array<" + signature(schema.getElementType()) + ">";
            case MAP:
                return "map<" + signature(schema.getValueType()) + ">";
            case ENUM:
                return "enum " + schema.getFullName() + schema.getEnumSymbols();
            default:
                LogicalType logical = schema.getLogicalType();
                return schema.getType().getName() + (logical == null ? "" : "/" + logical.getName());
        }
    }

    private static Schema legacySchema(String name) throws IOException {
        try (InputStream is = AvroSchemaTest.class.getResourceAsStream("/legacy-avro/" + name + ".avsc")) {
            assertNotNull(is, "Missing legacy schema for " + name);
            return new Schema.Parser().parse(is);
        }
    }

    @Test
    public void testSchemasMatchTheLegacyGeneratedModel() throws Exception {
        List<String> checked = new ArrayList<>();
        for (Map.Entry<String, Schema> entry : SchemaExporter.getSchemas().entrySet()) {
            Schema legacy = legacySchema(entry.getKey());
            assertEquals(signature(legacy), signature(entry.getValue()),
                    entry.getKey() + " no longer matches the schema the model module generated");
            checked.add(entry.getKey());
        }
        assertEquals(List.of("EmailMessage", "CalendarEntryRecord", "SenderReceiverDetails"), checked);
    }

    @Test
    public void testSchemasKeepTheirFullNames() {
        assertEquals("org.apache.nifi.processor.email.extraction.EmailMessage", EmailMessage.SCHEMA.getFullName());
        assertEquals("org.apache.nifi.processor.email.extraction.CalendarEntryRecord",
                CalendarEntryRecord.SCHEMA.getFullName());
        assertEquals("org.apache.nifi.processor.email.extraction.SenderReceiverDetails",
                SenderReceiverDetails.SCHEMA.getFullName());
    }

    /**
     * A bare long would lose the logical type, which is what the @AvroSchema annotations on
     * CalendarEntryRecord exist to prevent.
     */
    @Test
    public void testCalendarDatesKeepTheTimestampLogicalType() {
        for (String field : List.of("start_date", "end_date")) {
            Schema schema = CalendarEntryRecord.SCHEMA.getField(field).schema();
            assertEquals(LogicalTypes.timestampMillis(), schema.getLogicalType(), field);
        }

        Schema created = CalendarEntryRecord.SCHEMA.getField("date_created").schema();
        assertEquals(Schema.Type.UNION, created.getType());
        assertEquals(LogicalTypes.timestampMillis(),
                created.getTypes().get(1).getLogicalType(), "date_created");
    }

    @Test
    public void testOptionalFieldsAreNullableUnions() {
        for (String field : List.of("message_id", "in_reply_to", "attachments")) {
            Schema schema = EmailMessage.SCHEMA.getField(field).schema();
            assertEquals(Schema.Type.UNION, schema.getType(), field);
            assertEquals(Schema.Type.NULL, schema.getTypes().get(0).getType(),
                    field + " should offer null as the first branch");
        }
    }

    @Test
    public void testRequiredFieldsAreNotNullable() {
        for (String field : List.of("bodies", "folder", "recipients", "sender_details", "subject", "headers")) {
            Schema schema = EmailMessage.SCHEMA.getField(field).schema();
            assertTrue(schema.getType() != Schema.Type.UNION, field + " should not be nullable");
        }
    }

    /**
     * The .avsc copies published by the build have to be the schemas the processors actually
     * write, not a stale snapshot.
     */
    @Test
    public void testExportedFilesRoundTripToTheSameSchemas(@TempDir Path dir) throws Exception {
        SchemaExporter.export(dir);

        for (Map.Entry<String, Schema> entry : SchemaExporter.getSchemas().entrySet()) {
            Path file = dir.resolve(entry.getKey() + ".avsc");
            assertTrue(Files.exists(file), "Expected " + file + " to be exported");
            assertEquals(entry.getValue(), new Schema.Parser().parse(file.toFile()),
                    entry.getKey() + ".avsc does not round trip");
        }
    }

    @Test
    public void testExportedSchemasAreTheOnesTheProcessorsUse() {
        Map<String, Schema> exported = SchemaExporter.getSchemas();
        assertEquals(EmailMessage.SCHEMA, exported.get("EmailMessage"));
        assertEquals(CalendarEntryRecord.SCHEMA, exported.get("CalendarEntryRecord"));
        assertEquals(SenderReceiverDetails.SCHEMA, exported.get("SenderReceiverDetails"));
    }
}
