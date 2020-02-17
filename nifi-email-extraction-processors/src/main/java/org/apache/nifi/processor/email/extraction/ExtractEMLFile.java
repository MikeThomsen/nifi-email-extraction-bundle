package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.RecordSetWriter;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class ExtractEMLFile extends AbstractJavaMailProcessor {
    public static final PropertyDescriptor FLOWFILE_COUNT = new PropertyDescriptor.Builder()
        .name("extract-eml-flowfile-content")
        .displayName("Flowfile Count")
        .description("The maximum number of flowfiles to pull in per session.")
        .addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
        .defaultValue("1")
        .required(true)
        .build();

    public static final List<PropertyDescriptor> DESCRIPTOR_LIST = Collections.unmodifiableList(Arrays.asList(
        WRITER, PREFERRED_BODY_TYPE, FLOWFILE_COUNT, ERROR_STRATEGY, MISSING_FIELD_STRATEGY, MISSING_FIELD_SUBSTITUTION_VALUE
    ));

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return DESCRIPTOR_LIST;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    private int flowfileCount = 1;

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        super.onScheduled(context);
        flowfileCount = context.getProperty(FLOWFILE_COUNT).evaluateAttributeExpressions().asInteger();
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        List<FlowFile> flowFiles = session.get(flowfileCount);
        if (flowFiles.isEmpty()) {
            return;
        }

        FlowFile messages = session.create(flowFiles);
        try (OutputStream os = session.write(messages)) {
            RecordSetWriter writer = factory.createWriter(getLogger(), AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), os, messages.getAttributes());
            writer.beginRecordSet();

            flowFiles.forEach(flowFile -> {
                List<FlowFile> attachments = new ArrayList<>();
                try (InputStream is = session.read(flowFile)) {

                    Properties props = System.getProperties();
                    props.put("mail.host", "smtp.dummydomain.com");
                    props.put("mail.transport.protocol", "smtp");

                    Session mailSession = Session.getDefaultInstance(props, null);
                    MimeMessage message = new MimeMessage(mailSession, is);

                    processMessage("", message, writer, messages, attachments, session);

                    is.close();

                    session.transfer(flowFile, REL_ORIGINAL);
                    attachments.forEach(attachmentFlowFile -> session.transfer(attachmentFlowFile, REL_ATTACHMENTS));
                } catch (Exception e) {
                    getLogger().error("", e);
                    attachments.forEach(attachmentFlowFile -> session.remove(attachmentFlowFile));
                    session.transfer(flowFile, REL_FAILURE);
                }
            });

            writer.finishRecordSet();
            os.close();

            session.transfer(messages, REL_MESSAGES);

        } catch (Exception ex) {
            getLogger().error("", ex);
            session.remove(messages);
        }
    }
}
