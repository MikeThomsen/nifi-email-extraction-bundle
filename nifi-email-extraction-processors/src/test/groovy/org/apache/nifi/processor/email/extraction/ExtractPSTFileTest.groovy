package org.apache.nifi.processor.email.extraction

import org.apache.nifi.avro.AvroReaderWithEmbeddedSchema
import org.apache.nifi.avro.AvroRecordSetWriter
import org.apache.nifi.schema.access.SchemaAccessUtils
import org.apache.nifi.util.TestRunners
import org.junit.Test

class ExtractPSTFileTest {
    @Test
    void test() {
        def service = new AvroRecordSetWriter()
        def runner = TestRunners.newTestRunner(ExtractPSTFile.class)
        runner.addControllerService("writer", service)
        runner.setProperty(service, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA)
        runner.setProperty(ExtractPSTFile.WRITER, "writer")
        runner.enableControllerService(service)
        runner.assertValid()

        runner.enqueue(this.class.getResourceAsStream("/test_inbox.pst"))
        runner.run()

        runner.assertTransferCount(ExtractPSTFile.REL_FAILURE, 0)
        runner.assertTransferCount(ExtractPSTFile.REL_ORIGINAL, 1)
        runner.assertTransferCount(ExtractPSTFile.REL_MESSAGES, 1)
        runner.assertTransferCount(ExtractPSTFile.REL_ATTACHMENTS, 13)

        runner.getFlowFilesForRelationship(ExtractPSTFile.REL_ATTACHMENTS).each { ff ->
            assert ff.getSize() > 0
        }

        def raw = runner.getContentAsByteArray(runner.getFlowFilesForRelationship(ExtractPSTFile.REL_MESSAGES)[0])
        def is  = new ByteArrayInputStream(raw)
        def reader = new AvroReaderWithEmbeddedSchema(is)
        def record = reader.nextRecord()
        int count = 0
        while (record != null) {
            count++
            record = reader.nextRecord()
        }
        assert count == 7
    }
}
