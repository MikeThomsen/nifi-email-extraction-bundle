package org.apache.nifi.processor.email.extraction;

import com.pff.PSTAttachment;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTRAFileContent;
import com.pff.PSTRecipient;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@Tags({ "microsoft", "outlook", "email", "messages", "extract", "pst" })
@CapabilityDescription("This processor extracts messages from a Microsoft Outlook PST file.")
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

    public static final AllowableValue TRUE = new AllowableValue("true", "True", "Attachments will be added to records.");
    public static final AllowableValue FALSE = new AllowableValue("false", "False", "Attachments will be sent to the \"attachments\" relationship");

    public static final PropertyDescriptor ATTACH_ATTACHMENTS_TO_RECORD = new PropertyDescriptor.Builder()
        .name("extract-pst-attach-attachments")
        .displayName("Attach Attachments to Records")
        .allowableValues(TRUE, FALSE)
        .defaultValue(FALSE.getValue())
        .expressionLanguageSupported(ExpressionLanguageScope.NONE)
        .description("If true, attachments will be part of the record. If false, attachments will be sent to the \"attachments\" " +
                "relationship. If this option is enabled, the Avro records can grow very large.")
        .build();

    public static final List<PropertyDescriptor> PROPERTY_DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
        ATTACH_ATTACHMENTS_TO_RECORD
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

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) {
            return;
        }

        File _temp = null;
        FlowFile output = session.create(input);
        List<FlowFile> attachments = new ArrayList<>();
        boolean attach = context.getProperty(ATTACH_ATTACHMENTS_TO_RECORD).asBoolean();
        try {
            _temp = File.createTempFile(input.getAttribute("uuid"), null);
            FileOutputStream out = new FileOutputStream(_temp);
            session.exportTo(input, out);
            out.close();

            SpecificDatumWriter<EmailMessage> datumWriter = new SpecificDatumWriter<>(EmailMessage.class);
            DataFileWriter<EmailMessage> fileWriter = new DataFileWriter<>(datumWriter);
            PSTFile file = new PSTFile(new PSTRAFileContent(_temp));

            OutputStream ffOut = session.write(output);

            fileWriter.create(EmailMessage.SCHEMA$, ffOut);
            processFolder(file.getRootFolder(), fileWriter, attachments, attach, output, session);
            fileWriter.close();
            ffOut.close();

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_MESSAGES);

        } catch (Exception ex) {
            getLogger().error("Error", ex);

            for (FlowFile ff : attachments) {
                session.remove(ff);
            }
            session.remove(output);
            session.transfer(input, REL_FAILURE);

            if (_temp != null) {
                _temp.delete();
            }
        }
    }

    private void processFolder(PSTFolder folder, DataFileWriter writer, List<FlowFile> attachments, boolean attach, FlowFile parent, ProcessSession session) throws Exception
    {
        if (folder.hasSubfolders()) {
            List<PSTFolder> childFolders = folder.getSubFolders();
            for (PSTFolder childFolder : childFolders) {
                processFolder(childFolder, writer, attachments, attach, parent, session);
            }
        }

        if (folder.getContentCount() > 0) {
            PSTMessage email;
            while ( (email = (PSTMessage)folder.getNextChild()) != null) {
                EmailMessage.Builder message = EmailMessage.newBuilder()
                    .setSubject(email.getSubject())
                    .setFolder(folder.getDisplayName());
                if (email.getBody() != null) {
                    message.setBody(email.getBody()).setBodyType(BodyType.PLAIN);
                } else if (email.getBodyHTML() != null) {
                    message.setBody(email.getBodyHTML()).setBodyType(BodyType.HTML);
                } else if (email.getRTFBody() != null) {
                    message.setBody(email.getRTFBody()).setBodyType(BodyType.RTF);
                } else {
                    throw new ProcessException("Missing body.");
                }

                message.setSenderDetails(SenderDetails.newBuilder()
                        .setName(email.getSenderName())
                        .setEmailAddress(email.getSenderEmailAddress()).build());

                List<RecipientDetails> recipientDetails = new ArrayList<>();
                message.setRecipients(recipientDetails);
                for (int x = 0; x < email.getNumberOfRecipients(); x++) {
                    PSTRecipient recipient = email.getRecipient(x);
                    recipientDetails.add(RecipientDetails.newBuilder()
                        .setName(recipient.getDisplayName())
                        .setEmailAddress(recipient.getEmailAddress())
                        .build()
                    );
                }

                processAttachments(message, email, attachments, attach, parent, session);

                writer.append(message.build());
            }
        }
    }

    private void processAttachments(EmailMessage.Builder builder, PSTMessage message, List<FlowFile> attachments, boolean attach, FlowFile parent, ProcessSession session) throws Exception {
        List<Attachment> _temp = new ArrayList<>();
        for (int x = 0; x < message.getNumberOfAttachments(); x++) {
            PSTAttachment _att = message.getAttachment(x);
            _temp.add(buildAttachment(_att));
        }

        if (attach) {
            builder.setAttachments(_temp);
        } else {
            for (Attachment attachment : _temp) {
                SpecificDatumWriter writer = new SpecificDatumWriter(Attachment.class);
                DataFileWriter<Attachment> fileWriter = new DataFileWriter<>(writer);
                FlowFile _attFlowFile = session.create(parent);
                OutputStream os = session.write(_attFlowFile);
                fileWriter
                    .create(Attachment.SCHEMA$, os)
                    .append(attachment);
                fileWriter.close();

                _attFlowFile = session.putAttribute(_attFlowFile, "filename", attachment.getFileName().toString());

                attachments.add(_attFlowFile);
            }
        }
    }

    private Attachment buildAttachment(PSTAttachment _att) throws Exception {
        InputStream is = _att.getFileInputStream();
        return Attachment.newBuilder()
            .setData(ByteBuffer.wrap(IOUtils.toByteArray(is)))
            .setFileName(_att.getLongFilename())
            .setSize(_att.getSize())
            .build();
    }
}
