package org.apache.nifi.processor.email.extraction;

import org.apache.avro.Schema;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;

import java.util.List;
import java.util.Map;

/**
 * An extracted email message.
 *
 * <p>This and the other records in this package replace the Avro classes that used to be
 * generated from .avsc files in a separate module. Avro's reflect API induces the schema from
 * the declared fields of a plain class, which makes these definitions the single source of
 * truth: {@code ReflectData} reads the fields directly, so no accessors are involved and
 * nothing has to be kept in step with a separate schema file.
 *
 * <p>Two consequences worth knowing. Fields are named to match the wire format rather than Java
 * convention, because the field name becomes the Avro field name. And {@code ReflectData} orders
 * the fields of the induced schema alphabetically, which is not the order the old .avsc files
 * declared; the records are addressed by name, so only the ordering in the emitted schema
 * differs.
 *
 * @see SchemaExporter for the .avsc copies written out at build time
 */
public class EmailMessage {
    public static final Schema SCHEMA = ReflectData.get().getSchema(EmailMessage.class);

    List<BodyRecord> bodies;
    String folder;
    @Nullable
    String message_id;
    List<SenderReceiverDetails> recipients;
    SenderReceiverDetails sender_details;
    String subject;
    @Nullable
    String in_reply_to;
    Map<String, String> headers;
    @Nullable
    List<Map<String, String>> attachments;
}
