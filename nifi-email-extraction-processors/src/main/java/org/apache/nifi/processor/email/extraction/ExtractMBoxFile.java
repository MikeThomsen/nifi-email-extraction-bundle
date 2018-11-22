package org.apache.nifi.processor.email.extraction;

import com.sun.mail.util.BASE64DecoderStream;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
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
import org.apache.nifi.util.StringUtils;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.MimeBodyPart;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class ExtractMBoxFile extends AbstractExtractEmailProcessor {
    static {
        System.setProperty("mstor.cache.disabled", "true");
    }

    private static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        REL_FAILURE, REL_ORIGINAL, REL_MESSAGES, REL_ATTACHMENTS
    )));

    public static final String ATTR_PARENT_FOLDER = "parent.folder";

    public static final AllowableValue PLAIN = new AllowableValue("plain", "Plain Text", "Select the plain text version.");
    public static final AllowableValue HTML  = new AllowableValue("html", "HTML", "Select the HTML version.");
    public static final PropertyDescriptor PREFERRED_BODY_TYPE = new PropertyDescriptor.Builder()
        .name("extract-mbox-preferred-body-type")
        .displayName("Preferred Body Type")
        .description("Plain text and HTML versions are usually sent in the same message. This option selects which one to send with the " +
            "result record.")
        .allowableValues(PLAIN, HTML)
        .defaultValue(PLAIN.getValue())
        .required(true)
        .build();

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

    private static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
        WRITER, PREFERRED_BODY_TYPE, FOLDER_IDENTIFIER
    ));

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    private volatile RecordSetWriterFactory factory;

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        factory = context.getProperty(WRITER).asControllerService(RecordSetWriterFactory.class);
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) {
            return;
        }

        FlowFile output = session.create(input);
        File _temp = null;
        List<FlowFile> attachments = new ArrayList<>();

        String folderIdentifier = context.getProperty(FOLDER_IDENTIFIER).evaluateAttributeExpressions(input).getValue();

        try (OutputStream os = session.write(output)) {
            _temp = writeFlowFileToTemp(input, session);

            RecordSetWriter writer = factory.createWriter(getLogger(), AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), os);
            writer.beginRecordSet();

            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "mstor");
            props.setProperty("mstor.mbox.metadataStrategy", "none");
            props.setProperty("mstor.mbox.cacheBuffers", "disabled");
            props.setProperty("mstor.mbox.bufferStrategy", "mapped");
            props.setProperty("mstor.metadata", "disabled");
            Session mSession = Session.getDefaultInstance(props);
            Store store = mSession.getStore(new URLName("mstor:" + _temp.getAbsolutePath()));
            store.connect();
            Folder folder = store.getDefaultFolder();
            folder.open(Folder.READ_ONLY);

            int count = folder.getMessageCount();

            for (int index = 0; index < count; index++) {
                Message msg = folder.getMessage(index + 1);
                processMessage(folderIdentifier, msg, writer, output, attachments, session);
            }
            writer.finishRecordSet();
            writer.close();
            os.close();


            Map<String, String> attrs = new HashMap<>();
            attrs.put("record.count", String.valueOf(count));
            output = session.putAllAttributes(output, attrs);

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_MESSAGES);

            for (FlowFile flowFile : attachments) {
                session.transfer(flowFile, REL_ATTACHMENTS);
            }
        } catch (Exception ex) {
            getLogger().error("Error", ex);

            for (FlowFile attachment : attachments) {
                session.remove(attachment);
            }

            session.remove(output);
            session.transfer(input, REL_FAILURE);
        } finally {
            if (_temp != null) {
                _temp.delete();
            }
        }
    }

    private void processMessage(String folder, Message msg, RecordSetWriter writer, FlowFile parent, List<FlowFile> attachments, ProcessSession session) throws Exception {
        if (StringUtils.isBlank(msg.getSubject()) && msg.getFrom() == null) {
            getLogger().error("Encountered a possibly bad message, skipping...");
            if (getLogger().isDebugEnabled()) {
                getLogger().debug(String.format("Bad message:\n\n*****START****\n%s\n*******END******", msg.toString()));
            }
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("subject", msg.getSubject());
        message.put("folder", folder);
        String sender = (msg.getFrom()[0]).toString();
        boolean isMultiPart = msg.getContent() instanceof Multipart;

        Map<String, Object> senderDetails = new HashMap<>();
        senderDetails.put("name", sender);
        senderDetails.put("email_address", sender);

        message.put("body_type", "PLAIN");
        message.put("sender_details", new MapRecord(AvroTypeUtil.createSchema(SenderReceiverDetails.SCHEMA$), senderDetails));
        message.put("message_id", String.valueOf(msg.getMessageNumber()));

        List<Record> recipients = new ArrayList<>();
        for (Address address : msg.getAllRecipients()) {
            Map<String, Object> _temp = new HashMap<>();
            _temp.put("name", address.toString());
            _temp.put("email_address", address.toString());
            MapRecord recipient = new MapRecord(AvroTypeUtil.createSchema(SenderReceiverDetails.SCHEMA$), _temp);
            recipients.add(recipient);
        }
        message.put("recipients", recipients.toArray());

        if (isMultiPart) {
            Multipart multiPart = (Multipart) msg.getContent();
            findBody(folder, multiPart, message, parent, attachments, session);
        } else {
            message.put("body", msg.getContent());
        }
        writer.write(new MapRecord(AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), message));
    }

    private void findBody(String folder, Multipart multipart, Map<String, Object> message, FlowFile parent, List<FlowFile> attachments, ProcessSession session) throws Exception {
        int count = multipart.getCount();
        for (int x = 0; x < count; x++) {
            MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(x);
            Object content = part.getContent();
            String ct = part.getContentType();
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                InputStream is;
                if (content instanceof String) {
                    is = new ByteArrayInputStream(((String)content).getBytes());
                } else {
                    is = (InputStream)content;
                }
                handleAttachement(folder, ct, is, parent, attachments, session);
            }
            else if (Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
                if (ct.startsWith("text/plain")) {
                    message.put("body", part.getContent());
                } else if (ct.startsWith("text/html")) {

                } else if (content instanceof BASE64DecoderStream) {
                    handleAttachement(folder, ct, (BASE64DecoderStream) content, parent, attachments, session);
                }
            }
            else if (part.getDisposition() == null) {
                if (content instanceof Multipart) {
                    findBody(folder, (Multipart)content, message, parent, attachments, session);
                } else {
                    message.put("body", content);
                }
            }
        }
    }

    private void handleAttachement(String folder, String mime, InputStream stream, FlowFile parent, List<FlowFile> attachments, ProcessSession session) {
        String[] parts = mime.split(";");
        Map<String, String> attrs = new HashMap<>();
        attrs.put(CoreAttributes.FILENAME.key(), parts.length == 1
                ? String.format("%s.png", UUID.randomUUID().toString())
                : parts[1].replace("name=", "").trim());
        attrs.put(CoreAttributes.MIME_TYPE.key(), parts[0].trim());
        attrs.put(ATTR_PARENT_FOLDER, folder);

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
