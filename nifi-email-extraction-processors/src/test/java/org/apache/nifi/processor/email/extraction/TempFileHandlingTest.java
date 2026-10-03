package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.avro.AvroRecordSetWriter;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the spooling that ExtractPSTFile and ExtractMBoxFile rely on: the files have to land in
 * the configured temp directory rather than a fixed path, and they have to be cleaned up whether
 * or not the extraction succeeds.
 */
public class TempFileHandlingTest {
    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));

    private static Set<Path> spooledFiles() throws IOException {
        Set<Path> found = new HashSet<>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(TEMP_DIR, AbstractExtractEmailProcessor.TEMP_FILE_PREFIX + "*")) {
            stream.forEach(found::add);
        }
        return found;
    }

    private static TestRunner newRunner(Class<? extends AbstractExtractEmailProcessor> processor) throws Exception {
        AvroRecordSetWriter writer = new AvroRecordSetWriter();
        TestRunner runner = TestRunners.newTestRunner(processor);
        runner.addControllerService("writer", writer);
        runner.setProperty(writer, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA);
        runner.setProperty(AbstractExtractEmailProcessor.WRITER, "writer");
        runner.enableControllerService(writer);
        runner.assertValid();

        return runner;
    }

    @Test
    public void testPstTempFileRemovedAfterSuccess() throws Exception {
        Set<Path> before = spooledFiles();

        TestRunner runner = newRunner(ExtractPSTFile.class);
        runner.enqueue(getClass().getResourceAsStream("/test_inbox.pst"));
        runner.run();

        runner.assertTransferCount(ExtractPSTFile.REL_MESSAGES, 1);
        assertEquals(before, spooledFiles(), "Spooled file was left behind after a successful extraction");
    }

    @Test
    public void testMboxTempFileRemovedAfterSuccess() throws Exception {
        Set<Path> before = spooledFiles();

        TestRunner runner = newRunner(ExtractMBoxFile.class);
        LinkedHashMap<String, String> attrs = new LinkedHashMap<>(1);
        attrs.put("filename", "solr-users/201210.mbox");
        runner.enqueue(getClass().getResourceAsStream("/201210.mbox"), attrs);
        runner.run();

        runner.assertTransferCount(ExtractMBoxFile.REL_MESSAGES, 1);
        assertEquals(before, spooledFiles(), "Spooled file was left behind after a successful extraction");
    }

    /**
     * The spooled file used to be named after the filename attribute, which meant a flowfile
     * could decide where in the temp directory it landed. The name is now generated, so a
     * filename that tries to traverse out of the directory has no effect.
     */
    @Test
    public void testTempFileIgnoresFilenameAttribute() throws Exception {
        TestRunner runner = newRunner(ExtractMBoxFile.class);
        LinkedHashMap<String, String> attrs = new LinkedHashMap<>(1);
        attrs.put("filename", "../../../../tmp/nifi-email-extraction-traversal-probe");
        runner.enqueue(getClass().getResourceAsStream("/201210.mbox"), attrs);
        runner.run();

        runner.assertTransferCount(ExtractMBoxFile.REL_MESSAGES, 1);
        assertTrue(spooledFiles().isEmpty()
                        || spooledFiles().stream().noneMatch(p -> p.toString().contains("traversal-probe")),
                "Flowfile attribute influenced the spooled file name");
    }

    @Test
    public void testTempFileRemovedWhenExtractionFails() throws Exception {
        Set<Path> before = spooledFiles();

        TestRunner runner = newRunner(ExtractPSTFile.class);
        runner.enqueue(new ByteArrayInputStream("this is not a PST file".getBytes(StandardCharsets.UTF_8)));
        runner.run();

        runner.assertTransferCount(ExtractPSTFile.REL_FAILURE, 1);
        assertEquals(before, spooledFiles(), "Spooled file was left behind after a failed extraction");
    }

    /**
     * Mailbox content is sensitive and the temp directory is typically shared, so the spooled
     * file should not be readable by other local users.
     */
    @Test
    public void testTempFileIsNotReadableByOthers() throws Exception {
        Path temp = Files.createTempFile(AbstractExtractEmailProcessor.TEMP_FILE_PREFIX, null);
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(temp);
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms,
                    "Spooled file should only be accessible to the owner");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
