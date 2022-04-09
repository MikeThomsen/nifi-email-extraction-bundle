package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.avro.AvroReaderWithEmbeddedSchema;
import org.apache.nifi.avro.AvroRecordSetWriter;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtractPSTFileTest {
    @Test
    public void test() throws Exception {
        AvroRecordSetWriter service = new AvroRecordSetWriter();
        TestRunner runner = TestRunners.newTestRunner(ExtractPSTFile.class);
        runner.addControllerService("writer", service);
        runner.setProperty(service, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA);
        runner.setProperty(ExtractPSTFile.WRITER, "writer");
        runner.enableControllerService(service);
        runner.assertValid();

        runner.enqueue(this.getClass().getResourceAsStream("/test_inbox.pst"));
        runner.run();

        runner.assertTransferCount(ExtractPSTFile.REL_FAILURE, 0);
        runner.assertTransferCount(ExtractPSTFile.REL_ORIGINAL, 1);
        runner.assertTransferCount(ExtractPSTFile.REL_MESSAGES, 1);
        runner.assertTransferCount(ExtractPSTFile.REL_ATTACHMENTS, 13);

        for (MockFlowFile mockFlowFile : runner.getFlowFilesForRelationship(ExtractPSTFile.REL_ATTACHMENTS)) {
            assertTrue(mockFlowFile.getSize() > 0);
        }

        byte[] raw = runner.getContentAsByteArray(runner.getFlowFilesForRelationship(ExtractPSTFile.REL_MESSAGES).get(0));
        ByteArrayInputStream is = new ByteArrayInputStream(raw);
        AvroReaderWithEmbeddedSchema reader = new AvroReaderWithEmbeddedSchema(is);
        Record record = reader.nextRecord();
        int count = 0;
        while (record != null) {
            count++;
            record = reader.nextRecord();
        }

        assertEquals(7, count);
    }

}
