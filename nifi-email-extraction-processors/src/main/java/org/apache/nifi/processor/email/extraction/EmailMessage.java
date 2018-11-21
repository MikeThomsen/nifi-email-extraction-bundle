/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package org.apache.nifi.processor.email.extraction;

import org.apache.avro.specific.SpecificData;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class EmailMessage extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -8532262173106360039L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"EmailMessage\",\"namespace\":\"org.apache.nifi.processor.email.extraction\",\"fields\":[{\"name\":\"body\",\"type\":\"string\"},{\"name\":\"body_type\",\"type\":{\"type\":\"enum\",\"name\":\"BodyType\",\"symbols\":[\"HTML\",\"PLAIN\",\"RTF\"]}},{\"name\":\"folder\",\"type\":\"string\"},{\"name\":\"message_id\",\"type\":\"string\"},{\"name\":\"recipients\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"SenderReceiverDetails\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"email_address\",\"type\":\"string\"}]}}},{\"name\":\"sender_details\",\"type\":\"SenderReceiverDetails\"},{\"name\":\"subject\",\"type\":\"string\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<EmailMessage> ENCODER =
      new BinaryMessageEncoder<EmailMessage>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<EmailMessage> DECODER =
      new BinaryMessageDecoder<EmailMessage>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   */
  public static BinaryMessageDecoder<EmailMessage> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   */
  public static BinaryMessageDecoder<EmailMessage> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<EmailMessage>(MODEL$, SCHEMA$, resolver);
  }

  /** Serializes this EmailMessage to a ByteBuffer. */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /** Deserializes a EmailMessage from a ByteBuffer. */
  public static EmailMessage fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public java.lang.CharSequence body;
  @Deprecated public org.apache.nifi.processor.email.extraction.BodyType body_type;
  @Deprecated public java.lang.CharSequence folder;
  @Deprecated public java.lang.CharSequence message_id;
  @Deprecated public java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> recipients;
  @Deprecated public org.apache.nifi.processor.email.extraction.SenderReceiverDetails sender_details;
  @Deprecated public java.lang.CharSequence subject;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public EmailMessage() {}

  /**
   * All-args constructor.
   * @param body The new value for body
   * @param body_type The new value for body_type
   * @param folder The new value for folder
   * @param message_id The new value for message_id
   * @param recipients The new value for recipients
   * @param sender_details The new value for sender_details
   * @param subject The new value for subject
   */
  public EmailMessage(java.lang.CharSequence body, org.apache.nifi.processor.email.extraction.BodyType body_type, java.lang.CharSequence folder, java.lang.CharSequence message_id, java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> recipients, org.apache.nifi.processor.email.extraction.SenderReceiverDetails sender_details, java.lang.CharSequence subject) {
    this.body = body;
    this.body_type = body_type;
    this.folder = folder;
    this.message_id = message_id;
    this.recipients = recipients;
    this.sender_details = sender_details;
    this.subject = subject;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return body;
    case 1: return body_type;
    case 2: return folder;
    case 3: return message_id;
    case 4: return recipients;
    case 5: return sender_details;
    case 6: return subject;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: body = (java.lang.CharSequence)value$; break;
    case 1: body_type = (org.apache.nifi.processor.email.extraction.BodyType)value$; break;
    case 2: folder = (java.lang.CharSequence)value$; break;
    case 3: message_id = (java.lang.CharSequence)value$; break;
    case 4: recipients = (java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails>)value$; break;
    case 5: sender_details = (org.apache.nifi.processor.email.extraction.SenderReceiverDetails)value$; break;
    case 6: subject = (java.lang.CharSequence)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'body' field.
   * @return The value of the 'body' field.
   */
  public java.lang.CharSequence getBody() {
    return body;
  }

  /**
   * Sets the value of the 'body' field.
   * @param value the value to set.
   */
  public void setBody(java.lang.CharSequence value) {
    this.body = value;
  }

  /**
   * Gets the value of the 'body_type' field.
   * @return The value of the 'body_type' field.
   */
  public org.apache.nifi.processor.email.extraction.BodyType getBodyType() {
    return body_type;
  }

  /**
   * Sets the value of the 'body_type' field.
   * @param value the value to set.
   */
  public void setBodyType(org.apache.nifi.processor.email.extraction.BodyType value) {
    this.body_type = value;
  }

  /**
   * Gets the value of the 'folder' field.
   * @return The value of the 'folder' field.
   */
  public java.lang.CharSequence getFolder() {
    return folder;
  }

  /**
   * Sets the value of the 'folder' field.
   * @param value the value to set.
   */
  public void setFolder(java.lang.CharSequence value) {
    this.folder = value;
  }

  /**
   * Gets the value of the 'message_id' field.
   * @return The value of the 'message_id' field.
   */
  public java.lang.CharSequence getMessageId() {
    return message_id;
  }

  /**
   * Sets the value of the 'message_id' field.
   * @param value the value to set.
   */
  public void setMessageId(java.lang.CharSequence value) {
    this.message_id = value;
  }

  /**
   * Gets the value of the 'recipients' field.
   * @return The value of the 'recipients' field.
   */
  public java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> getRecipients() {
    return recipients;
  }

  /**
   * Sets the value of the 'recipients' field.
   * @param value the value to set.
   */
  public void setRecipients(java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> value) {
    this.recipients = value;
  }

  /**
   * Gets the value of the 'sender_details' field.
   * @return The value of the 'sender_details' field.
   */
  public org.apache.nifi.processor.email.extraction.SenderReceiverDetails getSenderDetails() {
    return sender_details;
  }

  /**
   * Sets the value of the 'sender_details' field.
   * @param value the value to set.
   */
  public void setSenderDetails(org.apache.nifi.processor.email.extraction.SenderReceiverDetails value) {
    this.sender_details = value;
  }

  /**
   * Gets the value of the 'subject' field.
   * @return The value of the 'subject' field.
   */
  public java.lang.CharSequence getSubject() {
    return subject;
  }

  /**
   * Sets the value of the 'subject' field.
   * @param value the value to set.
   */
  public void setSubject(java.lang.CharSequence value) {
    this.subject = value;
  }

  /**
   * Creates a new EmailMessage RecordBuilder.
   * @return A new EmailMessage RecordBuilder
   */
  public static org.apache.nifi.processor.email.extraction.EmailMessage.Builder newBuilder() {
    return new org.apache.nifi.processor.email.extraction.EmailMessage.Builder();
  }

  /**
   * Creates a new EmailMessage RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new EmailMessage RecordBuilder
   */
  public static org.apache.nifi.processor.email.extraction.EmailMessage.Builder newBuilder(org.apache.nifi.processor.email.extraction.EmailMessage.Builder other) {
    return new org.apache.nifi.processor.email.extraction.EmailMessage.Builder(other);
  }

  /**
   * Creates a new EmailMessage RecordBuilder by copying an existing EmailMessage instance.
   * @param other The existing instance to copy.
   * @return A new EmailMessage RecordBuilder
   */
  public static org.apache.nifi.processor.email.extraction.EmailMessage.Builder newBuilder(org.apache.nifi.processor.email.extraction.EmailMessage other) {
    return new org.apache.nifi.processor.email.extraction.EmailMessage.Builder(other);
  }

  /**
   * RecordBuilder for EmailMessage instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<EmailMessage>
    implements org.apache.avro.data.RecordBuilder<EmailMessage> {

    private java.lang.CharSequence body;
    private org.apache.nifi.processor.email.extraction.BodyType body_type;
    private java.lang.CharSequence folder;
    private java.lang.CharSequence message_id;
    private java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> recipients;
    private org.apache.nifi.processor.email.extraction.SenderReceiverDetails sender_details;
    private org.apache.nifi.processor.email.extraction.SenderReceiverDetails.Builder sender_detailsBuilder;
    private java.lang.CharSequence subject;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(org.apache.nifi.processor.email.extraction.EmailMessage.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.body)) {
        this.body = data().deepCopy(fields()[0].schema(), other.body);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.body_type)) {
        this.body_type = data().deepCopy(fields()[1].schema(), other.body_type);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.folder)) {
        this.folder = data().deepCopy(fields()[2].schema(), other.folder);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.message_id)) {
        this.message_id = data().deepCopy(fields()[3].schema(), other.message_id);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.recipients)) {
        this.recipients = data().deepCopy(fields()[4].schema(), other.recipients);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.sender_details)) {
        this.sender_details = data().deepCopy(fields()[5].schema(), other.sender_details);
        fieldSetFlags()[5] = true;
      }
      if (other.hasSenderDetailsBuilder()) {
        this.sender_detailsBuilder = org.apache.nifi.processor.email.extraction.SenderReceiverDetails.newBuilder(other.getSenderDetailsBuilder());
      }
      if (isValidValue(fields()[6], other.subject)) {
        this.subject = data().deepCopy(fields()[6].schema(), other.subject);
        fieldSetFlags()[6] = true;
      }
    }

    /**
     * Creates a Builder by copying an existing EmailMessage instance
     * @param other The existing instance to copy.
     */
    private Builder(org.apache.nifi.processor.email.extraction.EmailMessage other) {
            super(SCHEMA$);
      if (isValidValue(fields()[0], other.body)) {
        this.body = data().deepCopy(fields()[0].schema(), other.body);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.body_type)) {
        this.body_type = data().deepCopy(fields()[1].schema(), other.body_type);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.folder)) {
        this.folder = data().deepCopy(fields()[2].schema(), other.folder);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.message_id)) {
        this.message_id = data().deepCopy(fields()[3].schema(), other.message_id);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.recipients)) {
        this.recipients = data().deepCopy(fields()[4].schema(), other.recipients);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.sender_details)) {
        this.sender_details = data().deepCopy(fields()[5].schema(), other.sender_details);
        fieldSetFlags()[5] = true;
      }
      this.sender_detailsBuilder = null;
      if (isValidValue(fields()[6], other.subject)) {
        this.subject = data().deepCopy(fields()[6].schema(), other.subject);
        fieldSetFlags()[6] = true;
      }
    }

    /**
      * Gets the value of the 'body' field.
      * @return The value.
      */
    public java.lang.CharSequence getBody() {
      return body;
    }

    /**
      * Sets the value of the 'body' field.
      * @param value The value of 'body'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setBody(java.lang.CharSequence value) {
      validate(fields()[0], value);
      this.body = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'body' field has been set.
      * @return True if the 'body' field has been set, false otherwise.
      */
    public boolean hasBody() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'body' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearBody() {
      body = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'body_type' field.
      * @return The value.
      */
    public org.apache.nifi.processor.email.extraction.BodyType getBodyType() {
      return body_type;
    }

    /**
      * Sets the value of the 'body_type' field.
      * @param value The value of 'body_type'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setBodyType(org.apache.nifi.processor.email.extraction.BodyType value) {
      validate(fields()[1], value);
      this.body_type = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'body_type' field has been set.
      * @return True if the 'body_type' field has been set, false otherwise.
      */
    public boolean hasBodyType() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'body_type' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearBodyType() {
      body_type = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'folder' field.
      * @return The value.
      */
    public java.lang.CharSequence getFolder() {
      return folder;
    }

    /**
      * Sets the value of the 'folder' field.
      * @param value The value of 'folder'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setFolder(java.lang.CharSequence value) {
      validate(fields()[2], value);
      this.folder = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'folder' field has been set.
      * @return True if the 'folder' field has been set, false otherwise.
      */
    public boolean hasFolder() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'folder' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearFolder() {
      folder = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'message_id' field.
      * @return The value.
      */
    public java.lang.CharSequence getMessageId() {
      return message_id;
    }

    /**
      * Sets the value of the 'message_id' field.
      * @param value The value of 'message_id'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setMessageId(java.lang.CharSequence value) {
      validate(fields()[3], value);
      this.message_id = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'message_id' field has been set.
      * @return True if the 'message_id' field has been set, false otherwise.
      */
    public boolean hasMessageId() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'message_id' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearMessageId() {
      message_id = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /**
      * Gets the value of the 'recipients' field.
      * @return The value.
      */
    public java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> getRecipients() {
      return recipients;
    }

    /**
      * Sets the value of the 'recipients' field.
      * @param value The value of 'recipients'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setRecipients(java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails> value) {
      validate(fields()[4], value);
      this.recipients = value;
      fieldSetFlags()[4] = true;
      return this;
    }

    /**
      * Checks whether the 'recipients' field has been set.
      * @return True if the 'recipients' field has been set, false otherwise.
      */
    public boolean hasRecipients() {
      return fieldSetFlags()[4];
    }


    /**
      * Clears the value of the 'recipients' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearRecipients() {
      recipients = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    /**
      * Gets the value of the 'sender_details' field.
      * @return The value.
      */
    public org.apache.nifi.processor.email.extraction.SenderReceiverDetails getSenderDetails() {
      return sender_details;
    }

    /**
      * Sets the value of the 'sender_details' field.
      * @param value The value of 'sender_details'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setSenderDetails(org.apache.nifi.processor.email.extraction.SenderReceiverDetails value) {
      validate(fields()[5], value);
      this.sender_detailsBuilder = null;
      this.sender_details = value;
      fieldSetFlags()[5] = true;
      return this;
    }

    /**
      * Checks whether the 'sender_details' field has been set.
      * @return True if the 'sender_details' field has been set, false otherwise.
      */
    public boolean hasSenderDetails() {
      return fieldSetFlags()[5];
    }

    /**
     * Gets the Builder instance for the 'sender_details' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public org.apache.nifi.processor.email.extraction.SenderReceiverDetails.Builder getSenderDetailsBuilder() {
      if (sender_detailsBuilder == null) {
        if (hasSenderDetails()) {
          setSenderDetailsBuilder(org.apache.nifi.processor.email.extraction.SenderReceiverDetails.newBuilder(sender_details));
        } else {
          setSenderDetailsBuilder(org.apache.nifi.processor.email.extraction.SenderReceiverDetails.newBuilder());
        }
      }
      return sender_detailsBuilder;
    }

    /**
     * Sets the Builder instance for the 'sender_details' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setSenderDetailsBuilder(org.apache.nifi.processor.email.extraction.SenderReceiverDetails.Builder value) {
      clearSenderDetails();
      sender_detailsBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'sender_details' field has an active Builder instance
     * @return True if the 'sender_details' field has an active Builder instance
     */
    public boolean hasSenderDetailsBuilder() {
      return sender_detailsBuilder != null;
    }

    /**
      * Clears the value of the 'sender_details' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearSenderDetails() {
      sender_details = null;
      sender_detailsBuilder = null;
      fieldSetFlags()[5] = false;
      return this;
    }

    /**
      * Gets the value of the 'subject' field.
      * @return The value.
      */
    public java.lang.CharSequence getSubject() {
      return subject;
    }

    /**
      * Sets the value of the 'subject' field.
      * @param value The value of 'subject'.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder setSubject(java.lang.CharSequence value) {
      validate(fields()[6], value);
      this.subject = value;
      fieldSetFlags()[6] = true;
      return this;
    }

    /**
      * Checks whether the 'subject' field has been set.
      * @return True if the 'subject' field has been set, false otherwise.
      */
    public boolean hasSubject() {
      return fieldSetFlags()[6];
    }


    /**
      * Clears the value of the 'subject' field.
      * @return This builder.
      */
    public org.apache.nifi.processor.email.extraction.EmailMessage.Builder clearSubject() {
      subject = null;
      fieldSetFlags()[6] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public EmailMessage build() {
      try {
        EmailMessage record = new EmailMessage();
        record.body = fieldSetFlags()[0] ? this.body : (java.lang.CharSequence) defaultValue(fields()[0]);
        record.body_type = fieldSetFlags()[1] ? this.body_type : (org.apache.nifi.processor.email.extraction.BodyType) defaultValue(fields()[1]);
        record.folder = fieldSetFlags()[2] ? this.folder : (java.lang.CharSequence) defaultValue(fields()[2]);
        record.message_id = fieldSetFlags()[3] ? this.message_id : (java.lang.CharSequence) defaultValue(fields()[3]);
        record.recipients = fieldSetFlags()[4] ? this.recipients : (java.util.List<org.apache.nifi.processor.email.extraction.SenderReceiverDetails>) defaultValue(fields()[4]);
        if (sender_detailsBuilder != null) {
          record.sender_details = this.sender_detailsBuilder.build();
        } else {
          record.sender_details = fieldSetFlags()[5] ? this.sender_details : (org.apache.nifi.processor.email.extraction.SenderReceiverDetails) defaultValue(fields()[5]);
        }
        record.subject = fieldSetFlags()[6] ? this.subject : (java.lang.CharSequence) defaultValue(fields()[6]);
        return record;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<EmailMessage>
    WRITER$ = (org.apache.avro.io.DatumWriter<EmailMessage>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<EmailMessage>
    READER$ = (org.apache.avro.io.DatumReader<EmailMessage>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}
