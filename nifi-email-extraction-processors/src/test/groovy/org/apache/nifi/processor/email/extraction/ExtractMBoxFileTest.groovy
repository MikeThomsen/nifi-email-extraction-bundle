package org.apache.nifi.processor.email.extraction

import org.apache.nifi.avro.AvroReaderWithEmbeddedSchema
import org.apache.nifi.avro.AvroRecordSetWriter
import org.apache.nifi.schema.access.SchemaAccessUtils
import org.apache.nifi.util.TestRunners
import org.junit.Test
import org.junit.Ignore

@Ignore
class ExtractMBoxFileTest {
    @Test
    void test() {
        def writer = new AvroRecordSetWriter()
        def runner = TestRunners.newTestRunner(ExtractMBoxFile.class)
        runner.addControllerService("writer", writer)
        runner.setProperty(writer, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA)
        runner.setProperty(ExtractMBoxFile.WRITER, "writer")
        runner.enableControllerService(writer)
        runner.assertValid()

        runner.enqueue(this.class.getResourceAsStream("/201210.mbox"), [filename: "solr-users/201210.mbox"])
        runner.run()

        runner.assertTransferCount(ExtractMBoxFile.REL_FAILURE, 0)
        runner.assertTransferCount(ExtractMBoxFile.REL_ORIGINAL, 1)
        runner.assertTransferCount(ExtractMBoxFile.REL_MESSAGES, 1)

        def messagesFF = runner.getFlowFilesForRelationship(ExtractMBoxFile.REL_MESSAGES)
        def raw        = runner.getContentAsByteArray(messagesFF)
        def reader     = new AvroReaderWithEmbeddedSchema(new ByteArrayInputStream(raw))
        int count = 0
        def record = reader.nextRecord()

        while (record != null) {
            count++
            record = reader.nextRecord()
        }

        assert count == 213
    }
}
