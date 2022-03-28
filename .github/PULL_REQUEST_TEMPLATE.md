**Description**
A description of the PR, should include a decent explanation as to why this change was needed and a decent explanation as to what this change does

**Issue**
A link to a github issue or SEAB- ticket (using that as a prefix)

Please make sure that you've checked the following before submitting your pull request. Thanks!

- [ ] Check that you pass the basic style checks and unit tests by running `mvn clean install`
- [ ] Follow the existing JPA patterns for queries, using named parameters, to avoid SQL injection
- [ ] Check the Snyk dashboard to ensure you are not introducing new high/critical vulnerabilities
- [ ] Assume that inputs to the API can be malicious, and sanitize and/or check for Denial of Service type values, e.g., massive sizes
- [ ] Do not serve user-uploaded binary images through the Dockstore API
- [ ] Ensure that endpoints that only allow privileged access enforce that with the `@RolesAllowed` annotation
- [ ] Do not create cookies, although this may change in the future
