package org.apache.nifi.processor.email.extraction;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.MimeBodyPart;
import java.io.File;
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

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class ExtractMBoxFile extends AbstractExtractEmailProcessor {
    private static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        REL_FAILURE, REL_ORIGINAL, REL_MESSAGES
    )));

    private static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
        WRITER
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
        try (OutputStream os = session.write(output)) {
            _temp = writeFlowFileToTemp(input, session);

            RecordSetWriter writer = factory.createWriter(getLogger(), AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), os);
            writer.beginRecordSet();

            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "mstor");
            props.setProperty("mstor.mbox.metadataStrategy", "none");
            props.setProperty("mstor.mbox.cacheBuffers", "disabled");
            props.setProperty("mstor.cache.disabled", "true");
            props.setProperty("mstor.mbox.bufferStrategy", "mapped");
            props.setProperty("mstor.metadata", "disabled");
            Session mSession = Session.getDefaultInstance(props);
            Store store = mSession.getStore(new URLName("mstor:" + _temp.getAbsolutePath()));
            store.connect();
            Folder inbox = store.getDefaultFolder();
            inbox.open(Folder.READ_ONLY);
            int count = inbox.getMessageCount();

            for (Message msg : inbox.getMessages()) {
                processMessage(inbox.getName(), msg, writer);
            }
            writer.finishRecordSet();
            writer.close();
            os.close();


            Map<String, String> attrs = new HashMap<>();
            attrs.put("record.count", String.valueOf(count));
            output = session.putAllAttributes(output, attrs);

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_MESSAGES);
        } catch (Exception ex) {
            getLogger().error("Error", ex);
            session.remove(output);
            session.transfer(input, REL_FAILURE);
        } finally {
            if (_temp != null) {
                _temp.delete();
            }
        }
    }

    private void processMessage(String folder, Message msg, RecordSetWriter writer) throws Exception {
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

            for (int i = 0; i < multiPart.getCount(); i++) {
                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(i);
                String disposition = part.getDisposition();
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {

                } else if (Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
                    Object o = part.getContent();
                } else if (disposition == null) {
                    Object partContent = part.getContent();
                    if (partContent instanceof Multipart) {
                        Multipart mp = (Multipart)partContent;
                        for (int j = 0; j < mp.getCount(); j++) {
                            MimeBodyPart _part = (MimeBodyPart) mp.getBodyPart(j);
                            String type = _part.getContentType();
                            Object obj = _part.getContent();
                            if (type.contains("text/plain")) {
                                message.put("body", obj);
                            }
                        }
                    } else {
                        message.put("body", partContent);
                    }
                }
            }
        } else {
            message.put("body", msg.getContent());
        }
        writer.write(new MapRecord(AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), message));
    }
}
