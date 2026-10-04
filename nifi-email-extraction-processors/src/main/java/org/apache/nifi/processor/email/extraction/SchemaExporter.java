package org.apache.nifi.processor.email.extraction;

import org.apache.avro.Schema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the schemas induced from the record classes in this package out as .avsc files. The
 * build runs this so that the schemas are published alongside the processors, both inside the
 * jar and as a separate avro-schemas artifact, which gives anything downstream a copy to read
 * without having to depend on this module.
 *
 * <p>The schemas are derived from the classes rather than the other way around, so the files
 * this produces are generated output and are not kept in the repository.
 */
public final class SchemaExporter {
    private static final Map<String, Schema> SCHEMAS = schemas();

    private SchemaExporter() {
    }

    private static Map<String, Schema> schemas() {
        // Linked so the files are written in a stable order.
        Map<String, Schema> schemas = new LinkedHashMap<>();
        schemas.put("EmailMessage", EmailMessage.SCHEMA);
        schemas.put("CalendarEntryRecord", CalendarEntryRecord.SCHEMA);
        schemas.put("SenderReceiverDetails", SenderReceiverDetails.SCHEMA);

        return schemas;
    }

    /**
     * Returns the exported schemas keyed by the base name of the file each one is written to.
     * Exposed so that tests can assert the exported set matches what the processors use.
     */
    public static Map<String, Schema> getSchemas() {
        return SCHEMAS;
    }

    public static void export(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (Map.Entry<String, Schema> entry : SCHEMAS.entrySet()) {
            Path target = directory.resolve(entry.getKey() + ".avsc");
            Files.writeString(target, entry.getValue().toString(true) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: SchemaExporter <output directory>");
        }

        Path directory = Paths.get(args[0]);
        export(directory);
        System.out.printf("Exported %d Avro schemas to %s%n", SCHEMAS.size(), directory.toAbsolutePath());
    }
}
