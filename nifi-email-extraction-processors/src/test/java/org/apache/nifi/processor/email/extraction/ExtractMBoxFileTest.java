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
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ExtractMBoxFileTest {
    @Test
    public void test() throws Exception {
        AvroRecordSetWriter writer = new AvroRecordSetWriter();
        TestRunner runner = TestRunners.newTestRunner(ExtractMBoxFile.class);
        runner.addControllerService("writer", writer);
        runner.setProperty(writer, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA);
        runner.setProperty(ExtractMBoxFile.WRITER, "writer");
        runner.enableControllerService(writer);
        runner.assertValid();

        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
        map.put("filename", "solr-users/201210.mbox");
        runner.enqueue(this.getClass().getResourceAsStream("/201210.mbox"), map);
        runner.run();

        runner.assertTransferCount(ExtractMBoxFile.REL_FAILURE, 0);
        runner.assertTransferCount(ExtractMBoxFile.REL_ORIGINAL, 1);
        runner.assertTransferCount(ExtractMBoxFile.REL_MESSAGES, 1);

        List<MockFlowFile> messagesFF = runner.getFlowFilesForRelationship(ExtractMBoxFile.REL_MESSAGES);
        byte[] raw = runner.getContentAsByteArray(messagesFF.get(0));
        AvroReaderWithEmbeddedSchema reader = new AvroReaderWithEmbeddedSchema(new ByteArrayInputStream(raw));
        int count = 0;
        Record record = reader.nextRecord();

        while (record != null) {
            count++;
            record = reader.nextRecord();
        }


        assertEquals(213, count);
    }
}
