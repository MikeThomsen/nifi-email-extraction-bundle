package org.apache.nifi.processor.email.extraction

import org.apache.nifi.avro.AvroRecordSetWriter
import org.apache.nifi.schema.access.SchemaAccessUtils
import org.apache.nifi.util.TestRunners
import org.junit.Test
import org.testng.Assert

class ExtractICalTest {
    @Test
    void test() {
        def runner = TestRunners.newTestRunner(ExtractICal.class)
        def service = new AvroRecordSetWriter()
        runner.addControllerService("service", service)
        runner.setProperty(service, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA)
        runner.setProperty(ExtractICal.WRITER_FACTORY, "service")
        runner.enableControllerService(service)
        runner.enqueue(getClass().getResourceAsStream("/sample.ics"))
        runner.assertValid()
        runner.run()

        runner.assertTransferCount(ExtractICal.REL_FAILURE, 0)
        runner.assertTransferCount(ExtractICal.REL_ORIGINAL, 1)
        runner.assertTransferCount(ExtractICal.REL_SUCCESS, 1)

        def ff = runner.getFlowFilesForRelationship(ExtractICal.REL_SUCCESS)[0]
        Assert.assertEquals(ff.getAttribute("record.count"), "1")
    }
}
