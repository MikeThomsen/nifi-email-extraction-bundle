package org.apache.nifi.processor.email.extraction;

import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

public class ExtractEMLFile extends AbstractJavaMailProcessor {
    public static final PropertyDescriptor FLOWFILE_COUNT = new PropertyDescriptor.Builder()
        .name("extract-eml-flowfile-content")
        .displayName("Flowfile Count")
        .description("The maximum number of flowfiles to pull in per session.")
        .addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
        .defaultValue("0")
        .required(true)
        .build();

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        super.onScheduled(context);
    }

    @Override
    public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {

    }
}
