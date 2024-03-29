package org.apache.nifi.processor.email.extraction;

import com.sun.mail.util.BASE64DecoderStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;

import javax.mail.Address;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractJavaMailProcessor extends AbstractExtractEmailProcessor {
    protected static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_FAILURE, REL_ORIGINAL, REL_MESSAGES, REL_ATTACHMENTS
    )));

    public static final String ATTR_PARENT_FOLDER = "parent.folder";
    public static final String ATTR_MESSAGE_ID = "message.id";
    public static final String HEADER_IN_REPLY_TO = "In-Reply-To";
    public static final String HEADER_MESSAGE_ID = "message-id";

    public static final AllowableValue PLAIN = new AllowableValue("plain", "Plain Text", "Select the plain text version.");
    public static final AllowableValue HTML  = new AllowableValue("html", "HTML", "Select the HTML version.");

    public static final PropertyDescriptor FOLDER_IDENTIFIER = new PropertyDescriptor.Builder()
            .name("extract-mbox-folder-identifier")
            .displayName("Folder Identifier")
            .description("A MBox file typically represents one folder in an email account. Use this property to configure a fully qualified name for a MBox file " +
                    "that allows it to be associated with things like the user and originating system.")
            .required(true)
            .defaultValue("${filename}")
            .addValidator(StandardValidators.NON_EMPTY_EL_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    public static final AllowableValue MISSING_FIELD_ERROR = new AllowableValue("error", "As an error",
            "Treat it as an error event.");
    public static final AllowableValue MISSING_FIELD_EMPTY = new AllowableValue("empty", "Assign an empty string",
            "Use an empty string whenever the value is missing.");
    public static final AllowableValue MISSING_FIELD_VALUE = new AllowableValue("value", "Assign a supplied value",
            "Assign a value that you specify.");
    public static final PropertyDescriptor MISSING_FIELD_STRATEGY = new PropertyDescriptor.Builder()
            .name("extract-mbox-missing-field-strategy")
            .displayName("Missing Field Strategy")
            .description("Sometimes fields like the sender field are missing because of poorly constructed email messages. This configuration option controls " +
                    "whether a value should be substituted in its place or have it be treated as an error event.")
            .allowableValues(MISSING_FIELD_ERROR, MISSING_FIELD_EMPTY, MISSING_FIELD_VALUE)
            .defaultValue(MISSING_FIELD_ERROR.getValue())
            .addValidator(Validator.VALID)
            .required(true)
            .build();
    public static final PropertyDescriptor MISSING_FIELD_SUBSTITUTION_VALUE = new PropertyDescriptor.Builder()
            .name("extract-mbox-missing-field-value")
            .displayName("Missing Field Substitution Value")
            .addValidator(Validator.VALID)
            .required(false)
            .description("This value is used when the missing field strategy is configured to use the value substitution option. It is mainly" +
                    "useful for scenarios when you need to put in a string to give a cue to a user interface or downstream application that the value " +
                    "is missing.")
            .build();


    public static final AllowableValue ERROR_CONTINUE = new AllowableValue("continue", "Continue", "Try to keep parsing " +
            "and commit.");
    public static final AllowableValue ERROR_SEND_TO_FAILURE = new AllowableValue("failure", "Send to Failure Relationship",
            "Remove all work attempted so far and send the input flowfile to the failure relationship.");
    public static final PropertyDescriptor ERROR_STRATEGY = new PropertyDescriptor.Builder()
            .name("extract-mbox-error-strategy")
            .displayName("Error Strategy")
            .description("Controls how errors are handled when parsing.")
            .required(true)
            .allowableValues(ERROR_CONTINUE, ERROR_SEND_TO_FAILURE)
            .defaultValue(ERROR_CONTINUE.getValue())
            .build();

    protected volatile RecordSetWriterFactory factory;
    protected boolean sendToFailure;

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        factory = context.getProperty(WRITER).asControllerService(RecordSetWriterFactory.class);
        sendToFailure = context.getProperty(ERROR_STRATEGY).getValue().equals(ERROR_SEND_TO_FAILURE.getValue());
    }

    protected void handleEmailAddress(String email, Map<String, Object> target) {
        if (email.contains("<") && email.contains(">")) {
            String[] split = email.split("[\\s]*[\\<]");
            if (split.length != 2) {
                getLogger().error(String.format("Got %d tokens from %s, failing this email.", split.length, email));
                return;
            }

            target.put("name", split[0].replaceAll("\"", ""));
            target.put("email_address", split[1].replaceAll("[\\<\\>\\\"]", ""));
        } else {
            target.put("name", email);
            target.put("email_address", email);
        }
    }

    protected void processMessage(String folder, Message msg, RecordSetWriter writer, FlowFile parent, List<FlowFile> attachments, ProcessSession session) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("bodies", new ArrayList<Map<String, Object>>());
        message.put("subject", StringUtils.isBlank(msg.getSubject()) ? "" : msg.getSubject());
        message.put("folder", folder);
        String sender = (msg.getFrom() != null && msg.getFrom().length > 0) ? (msg.getFrom()[0]).toString() : "";
        boolean isMultiPart = msg.getContent() instanceof Multipart;

        Map<String, Object> senderDetails = new HashMap<>();
        handleEmailAddress(sender, senderDetails);


        message.put("sender_details", new MapRecord(AvroTypeUtil.createSchema(SenderReceiverDetails.SCHEMA$), senderDetails));
        message.put("message_id", String.valueOf(msg.getMessageNumber()));

        List<Record> recipients = new ArrayList<>();
        if (msg.getAllRecipients() != null) {
            for (Address address : msg.getAllRecipients()) {
                Map<String, Object> _temp = new HashMap<>();
                handleEmailAddress(address.toString(), _temp);
                MapRecord recipient = new MapRecord(AvroTypeUtil.createSchema(SenderReceiverDetails.SCHEMA$), _temp);
                recipients.add(recipient);
            }
        }
        message.put("recipients", recipients.toArray());

        Enumeration headers = msg.getAllHeaders();
        Map<String, String> _heads = new HashMap<>();
        String messageId = null;

        while (headers.hasMoreElements()) {
            Header header = (Header) headers.nextElement();
            _heads.put(header.getName(), header.getValue());

            if (header.getName().equals(HEADER_IN_REPLY_TO)) {
                message.put("in_reply_to", header.getValue());
            } else if (header.getName().equalsIgnoreCase(HEADER_MESSAGE_ID)) {
                messageId = header.getValue();
            }
        }

        if (isMultiPart) {
            Multipart multiPart = (Multipart) msg.getContent();
            findBody(folder, messageId, multiPart, message, parent, attachments, session);
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("body", msg.getContent());
            body.put("body_type", "PLAIN");
            ((List)message.get("bodies")).add(body);
        }

        message.put("headers", _heads);

        addAttachmentInformation(message, attachments);

        writer.write(new MapRecord(AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), message));
    }

    protected void addAttachmentInformation(Map<String, Object> message, List<FlowFile> attachments) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (FlowFile flowFile : attachments) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(CoreAttributes.FILENAME.key(), flowFile.getAttribute(CoreAttributes.FILENAME.key()));
            attrs.put(CoreAttributes.MIME_TYPE.key(), flowFile.getAttribute(CoreAttributes.MIME_TYPE.key()));
            attrs.put("messageId", flowFile.getAttribute(ATTR_MESSAGE_ID));
            attrs.put("folder", flowFile.getAttribute(ATTR_PARENT_FOLDER));
            files.add(attrs);
        }
        message.put("attachments", files);
    }

    protected void findBody(String folder, String messageId, Multipart multipart, Map<String, Object> message, FlowFile parent, List<FlowFile> attachments, ProcessSession session) throws Exception {
        int count = multipart.getCount();

        Map<String, String> inlineBodies = new HashMap<>();

        for (int x = 0; x < count; x++) {
            MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(x);
            Object content = part.getContent();
            String ct = part.getContentType();
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                InputStream is;
                if (content instanceof String) {
                    is = new ByteArrayInputStream(((String)content).getBytes());
                } else if (content instanceof InputStream) {
                    is = (InputStream)content;
                } else if (content instanceof MimeMessage) {
                    MimeMessage mm = (MimeMessage)content;
                    content = mm.getContent();
                    if (content instanceof String) {
                        is = new ByteArrayInputStream(((String)content).getBytes());
                    } else {
                        return; //TODO handle this...
                    }
                } else {
                    return;
                }

                handleAttachement(folder, messageId, ct, is, parent, attachments, session);
            }
            else if (Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
                if (ct.startsWith("text/plain")) {
                    inlineBodies.put("text/plain", (String)content);
                } else if (ct.startsWith("text/html")) {
                    inlineBodies.put("text/html", (String)content);
                } else if (content instanceof BASE64DecoderStream) {
                    handleAttachement(folder, messageId, ct, (BASE64DecoderStream) content, parent, attachments, session);
                }
            }
            else if (part.getDisposition() == null) {
                if (content instanceof Multipart) {
                    findBody(folder, messageId, (Multipart)content, message, parent, attachments, session);
                } else {
                    Map<String, Object> body = new HashMap<>();
                    body.put("body", content);
                    body.put("body_type", ct.toLowerCase().contains("html") ? "HTML" : "PLAIN");
                    ((List)message.get("bodies")).add(body);
                }
            }
        }

        if (!inlineBodies.isEmpty()) {
            final List<Map<String, Object>> bodies = (List<Map<String, Object>>) message.get("bodies");
            inlineBodies.entrySet().forEach(entry -> {
                Map<String, Object> body = new HashMap<>();
                body.put("body", entry.getValue());
                body.put("body_type", entry.getKey());
                bodies.add(body);
            });
        }
    }

    protected void handleAttachement(String folder, String messageId, String mime, InputStream stream,
                                     FlowFile parent, List<FlowFile> attachments, ProcessSession session) {
        String[] parts = mime.split(";");
        Map<String, String> attrs = new HashMap<>();
        attrs.put(CoreAttributes.FILENAME.key(), parts.length == 1
                ? String.format("%s.png", UUID.randomUUID().toString())
                : parts[1].replace("name=", "").trim());
        attrs.put(CoreAttributes.MIME_TYPE.key(), parts[0].trim());
        attrs.put(ATTR_PARENT_FOLDER, folder);
        attrs.put(ATTR_MESSAGE_ID, messageId);

        FlowFile attachment = session.create(parent);
        try (OutputStream os = session.write(attachment)) {
            IOUtils.copy(stream, os);
            os.close();

            attachment = session.putAllAttributes(attachment, attrs);
            attachments.add(attachment);
        } catch (Exception ex) {
            session.remove(attachment);
            throw new ProcessException(ex);
        }
    }
}
