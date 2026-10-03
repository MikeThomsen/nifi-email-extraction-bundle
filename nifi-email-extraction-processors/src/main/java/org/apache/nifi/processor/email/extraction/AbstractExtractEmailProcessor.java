package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.RecordSetWriterFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /**
     * Prefix for the temporary files spooled below. The random part of the name is left to the
     * JDK so that nothing in it derives from flowfile attributes.
     */
    static final String TEMP_FILE_PREFIX = "nifi-email-extraction-";

    /**
     * Spools the content of a flowfile into a temporary file, for the benefit of the mailbox
     * libraries that can only read a {@link java.io.File} rather than a stream. The caller owns
     * the returned path and is responsible for passing it to {@link #deleteTempFile(Path)} once
     * it is done, which is best done from a finally block.
     */
    protected Path writeFlowFileToTemp(FlowFile input, ProcessSession session) {
        final Path temp;
        try {
            // Files.createTempFile picks an unpredictable name in the configured temp directory
            // and, on filesystems that support it, creates the file readable only by the owner.
            // Both matter here: the temp directory is usually shared and world readable, and
            // what gets spooled into it is the full contents of someone's mailbox.
            temp = Files.createTempFile(TEMP_FILE_PREFIX, null);
        } catch (IOException e) {
            throw new ProcessException("Could not create a temporary file for extraction.", e);
        }

        try (OutputStream out = Files.newOutputStream(temp)) {
            session.exportTo(input, out);
        } catch (Exception e) {
            // The caller never sees the path, so it would be left behind for good.
            deleteTempFile(temp);
            throw new ProcessException(String.format("Could not spool flowfile content to %s.", temp), e);
        }

        return temp;
    }

    /**
     * Removes a temporary file created by {@link #writeFlowFileToTemp(FlowFile, ProcessSession)}.
     * Failing to clean up is worth a warning but is not worth failing the flowfile over, so this
     * never throws.
     */
    protected void deleteTempFile(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            getLogger().warn("Could not delete temporary file {}.", temp, e);
        }
    }
}
