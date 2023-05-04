We're modifying api and apiService.

Changes include:

 * overriding of tags to organize new operations
 * adding @UnitOfWork
 * adding support for containerContext (for content negotiation) and user (for authorization)
 * overriding of path
 * override tags to GA4GHV20
 * adding @JsonValue annotation to modelEnum
 * changing from JacksonJaxbJsonProvider to JacksonXmlBindJsonProvider to support jakarta migration

Updated selected templates to
Using https://github.com/swagger-api/swagger-codegen-generators/tree/v1.0.25/src/main/resources/handlebars/JavaJaxRS


