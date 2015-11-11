package io.dockstore.webservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.User;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author dyuen
 */
public class AvroExporter {

        public static void main(String[] args) throws IOException {
                ObjectMapper mapper = new ObjectMapper(new AvroFactory());
                for(Class currClass : new Class[]{Container.class, User.class, Tag.class, Group.class}){
                        AvroSchemaGenerator gen = new AvroSchemaGenerator();
                        mapper.acceptJsonFormatVisitor(currClass, gen);
                        AvroSchema schemaWrapper = gen.getGeneratedSchema();
                        org.apache.avro.Schema avroSchema = schemaWrapper.getAvroSchema();
                        String asJson = avroSchema.toString(true);
                        FileUtils.writeStringToFile(new File(currClass.getSimpleName() + ".json"), asJson, StandardCharsets.UTF_8);
                }
        }
}
