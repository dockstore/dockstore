package io.dockstore.swagger;

import java.lang.annotation.Annotation;

import io.swagger.client.model.Workflow;
import org.junit.Assert;
import org.junit.Test;

public class EnforceAnnotationsTest {

    @Test
    public void checkAnnotations() {
        // we had a bizarre issue where the Workflow would lose an annotation that was required for dockstore-cli to work properly
        Workflow workflow = new Workflow();
        Class<? extends Workflow> cls = workflow.getClass();
        final Annotation[] annotations = cls.getAnnotations();
        boolean hasJsonSubTypes = false;
        for (Annotation an : annotations) {
            final String toString = an.toString();
            if (toString.startsWith("@com.fasterxml.jackson.annotation.JsonSubTypes(") && toString
                .contains("name=\"Service\", value=io.swagger.client.model.Service.class") && toString
                .contains("name=\"BioWorkflow\", value=io.swagger.client.model.BioWorkflow.class")) {
                hasJsonSubTypes = true;
            }
        }
        Assert.assertTrue(hasJsonSubTypes);
    }

}
