package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.avro.AvroRecordSetWriter;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

public class ExtractEMLFileTest {
    @Test
    public void test() throws Exception {
        AvroRecordSetWriter writer = new AvroRecordSetWriter();
        TestRunner runner = TestRunners.newTestRunner(ExtractEMLFile.class);
        runner.addControllerService("writer", writer);
        runner.setProperty(writer, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA);
        runner.setProperty(ExtractMBoxFile.WRITER, "writer");
        runner.enableControllerService(writer);
        runner.assertValid();

        runner.enqueue(this.getClass().getResourceAsStream("/test_sample_message.eml"));
        runner.run();

        runner.assertTransferCount(ExtractEMLFile.REL_FAILURE, 0);
        runner.assertTransferCount(ExtractEMLFile.REL_ATTACHMENTS, 3);
        runner.assertTransferCount(ExtractEMLFile.REL_MESSAGES, 1);
        runner.assertTransferCount(ExtractEMLFile.REL_ORIGINAL, 1);
    }

}
