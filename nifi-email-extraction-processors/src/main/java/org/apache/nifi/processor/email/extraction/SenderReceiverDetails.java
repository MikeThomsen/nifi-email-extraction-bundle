package org.apache.nifi.processor.email.extraction;

import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectData;

/**
 * The name and address of a sender or a recipient. See {@link EmailMessage} for how the schema
 * is derived.
 */
public class SenderReceiverDetails {
    public static final Schema SCHEMA = ReflectData.get().getSchema(SenderReceiverDetails.class);

    String name;
    String email_address;
}
