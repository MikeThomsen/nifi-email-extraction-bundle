package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.RecordSetWriterFactory;

import java.io.File;
import java.io.FileOutputStream;

public abstract class AbstractExtractEmailProcessor extends AbstractProcessor {
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

    public File writeFlowFileToTemp(FlowFile input, ProcessSession session) {
        try {
            File temp = File.createTempFile(input.getAttribute("uuid"), "");
            FileOutputStream out = new FileOutputStream(temp);
            session.exportTo(input, out);
            out.close();

            return temp;
        } catch (Exception ex) {
            throw new ProcessException(ex);
        }
    }
}
