package io.dockstore.common.yaml;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public final class DockstoreYamlHelper {

    enum Version {
        ONE_ZERO("1.0") {
            @Override
            public DockstoreYaml readAndValidateDockstoreYaml(String content) throws DockstoreYamlException {
                final DockstoreYaml10 dockstoreYaml10 = readDockstoreYaml10(content);
                validate(dockstoreYaml10);
                return dockstoreYaml10;
            }
        },
        ONE_ONE("1.1") {
            @Override
            public DockstoreYaml readAndValidateDockstoreYaml(String content) throws DockstoreYamlException {
                final DockstoreYaml11 dockstoreYaml11 = readDockstoreYaml11(content);
                validate(dockstoreYaml11);
                return dockstoreYaml11;
            }
        },
        ONE_TWO("1.2") {
            @Override
            public DockstoreYaml readAndValidateDockstoreYaml(String content) throws DockstoreYamlException {
                final DockstoreYaml12 dockstoreYaml12 = readDockstoreYaml12(content);
                validate(dockstoreYaml12);
                return dockstoreYaml12;
            }
        };

        private final String version;

        Version(String version) {
            this.version = version;
        }

        public abstract DockstoreYaml readAndValidateDockstoreYaml(String content) throws DockstoreYamlException;

        public static Optional<Version> findVersion(String versionString) {
            return Stream.of(values()).filter(v -> v.version.equals(versionString)).findFirst();
        }
    }

    public static final String DOCKSTORE_YML_MISSING_VALID_VERSION = ".dockstore.yml missing valid version";

    private static final Logger LOG = LoggerFactory.getLogger(DockstoreYamlHelper.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\s*((dockstoreVersion)|(version))\\s*:\\s*(?<version>\\S+)$", Pattern.MULTILINE);

    private DockstoreYamlHelper() {
    }

    /**
     * Reads a .dockstore.yml and returns a DockstoreYaml12 object, if possible. It's possible if the .dockstore.yml
     * is version 1.1 or 1.2, but not 1.0.
     * @param content
     * @return a DockstoreYaml12
     * @throws DockstoreYamlException
     */
    public static DockstoreYaml12 readAsDockstoreYaml12(String content) throws DockstoreYamlException {
        final DockstoreYaml dockstoreYaml = readDockstoreYaml(content);
        if (dockstoreYaml instanceof DockstoreYaml12) {
            return (DockstoreYaml12)dockstoreYaml;
        } else if (dockstoreYaml instanceof DockstoreYaml11) {
            return convert11To12((DockstoreYaml11)dockstoreYaml);
        } else {
            throw new DockstoreYamlException("not a valid dockstore.yml version 1.1 or 1.2");
        }
    }

    /**
     * Reads a .dockstore.yml and returns a DockstoreYaml10 object, if possible. Throws an exception otherwise
     * @param content
     * @return a DockstoreYaml10
     * @throws DockstoreYamlException
     */
    public static DockstoreYaml10 readDockstoreYaml10(String content) throws DockstoreYamlException {
        Constructor constructor = new Constructor(DockstoreYaml10.class);
        constructor.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                return super.getProperty(type, "class".equals(name) ? "clazz" : name);
            }

        });
        final Yaml yaml = new Yaml(constructor);
        try {
            DockstoreYaml10 dockstoreYaml = yaml.load(content);
            return dockstoreYaml;
        } catch (Exception e) {
            final String msg = "Error reading .dockstore.yml: " + e.getMessage();
            LOG.error(msg, e);
            throw new DockstoreYamlException(msg);
        }
    }

    static DockstoreYaml readDockstoreYaml(String content) throws DockstoreYamlException {
        final Optional<Version> maybeVersion = findValidVersion(content);
        if (maybeVersion.isPresent()) {
            return maybeVersion.get().readAndValidateDockstoreYaml(content);
        }
        throw new DockstoreYamlException(DOCKSTORE_YML_MISSING_VALID_VERSION);
    }

    /**
     * Converts a DockstoreYaml11 to a DockstoreYaml12
     * @param dockstoreYaml11
     * @return a DockstoreYaml11
     * @throws DockstoreYamlException
     */
    private static DockstoreYaml12 convert11To12(DockstoreYaml11 dockstoreYaml11) throws DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml12 = new DockstoreYaml12();
        dockstoreYaml12.setVersion(Version.ONE_TWO.version);
        final Service12 service12 = new Service12();
        try {
            final YamlService11 service11 = dockstoreYaml11.getService();
            BeanUtils.copyProperties(service12, service11);
            service12.setSubclass(service11.getType());
            dockstoreYaml12.setServices(Collections.singletonList(service12));
            validate(dockstoreYaml12);
            return dockstoreYaml12;
        } catch (UnsupportedOperationException | InvocationTargetException | IllegalAccessException e) {
            final String msg = "Error converting ; " + e.getMessage();
            LOG.error(msg, e);
            throw new DockstoreYamlException(msg);
        }
    }

    /**
     * Searches content for a version at the start of a line, ensuring the value is a recognized version.
     * @param content
     * @return
     */
    static Optional<Version> findValidVersion(String content) {
        final Matcher matcher = VERSION_PATTERN.matcher(content);
        if (matcher.find()) {
            final String version = matcher.group("version");
            return Version.findVersion(version);
        }
        return Optional.empty();
    }


    private static DockstoreYaml11 readDockstoreYaml11(String content) {
        final Constructor constructor = new Constructor(DockstoreYaml11.class);
        final Yaml yaml = new Yaml(constructor);
        return yaml.load(content);
    }

    private static DockstoreYaml12 readDockstoreYaml12(String content) {
        final Constructor constructor = new Constructor(DockstoreYaml12.class);
        final Yaml yaml = new Yaml(constructor);
        return yaml.load(content);
    }

    private static <T> void validate(T validatee) throws DockstoreYamlException {
        final Validator validator = createValidator();
        final Set<ConstraintViolation<T>> violations = validator.validate(validatee);
        if (!violations.isEmpty()) {
            throw new DockstoreYamlException(
                    violations.stream()
                            .map(v -> v.getMessage())
                            .collect(Collectors.joining("; ")));
        }
    }

    private static Validator createValidator() {
        final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        return validatorFactory.getValidator();
    }

    public static class DockstoreYamlException extends Exception {
        public DockstoreYamlException(String msg) {
            super(msg);
        }
    }

}
