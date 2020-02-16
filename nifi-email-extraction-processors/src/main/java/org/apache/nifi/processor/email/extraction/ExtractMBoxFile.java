package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.util.StringUtils;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class ExtractMBoxFile extends AbstractJavaMailProcessor {
    static {
        System.setProperty("mstor.cache.disabled", "true");
    }

    private static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
        WRITER, PREFERRED_BODY_TYPE, FOLDER_IDENTIFIER, ERROR_STRATEGY, MISSING_FIELD_STRATEGY, MISSING_FIELD_SUBSTITUTION_VALUE
    ));

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    private volatile RecordSetWriterFactory factory;

    @Override
    protected Collection<ValidationResult> customValidate(ValidationContext context) {
        List<ValidationResult> problems = new ArrayList<>();

        if (context.getProperty(MISSING_FIELD_STRATEGY).isSet()
                && context.getProperty(MISSING_FIELD_STRATEGY).getValue().equals(MISSING_FIELD_VALUE.getValue())) {
            String val = context.getProperty(MISSING_FIELD_SUBSTITUTION_VALUE).getValue();
            if (StringUtils.isEmpty(val)) {
                problems.add(new ValidationResult.Builder()
                    .subject(MISSING_FIELD_SUBSTITUTION_VALUE.getName())
                    .input(val)
                    .valid(false)
                    .build());
            }
        }

        return problems;
    }

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        super.onScheduled(context);
        factory = context.getProperty(WRITER).asControllerService(RecordSetWriterFactory.class);
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) {
            return;
        }

        FlowFile output = session.create(input);
        File _temp = null;
        Store store = null;
        List<FlowFile> attachments = new ArrayList<>();

        String folderIdentifier = context.getProperty(FOLDER_IDENTIFIER).evaluateAttributeExpressions(input).getValue();

        try (OutputStream os = session.write(output)) {
            _temp = writeFlowFileToTemp(input, session);

            RecordSetWriter writer = factory.createWriter(getLogger(), AvroTypeUtil.createSchema(EmailMessage.SCHEMA$), os);
            writer.beginRecordSet();

            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "mstor");
            props.setProperty("mail.mime.address.strict", "false");
            props.setProperty("mstor.mbox.metadataStrategy", "none");
            props.setProperty("mstor.mbox.cacheBuffers", "disabled");
            props.setProperty("mstor.mbox.bufferStrategy", "mapped");
            props.setProperty("mstor.metadata", "disabled");
            Session mSession = Session.getDefaultInstance(props);
            store = mSession.getStore(new URLName("mstor:" + _temp.getAbsolutePath()));
            store.connect();
            Folder folder = store.getDefaultFolder();
            folder.open(Folder.READ_ONLY);

            int count = folder.getMessageCount();

            for (int index = 0; index < count; index++) {
                try {
                    Message msg = folder.getMessage(index + 1);
                    processMessage(folderIdentifier, msg, writer, output, attachments, session);
                } catch (Exception ex) {
                    if (sendToFailure) {
                        folder.close(false);
                        store.close();
                        throw new ProcessException(ex);
                    }
                }
            }
            folder.close(false);
            store.close();
            writer.finishRecordSet();
            writer.close();
            os.close();


            Map<String, String> attrs = new HashMap<>();
            attrs.put("record.count", String.valueOf(count));
            output = session.putAllAttributes(output, attrs);

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_MESSAGES);
            session.getProvenanceReporter().modifyAttributes(input);

            for (FlowFile flowFile : attachments) {
                session.transfer(flowFile, REL_ATTACHMENTS);
            }
        } catch (Exception ex) {
            getLogger().error("Error", ex);

            for (FlowFile attachment : attachments) {
                session.remove(attachment);
            }

            session.remove(output);
            session.transfer(input, REL_FAILURE);
            session.getProvenanceReporter().modifyAttributes(input);
        } finally {
            if (_temp != null) {
                _temp.delete();
            }
        }
    }
}
