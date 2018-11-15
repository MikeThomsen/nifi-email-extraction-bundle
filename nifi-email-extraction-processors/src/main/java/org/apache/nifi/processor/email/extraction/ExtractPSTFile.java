package org.apache.nifi.processor.email.extraction;

import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTRAFileContent;
import com.pff.PSTRecipient;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
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

    public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        REL_MESSAGES, REL_ATTACHMENTS, REL_FAILURE, REL_ORIGINAL
    )));

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
            processFolder(file.getRootFolder(), fileWriter);
            fileWriter.close();
            ffOut.close();

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_MESSAGES);

        } catch (Exception ex) {
            getLogger().error("Error", ex);
            session.rollback();
            session.transfer(input, REL_FAILURE);

            if (_temp != null) {
                _temp.delete();
            }
        }
    }

    private void processFolder(PSTFolder folder, DataFileWriter writer) throws Exception
    {
        if (folder.hasSubfolders()) {
            List<PSTFolder> childFolders = folder.getSubFolders();
            for (PSTFolder childFolder : childFolders) {
                processFolder(childFolder, writer);
            }
        }

        if (folder.getContentCount() > 0) {
            PSTMessage email = null;
            while ( (email = (PSTMessage)folder.getNextChild()) != null) {
                EmailMessage.Builder message = EmailMessage.newBuilder()
                    .setSubject(email.getSubject());
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

                writer.append(message.build());
            }
        }
    }
}
