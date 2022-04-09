package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.avro.AvroRecordSetWriter;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExtractICalTest {
    @Test
    public void test() throws Exception {
        TestRunner runner = TestRunners.newTestRunner(ExtractICal.class);
        AvroRecordSetWriter service = new AvroRecordSetWriter();
        runner.addControllerService("service", service);
        runner.setProperty(service, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA);
        runner.setProperty(ExtractICal.WRITER_FACTORY, "service");
        runner.enableControllerService(service);
        runner.enqueue(getClass().getResourceAsStream("/sample.ics"));
        runner.assertValid();
        runner.run();

        runner.assertTransferCount(ExtractICal.REL_FAILURE, 0);
        runner.assertTransferCount(ExtractICal.REL_ORIGINAL, 1);
        runner.assertTransferCount(ExtractICal.REL_SUCCESS, 1);

        MockFlowFile ff = runner.getFlowFilesForRelationship(ExtractICal.REL_SUCCESS).get(0);
        assertEquals(ff.getAttribute("record.count"), "1");
    }
}
