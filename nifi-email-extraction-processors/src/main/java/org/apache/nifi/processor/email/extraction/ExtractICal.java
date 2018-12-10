package org.apache.nifi.processor.email.extraction;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.util.MapTimeZoneCache;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordSchema;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class ExtractICal extends AbstractProcessor {
    static {
        System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());
        System.setProperty("ical4j.unfolding.relaxed","true");
        System.setProperty("ical4j.parsing.relaxed", "true");
        System.setProperty("ical4j.validation.relaxed", "true");
        System.setProperty("ical4j.compatibility.outlook", "true");
    }

    public static final PropertyDescriptor WRITER_FACTORY = new PropertyDescriptor.Builder()
        .name("writer-factory")
        .displayName("Result Writer")
        .description("A writer service that will convert the output.")
        .identifiesControllerService(RecordSetWriterFactory.class)
        .required(true)
        .build();

    public static final List<PropertyDescriptor> PROPERTY_DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
        WRITER_FACTORY
    ));

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return PROPERTY_DESCRIPTORS;
    }

    public static final Relationship REL_FAILURE = new Relationship.Builder()
        .name("failure")
        .description("All flowfiles that fail extraction are sent to this relationship.")
        .build();

    public static final Relationship REL_ORIGINAL = new Relationship.Builder()
        .name("original")
        .description("All of the original input flowfiles go to this relationship when the extraction is successful")
        .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
        .name("success")
        .description("All flowfiles that pass extraction are sent to this relationship.")
        .build();

    public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_SUCCESS, REL_FAILURE, REL_ORIGINAL
    )));

    public static final RecordSchema SCHEMA = AvroTypeUtil.createSchema(CalendarEntryRecord.SCHEMA$);

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    private volatile RecordSetWriterFactory factory;

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        factory = context.getProperty(WRITER_FACTORY).asControllerService(RecordSetWriterFactory.class);
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) {
            return;
        }

        FlowFile output = session.create(input);
        try (InputStream is = session.read(input);
             OutputStream os = session.write(output)) {
            Calendar calendar = new CalendarBuilder().build(is);
            ComponentList list = calendar.getComponents();

            RecordSetWriter writer = factory.createWriter(getLogger(), SCHEMA, os);
            writer.beginRecordSet();
            for (int index = 0; index < list.size(); index++) {
                Object current = list.get(index);
                if (current instanceof VEvent) {
                    VEvent event = (VEvent)current;
                    Map<String, Object> converted = buildEvent(event);
                    Record record = new MapRecord(SCHEMA, converted);
                    writer.write(record);
                }
            }
            writer.finishRecordSet();
            writer.close();
            is.close();
            os.close();

            output = session.putAttribute(output, "record.count", String.valueOf(list.size()));

            session.transfer(input, REL_ORIGINAL);
            session.transfer(output, REL_SUCCESS);
        } catch (Exception ex) {
            session.remove(output);
            ex.printStackTrace();
            getLogger().error("Could not parse", ex);
            session.transfer(input, REL_FAILURE);
        }
    }

    private Long getDate(DateProperty property) {
        if (property == null) {
            return null;
        } else {
            return property.getDate().getTime();
        }
    }

    private Map<String, Object> buildEvent(VEvent event) {
        Map<String, Object> retVal = new HashMap<>();
        retVal.put("name", event.getName());
        retVal.put("description", event.getDescription());
        retVal.put("summary", event.getSummary());
        retVal.put("date_created", getDate(event.getCreated()));
        retVal.put("start_date", getDate(event.getStartDate()));
        retVal.put("end_date", getDate(event.getEndDate()));

        return retVal;
    }
}
