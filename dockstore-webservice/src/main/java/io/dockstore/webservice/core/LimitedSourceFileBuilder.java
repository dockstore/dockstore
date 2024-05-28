package io.dockstore.webservice.core;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.helpers.SourceFileHelper;

public class LimitedSourceFileBuilder {

    private DescriptorLanguage.FileType type;
    private String content;
    private String path;
    private String absolutePath;

    public LimitedSourceFileBuilder() {
    }

    public FirstStep start() {
        return new FirstStep();
    }

    public class FirstStep extends TypeStep {
    }

    public class TypeStep {
        public ContentStep type(DescriptorLanguage.FileType newType) {
            type = newType;
            return new ContentStep();
        }
    }

    public class ContentStep {
        public PathStep content(String newContent) {
            content = newContent;
            return new PathStep();
        }
    }

    public class PathStep {
        public AbsolutePathStep path(String newPath) {
            path = newPath;
            return new AbsolutePathStep();
        }
        public BuildStep paths(String newPath) {
            return path(newPath).absolutePath(newPath);
        }
    }

    public class AbsolutePathStep {
        public BuildStep absolutePath(String newAbsolutePath) {
            absolutePath = newAbsolutePath;
            return new BuildStep();
        }
    }

    public class BuildStep {
        public SourceFile build() {
            // TODO add sourcefile construction logic 
            // LOG.error("BUILD SOURCEFILE {} {} {} {}", type, content, path, absolutePath);
            return SourceFileHelper.create(type, content, path, absolutePath);
        }
    }
}
