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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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


    public static final List<PropertyDescriptor> PROPERTY_DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(

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
            processFolder(file.getRootFolder(), fileWriter, attachments, output, session);
            fileWriter.close();
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

            if (_temp != null) {
                _temp.delete();
            }
        }
    }

    private void processFolder(PSTFolder folder, DataFileWriter writer, List<FlowFile> attachments, FlowFile parent, ProcessSession session) throws Exception
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

                message.setMessageId(email.getInternetMessageId());

                processAttachments(email, attachments, parent, session);

                writer.append(message.build());
            }
        }
    }

    private void processAttachments(PSTMessage message, List<FlowFile> attachments, FlowFile parent, ProcessSession session) throws Exception {
        for (int x = 0; x < message.getNumberOfAttachments(); x++) {
            FlowFile _attFlowFile = session.create(parent);
            try (OutputStream os = session.write(_attFlowFile)) {
                PSTAttachment attachment = message.getAttachment(x);
                IOUtils.copy(attachment.getFileInputStream(), os);
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
