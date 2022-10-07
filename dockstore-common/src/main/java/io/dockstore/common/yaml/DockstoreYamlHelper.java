package io.dockstore.common.yaml;

import io.dockstore.common.DescriptorLanguageSubclass;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

public final class DockstoreYamlHelper {

    public static final String ERROR_READING_DOCKSTORE_YML = "Error reading .dockstore.yml: ";
    public static final String UNKNOWN_PROPERTY = "Unknown property: ";
    public static final Pattern WRONG_KEY_PATTERN = Pattern.compile("Unable to find property '(.+)'");
    private static final int INVALID_VALUE_ECHO_LIMIT = 80;

    enum Version {
        ONE_ZERO("1.0") {
            @Override
            public DockstoreYaml readAndValidateDockstoreYaml(final String content, boolean validateEntries) throws DockstoreYamlException {
                final DockstoreYaml10 dockstoreYaml10 = readDockstoreYaml10(content);
                validate(dockstoreYaml10);
                return dockstoreYaml10;
            }

            @Override
            public void validateDockstoreYamlProperties(final String content) throws DockstoreYamlException {
                return; // Don't validate properties for 1.0
            }
        },
        ONE_ONE("1.1") {
            @Override
            public DockstoreYaml readAndValidateDockstoreYaml(String content, boolean validateEntries) throws DockstoreYamlException {
                final DockstoreYaml11 dockstoreYaml11 = readDockstoreYaml11(content);
                validate(dockstoreYaml11);
                return dockstoreYaml11;
            }

            @Override
            public void validateDockstoreYamlProperties(final String content) throws DockstoreYamlException {
                checkForUnknownProperty(DockstoreYaml11.class, content);
            }
        },
        ONE_TWO("1.2") {
            @Override
            public DockstoreYaml readAndValidateDockstoreYaml(String content, boolean validateEntries) throws DockstoreYamlException {
                final DockstoreYaml12 dockstoreYaml12 = readDockstoreYaml12(content);
                validate(dockstoreYaml12, validateEntries ? v -> true : DockstoreYamlHelper::doesNotReferenceWorkflowish);
                return dockstoreYaml12;
            }

            @Override
            public void validateDockstoreYamlProperties(final String content) throws DockstoreYamlException {
                checkForUnknownProperty(DockstoreYaml12.class, content);
            }
        };

        private final String version;

        Version(String version) {
            this.version = version;
        }

        public abstract DockstoreYaml readAndValidateDockstoreYaml(String content, boolean validateEntries) throws DockstoreYamlException;

        public abstract void validateDockstoreYamlProperties(String content) throws DockstoreYamlException;

        public static Optional<Version> findVersion(final String versionString) {
            return Stream.of(values()).filter(v -> v.version.equals(versionString)).findFirst();
        }
    }

    public static final String DOCKSTORE_YML_MISSING_VALID_VERSION = ".dockstore.yml missing valid version";

    private static final Logger LOG = LoggerFactory.getLogger(DockstoreYamlHelper.class);
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\s*((dockstoreVersion)|(version))\\s*:\\s*(?<version>\\S+)$", Pattern.MULTILINE);

    private DockstoreYamlHelper() {
    }

    public static DockstoreYaml12 readAsDockstoreYaml12(final String content) throws DockstoreYamlException {
        return readAsDockstoreYaml12(content, false);
    }

    /**
     * Reads a .dockstore.yml and returns a DockstoreYaml12 object, if possible. It's possible if the .dockstore.yml
     * is version 1.1 or 1.2, but not 1.0.
     * @param content
     * @param validateEntries
     * @return a DockstoreYaml12
     * @throws DockstoreYamlException
     */
    public static DockstoreYaml12 readAsDockstoreYaml12(final String content, boolean validateEntries) throws DockstoreYamlException {
        final DockstoreYaml dockstoreYaml = readDockstoreYaml(content, validateEntries);
        if (dockstoreYaml instanceof DockstoreYaml12) {
            return (DockstoreYaml12)dockstoreYaml;
        } else if (dockstoreYaml instanceof DockstoreYaml11) {
            return convert11To12((DockstoreYaml11)dockstoreYaml);
        } else {
            throw new DockstoreYamlException("not a valid .dockstore.yml version 1.1 or 1.2");
        }
    }

    /**
     * Reads a .dockstore.yml and returns a DockstoreYaml10 object, if possible. Throws an exception otherwise
     * @param content
     * @return a DockstoreYaml10
     * @throws DockstoreYamlException
     */
    public static DockstoreYaml10 readDockstoreYaml10(final String content) throws DockstoreYamlException {
        Constructor constructor = new Constructor(DockstoreYaml10.class);
        constructor.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                return super.getProperty(type, "class".equals(name) ? "clazz" : name);
            }

        });
        return readContent(content, constructor, true);
    }

    static DockstoreYaml readDockstoreYaml(final String content) throws DockstoreYamlException {
        return readDockstoreYaml(content, true);
    }

    static DockstoreYaml readDockstoreYaml(final String content, boolean validateEntries) throws DockstoreYamlException {
        final Optional<Version> maybeVersion = findValidVersion(content);
        if (maybeVersion.isPresent()) {
            return maybeVersion.get().readAndValidateDockstoreYaml(content, validateEntries);
        }
        throw new DockstoreYamlException(DOCKSTORE_YML_MISSING_VALID_VERSION);
    }

    /**
     * Converts a DockstoreYaml11 to a DockstoreYaml12
     * @param dockstoreYaml11
     * @return a DockstoreYaml11
     * @throws DockstoreYamlException
     */
    private static DockstoreYaml12 convert11To12(final DockstoreYaml11 dockstoreYaml11) throws DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml12 = new DockstoreYaml12();
        dockstoreYaml12.setVersion(Version.ONE_TWO.version);
        final Service12 service12 = new Service12();
        try {
            final YamlService11 service11 = dockstoreYaml11.getService();
            BeanUtils.copyProperties(service12, service11);
            final DescriptorLanguageSubclass descriptorLanguageSubclass = DescriptorLanguageSubclass
                    .convertShortNameStringToEnum(service11.getType());
            service12.setSubclass(descriptorLanguageSubclass);
            dockstoreYaml12.setService(service12);
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
     * @return an optional version
     */
    static Optional<Version> findValidVersion(final String content) {
        final Matcher matcher = VERSION_PATTERN.matcher(content);
        if (matcher.find()) {
            final String version = matcher.group("version");
            return Version.findVersion(version);
        }
        return Optional.empty();
    }


    private static DockstoreYaml11 readDockstoreYaml11(final String content) throws DockstoreYamlException {
        return readContent(content, new Constructor(DockstoreYaml11.class), true);
    }

    private static DockstoreYaml12 readDockstoreYaml12(final String content) throws DockstoreYamlException {
        return readContent(content, new Constructor(DockstoreYaml12.class), true);
    }

    private static <T> T readContent(final String content, final Constructor constructor, final boolean skipUnknownProperties) throws DockstoreYamlException {
        try {
            // first check to make sure there aren't any unsafe types
            final Yaml safeYaml = new Yaml(new SafeConstructor());
            safeYaml.load(content);
            Representer representer = new Representer();
            representer.getPropertyUtils().setSkipMissingProperties(skipUnknownProperties);
            DumperOptions dumperOptions = new DumperOptions();
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setAllowDuplicateKeys(false);
            final Yaml yaml = new Yaml(constructor, representer, dumperOptions, loaderOptions);
            return yaml.load(content);
        } catch (Exception e) {
            final String exceptionMsg = e.getMessage();
            LOG.error(ERROR_READING_DOCKSTORE_YML + exceptionMsg, e);
            throw new DockstoreYamlException(exceptionMsg);
        }
    }

    /**
     * Validates the .dockstore.yml properties. An exception is ONLY thrown if there's an unknown property.
     * An initial reading of the content (using a method like readAsDockstoreYaml12, for example) should be performed prior to calling this method to catch other validation errors.
     * @param content .dockstore.yml content
     * @throws DockstoreYamlException Exception is thrown only if an unknown property is found in the content.
     */
    public static void validateDockstoreYamlProperties(final String content) throws DockstoreYamlException {
        final Optional<Version> maybeVersion = findValidVersion(content);
        if (maybeVersion.isPresent()) {
            maybeVersion.get().validateDockstoreYamlProperties(content);
        }
    }

    /**
     * Checks to see if an unknown property exists in the .dockstore.yml. If there is, an exception is thrown with an error message containing
     * the unknown property and a suggested property (if one exists).
     * An exception is ONLY thrown if there's an unknown property. Will not throw for other yaml validation errors.
     * @param dockstoreYamlClass DockstoreYaml class. Allowed values: DockstoreYaml12.class, DockstoreYaml11.class
     * @param content .dockstore.yml content
     * @throws DockstoreYamlException Exception is thrown only if an unknown property is found in the content
     */
    private static void checkForUnknownProperty(final Class<? extends DockstoreYaml> dockstoreYamlClass, final String content) throws DockstoreYamlException {
        try {
            readContent(content, new Constructor(dockstoreYamlClass), false);
        } catch (DockstoreYamlException ex) {
            String exceptionMessage = ex.getMessage();
            final Matcher matcher = WRONG_KEY_PATTERN.matcher(exceptionMessage);

            if (matcher.find()) {
                String unknownProperty = matcher.group(1);
                String suggestedProperty = getSuggestedDockstoreYamlProperty(dockstoreYamlClass, unknownProperty);
                String errorMessage = UNKNOWN_PROPERTY + String.format("'%s'.", unknownProperty);
                if (!suggestedProperty.isEmpty()) {
                    errorMessage += String.format(" Did you mean: '%s'?", suggestedProperty);
                }
                LOG.info(ERROR_READING_DOCKSTORE_YML + errorMessage, ex);
                throw new DockstoreYamlException(errorMessage);
            }
            LOG.info(ex.getMessage(), ex);
        }
    }

    /**
     * Tries to find a valid .dockstore.yml property that is the closest to the unknown property according to its Levenshtein distance.
     * If a suggested property cannot be found, then an empty string is returned.
     * @param dockstoreYamlClass The DockstoreYaml class. Allowed values: DockstoreYaml12.class, Dockstore11.class
     * @param unknownProperty The unknown property to find a suggestion for.
     * @return A suggested property if one is found or an empty string if a suggested property cannot be found.
     */
    public static String getSuggestedDockstoreYamlProperty(Class<? extends DockstoreYaml> dockstoreYamlClass, String unknownProperty) {
        Set<String> validProperties = getDockstoreYamlProperties(dockstoreYamlClass);
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        int shortestDistance = Integer.MAX_VALUE;
        String shortestDistanceProperty = "";

        if (validProperties.contains(unknownProperty) || unknownProperty.isEmpty()) {
            return unknownProperty;
        }

        for (String validProperty : validProperties) {
            int distance = levenshteinDistance.apply(unknownProperty, validProperty);
            if (distance < shortestDistance) {
                shortestDistance = distance;
                shortestDistanceProperty = validProperty;
            }
        }

        // Return the property if the number of changes needed to be made is less than the length of the unknown property.
        // This is to prevent suggestions that don't make sense.
        if (shortestDistance < unknownProperty.length()) {
            return shortestDistanceProperty;
        } else {
            return "";
        }
    }

    /**
     * Gets all the properties of a .dockstore.yml as a list of strings.
     * The convention that makes this work is that all the property fields in the DockstoreYaml classes are private.
     * Uses bread-first search to find all the classes.
     * @param dockstoreYamlClass The DockstoreYaml class. Allowed values: DockstoreYaml12.class, DockstoreYaml11.class
     * @return A set of properties belonging to the .dockstore.yml version
     */
    public static Set<String> getDockstoreYamlProperties(Class<? extends DockstoreYaml> dockstoreYamlClass) {
        Set<String> properties = new HashSet<>();
        Queue<Class> dockstoreYmlPropertiesQueue = new ArrayDeque<>(); // A queue to process the property classes
        List<Class> discoveredClasses = new ArrayList<>();
        final String dockstoreYamlPackageName = dockstoreYamlClass.getPackageName();

        discoveredClasses.add(dockstoreYamlClass);
        dockstoreYmlPropertiesQueue.add(dockstoreYamlClass); // Add the high level class to the queue

        while (!dockstoreYmlPropertiesQueue.isEmpty()) {
            Class propertyClass = dockstoreYmlPropertiesQueue.poll(); // Get a class in the queue to process
            Field[] declaredFields = propertyClass.getDeclaredFields(); // Get all the fields declared in the class

            // Go through each field and determine if the field needs to be parsed further for properties
            for (Field field : declaredFields) {
                if (Modifier.isPublic(field.getModifiers())) {
                    continue; // By our own convention, public fields are not part of the .dockstore.yml properties
                }

                // The field name is a .dockstore.yml property
                properties.add(field.getName());

                // Determine the class of the field
                Class fieldClass = field.getType();
                Type fieldGenericType = field.getGenericType(); // This is the field's generic type, if it has one. If it doesn't, this is just the field's class
                if  (fieldGenericType instanceof ParameterizedType) {
                    // Example: List<YamlWorkflow> is a parameterized type. We want to get the class YamlWorkflow
                    Type[] parameterizedTypes = ((ParameterizedType) fieldGenericType).getActualTypeArguments();
                    // There can be more than one parameterized type if it's something like Map<String, String>
                    for (Type type: parameterizedTypes) {
                        String className = type.getTypeName();
                        try {
                            fieldClass = Class.forName(className);
                        } catch (ClassNotFoundException ex) {
                            LOG.error("Could not get the class object for {}", className, ex);
                            continue;
                        }
                        addNewPropertyClassToQueue(dockstoreYamlPackageName, fieldClass, discoveredClasses, dockstoreYmlPropertiesQueue);
                    }
                } else {
                    addNewPropertyClassToQueue(dockstoreYamlPackageName, fieldClass, discoveredClasses, dockstoreYmlPropertiesQueue);
                }
            }
        }

        return properties;
    }

    /**
     * Adds a class to the queue if it belongs in the io.dockstore.common.yaml package and it hasn't been processed yet.
     * Also checks if the class has a super class and adds that to the queue if it's valid.
     * @param dockstoreYamlPackageName The package name of the DockstoreYaml class
     * @param propertyClass The class to add to the queue if it's valid
     * @param discoveredClasses List of classes that have been discovered and processed
     * @param classQueue Queue of classes waiting to be processed
     */
    private static void addNewPropertyClassToQueue(String dockstoreYamlPackageName, Class propertyClass, List<Class> discoveredClasses, Queue classQueue) {
        // Check if the class is in the io.dockstore.common.yaml package to prevent classes like String from being added to the queue
        if (propertyClass.getPackageName().equals(dockstoreYamlPackageName) && !discoveredClasses.contains(propertyClass)) {
            discoveredClasses.add(propertyClass);
            classQueue.add(propertyClass);

            // Check if the superclass is a valid .dockstore.yml class. Ex: Service12's superclass is AbstractYamlService
            addNewPropertyClassToQueue(dockstoreYamlPackageName, propertyClass.getSuperclass(), discoveredClasses, classQueue);
        }
    }

    public static <T> void validate(final T validatee) throws DockstoreYamlException {
        validate(validatee, x -> true);
    }

    public static <T> void validate(final T validatee, Predicate<ConstraintViolation<T>> includeViolation) throws DockstoreYamlException {
        final Validator validator = createValidator();
        final Set<ConstraintViolation<T>> violations = validator.validate(validatee).stream().filter(includeViolation).collect(Collectors.toSet());
        if (!violations.isEmpty()) {
            throw new DockstoreYamlException(
                violations.stream()
                    // The violations come back unordered in a HashSet.
                    // Sort them lexicographically by property path (ex "workflows[0].author[0].name").
                    // The result doesn't match their order in the yaml file, but is probably good enough for now...
                    .sorted((a, b) -> a.getPropertyPath().toString().compareTo(b.getPropertyPath().toString()))
                    .map(v -> buildMessageFromViolation(v))  // NOSONAR a lambda is more understandable than method reference here
                    .collect(Collectors.joining("; ")));
        }
    }

    private static <T> String buildMessageFromViolation(ConstraintViolation<T> violation) {
        String message = violation.getMessage();

        // If the violation contains a non-empty property path, add it to the message, with a summary of the invalid value, if present.
        javax.validation.Path propertyPath = violation.getPropertyPath();
        if (propertyPath != null) {
            String propertyPathString = propertyPath.toString();
            if (propertyPathString != null && !propertyPathString.isEmpty()) {
                message = String.format("Property \"%s\" %s", propertyPathString, message);
                Object invalidValue = violation.getInvalidValue();
                // If there's a non-null invalid value, and it's a simple object (not the invalid bean), include it in the message.
                if (invalidValue != null && violation.getLeafBean() != invalidValue) {
                    message = String.format("%s (current value: \"%s\")", message, StringUtils.abbreviate(invalidValue.toString(), INVALID_VALUE_ECHO_LIMIT));
                }
            }
        }

        return message;
    }

    private static <T> boolean doesNotReferenceWorkflowish(ConstraintViolation<T> violation) {
        javax.validation.Path propertyPath = violation.getPropertyPath();
        if (propertyPath == null) {
            return true;
        }
        String path = propertyPath.toString();
        return !(path.startsWith("workflows[") || path.startsWith("tools[") || path.startsWith("service."));
    }

    private static Validator createValidator() {
        final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        return validatorFactory.getValidator();
    }

    /**
     * Decide whether a gitReference is excluded, given a workflow/service's filters
     * @param gitRefPath Path.of(gitReference) for glob matching with PathMatcher
     * @param filters Filters specified for a workflow/service in .dockstore.yml
     * @return
     */
    public static boolean filterGitReference(final Path gitRefPath, final Filters filters) {
        final List<String> branches = filters.getBranches();
        final List<String> tags = filters.getTags();

        // If no filters specified, accept anything
        if (branches.isEmpty() && tags.isEmpty()) {
            return true;
        }

        List<String> patterns;
        if (gitRefPath.startsWith("refs/heads/")) {
            patterns = branches;
        } else if (gitRefPath.startsWith("refs/tags/")) {
            patterns = tags;
        } else {
            throw new UnsupportedOperationException("Invalid git reference: " + gitRefPath.toString());
        }

        // Remove refs/heads/ or refs/tags/ from Path for matching
        final Path matchPath = gitRefPath.subpath(2, gitRefPath.getNameCount());
        return patterns.stream().anyMatch(pattern -> {
            String matcherString;
            // Use regex if pattern string is surrounded by /, otherwise use glob
            if (pattern.matches("^\\/.*\\/$")) {
                matcherString = "regex:" + pattern.substring(1, pattern.length() - 1);
            } else {
                matcherString = "glob:" + pattern;
            }
            try {
                final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(matcherString);
                return pathMatcher.matches(matchPath);
            } catch (PatternSyntaxException | UnsupportedOperationException e) {
                final String msg = ERROR_READING_DOCKSTORE_YML + e.getMessage();
                LOG.warn(msg, e);
                return false;
            }
        });
    }

    public static class DockstoreYamlException extends Exception {
        public DockstoreYamlException(final String msg) {
            super(msg);
        }
    }

}
