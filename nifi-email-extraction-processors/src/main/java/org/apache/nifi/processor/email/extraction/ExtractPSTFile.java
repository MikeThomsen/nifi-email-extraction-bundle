package org.apache.nifi.processor.email.extraction;

import com.pff.PSTAttachment;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTRAFileContent;
import com.pff.PSTRecipient;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordSchema;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@Tags({ "microsoft", "outlook", "email", "messages", "extract", "pst" })
@CapabilityDescription("This processor extracts messages and attachments from a Microsoft Outlook PST file and " +
        "groups the messages into a record set.")
public class ExtractPSTFile extends AbstractProcessor {
    public static final Relationship REL_FAILURE = new Relationship.Builder()
        .name("failure")
        .description("All flowfiles that fail extraction are sent to this relationship.")
        .build();
    public static final Relationship REL_ORIGINAL = new Relationship.Builder()
            .name("original")
            .description("All original input flowfiles go to this relationship after successful extraction.")
            .build();
    public static final Relationship REL_MESSAGES = new Relationship.Builder()
        .name("messages")
        .description("Extracted messages are sent to this relationship.")
        .build();
    public static final Relationship REL_ATTACHMENTS = new Relationship.Builder()
        .name("attachments")
        .description("Attachments can be sent to this relationship if configured.")
        .autoTerminateDefault(true)
        .build();

    public static final PropertyDescriptor WRITER = new PropertyDescriptor.Builder()
        .name("output-writer")
        .displayName("Writer")
        .description("Controller service to use for writing the output.")
        .required(true)
        .identifiesControllerService(RecordSetWriterFactory.class)
        .build();

    public static final List<PropertyDescriptor> PROPERTY_DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
        WRITER
    ));

    public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        REL_MESSAGES, REL_ATTACHMENTS, REL_FAILURE, REL_ORIGINAL
    )));

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return PROPERTY_DESCRIPTORS;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    private volatile RecordSetWriterFactory factory;
    public static final RecordSchema SCHEMA = AvroTypeUtil.createSchema(EmailMessage.SCHEMA$);
    public static final RecordSchema SENDER_DETAILS_SCHEMA = AvroTypeUtil.createSchema(SenderReceiverDetails.SCHEMA$);

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

        File _temp = null;
        FlowFile output = session.create(input);
        List<FlowFile> attachments = new ArrayList<>();

        try (OutputStream ffOut = session.write(output)) {
            _temp = File.createTempFile(input.getAttribute("uuid"), null);
            FileOutputStream out = new FileOutputStream(_temp);
            session.exportTo(input, out);
            out.close();

            RecordSetWriter writer = factory.createWriter(getLogger(), SCHEMA, ffOut);
            PSTFile file = new PSTFile(new PSTRAFileContent(_temp));


            writer.beginRecordSet();
            processFolder(file.getRootFolder(), writer, attachments, output, session);
            writer.finishRecordSet();
            writer.close();
            ffOut.close();

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_MESSAGES);

            for (FlowFile ff : attachments) {
                session.transfer(ff, REL_ATTACHMENTS);
            }

        } catch (Exception ex) {
            getLogger().error("Error", ex);

            for (FlowFile ff : attachments) {
                session.remove(ff);
            }
            session.remove(output);
            session.transfer(input, REL_FAILURE);
        } finally {
            if (_temp != null) {
                _temp.delete();
            }
        }
    }

    private Map<String, String> makeBody(String body, String mime) {
        Map<String, String> retVal = new HashMap<>();
        retVal.put("body", body);
        retVal.put("body_type", mime);

        return retVal;
    }

    private void processFolder(PSTFolder folder, RecordSetWriter writer, List<FlowFile> attachments, FlowFile parent, ProcessSession session) throws Exception
    {
        if (folder.hasSubfolders()) {
            List<PSTFolder> childFolders = folder.getSubFolders();
            for (PSTFolder childFolder : childFolders) {
                processFolder(childFolder, writer, attachments, parent, session);
            }
        }

        if (folder.getContentCount() > 0) {
            PSTMessage email;
            while ( (email = (PSTMessage)folder.getNextChild()) != null) {
                Map<String, Object> message = new HashMap<>();
                message.put("headers", new HashMap<>());
                message.put("subject", email.getSubject());
                message.put("folder", folder.getDisplayName());

                List<Map<String, String>> bodies = new ArrayList<>();
                message.put("bodies", bodies);

                if (email.getBody() != null) {
                    bodies.add(makeBody(email.getBody(), "PLAIN"));
                } else if (email.getBodyHTML() != null) {
                    bodies.add(makeBody(email.getBodyHTML(), "HTML"));
                } else if (email.getRTFBody() != null) {
                    bodies.add(makeBody(email.getRTFBody(), "RTF"));
                } else {
                    throw new ProcessException("Missing body.");
                }

                Map<String, Object> senderDetails = new HashMap<>();
                senderDetails.put("name", email.getSenderName());
                senderDetails.put("email_address", email.getSenderEmailAddress());
                message.put("sender_details", new MapRecord(SENDER_DETAILS_SCHEMA, senderDetails));

                List<Record> recipientDetails = new ArrayList<>();
                for (int x = 0; x < email.getNumberOfRecipients(); x++) {
                    PSTRecipient recipient = email.getRecipient(x);
                    Map<String, Object> _temp = new HashMap<>();
                    _temp.put("name", recipient.getDisplayName());
                    _temp.put("email_address", recipient.getEmailAddress());
                    recipientDetails.add(new MapRecord(SENDER_DETAILS_SCHEMA, _temp));
                }

                message.put("recipients", recipientDetails.toArray());
                message.put("message_id", email.getInternetMessageId());

                Map<String, Object> headers = new HashMap<>();
                message.put("headers", headers);

                String[] headerSplit = email.getTransportMessageHeaders().split("[\\r\\n]");
                for (String header : headerSplit) {
                    if (StringUtils.isEmpty(header)) {
                        continue;
                    }
                    String[] split = header.split("[\\:][\\s]{1,}");
                    if (split.length != 2) {
                        getLogger().info(String.format("Skipping header: \"%s\"", header));
                    }

                    if (split[0].equalsIgnoreCase("date")) {
                        String[] temp = split[1].split(",");
                        if (temp.length == 4) {
                            headers.put("Date", String.format("%s, %s", temp[0], temp[1]));
                        } else {
                            headers.put("Date", split[1]);
                        }
                    } else {
                        headers.put(split[0], split[1]);
                    }
                }

                processAttachments(email, attachments, parent, session);

                writer.write(new MapRecord(SCHEMA, message));
            }
        }
    }

    private void processAttachments(PSTMessage message, List<FlowFile> attachments, FlowFile parent, ProcessSession session) throws Exception {
        for (int x = 0; x < message.getNumberOfAttachments(); x++) {
            FlowFile _attFlowFile = session.create(parent);
            PSTAttachment attachment = message.getAttachment(x);
            try (OutputStream os = session.write(_attFlowFile);
                 InputStream is = attachment.getFileInputStream()) {
                IOUtils.copy(is, os);
                os.close();

                Map<String, String> _attrs = new HashMap<>();
                _attrs.put("filename", attachment.getLongFilename());
                _attrs.put("source.pst.file", parent.getAttribute("filename"));
                _attrs.put("source.message.id", message.getInternetMessageId());

                _attFlowFile = session.putAllAttributes(_attFlowFile, _attrs);

                attachments.add(_attFlowFile);
            } catch (Exception ex) {
                session.remove(_attFlowFile);
                throw ex;
            }
        }
    }
}
