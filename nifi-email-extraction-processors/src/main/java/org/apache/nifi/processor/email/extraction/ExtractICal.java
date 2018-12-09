package org.apache.nifi.processor.email.extraction;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.filter.Filter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.transform.rfc5545.VEventRule;
import net.fortuna.ical4j.util.MapTimeZoneCache;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("All flowfiles that fail extraction are sent to this relationship.")
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All flowfiles that pass extraction are sent to this relationship.")
            .build();

    public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(REL_SUCCESS, REL_FAILURE)));

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) {
            return;
        }

        try (InputStream is = session.read(input)) {
            Calendar calendar = new CalendarBuilder().build(is);
            ComponentList list = calendar.getComponents();
            for (int index = 0; index < list.size(); index++) {
                Object current = list.get(index);
                if (current instanceof VEvent) {
                    VEvent event = (VEvent)current;
                    getLogger().error(event.toString());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            getLogger().error("Could not parse", ex);
            session.transfer(input, REL_FAILURE);
        }

        session.remove(input);
    }
}
