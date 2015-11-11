package io.dockstore.webservice.utils;

import com.dyuproject.protostuff.Schema;
import com.dyuproject.protostuff.runtime.RuntimeSchema;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.User;
import net.webby.protostuff.runtime.Generators;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author dyuen
 */
public class ProtoExporter {
        public static void main(String[] args) throws IOException {
                for(Class currClass : new Class[]{Container.class, User.class, Tag.class, Group.class}){
                        Schema<?> schema = RuntimeSchema.getSchema(currClass);
                        String content = Generators.newProtoGenerator(schema).generate();
                        FileUtils.writeStringToFile(new File(currClass.getSimpleName() + ".proto"), content, StandardCharsets.UTF_8);
                }
        }
}
