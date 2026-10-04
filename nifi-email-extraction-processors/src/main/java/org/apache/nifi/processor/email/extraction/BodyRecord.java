package org.apache.nifi.processor.email.extraction;

/**
 * One body of an email message. See {@link EmailMessage} for how the schema is derived.
 */
public class BodyRecord {
    String body;
    BodyType body_type;
}
