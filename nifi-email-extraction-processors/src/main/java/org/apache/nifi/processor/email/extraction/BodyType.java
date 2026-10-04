package org.apache.nifi.processor.email.extraction;

/**
 * The forms an email body can take. Avro's reflect API induces an enum schema from this.
 */
public enum BodyType {
    HTML,
    PLAIN,
    RTF
}
