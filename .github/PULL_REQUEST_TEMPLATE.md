**Description**
A description of the PR, should include a decent explanation as to why this change was needed and a decent explanation as to what this change does

**Review Instructions**
Describe if this ticket needs review and if so, how one may go about it in qa and/or staging environments.
For example, a ticket based on Security Hub, Snyk, or Dependabot may not need review since those services 
will generate new warnings if the issue has not been resolved properly. On the other hand, an infrastructure
ticket that results in visible changes to the end-user will definitely require review. 
Many tickets will likely be between these two extremes, so some judgement may be required.

**Issue**
A link to a github issue or SEAB- ticket (using that as a prefix)

**Security and Privacy**

If there are any concerns that require extra attention from the security team, highlight them here.

e.g. Does this change...
* Any user data we collect, or data location?
* Access control, authentication or authorization?
* Encryption features?

Please make sure that you've checked the following before submitting your pull request. Thanks!

- [ ] Check that you pass the basic style checks and unit tests by running `mvn clean install`
- [ ] Ensure that the PR targets the correct branch. Check the milestone or fix version of the ticket.
- [ ] Follow the existing JPA patterns for queries, using named parameters, to avoid SQL injection
- [ ] If you are changing dependencies, check the Snyk status check or the dashboard to ensure you are not introducing new high/critical vulnerabilities
- [ ] Assume that inputs to the API can be malicious, and sanitize and/or check for Denial of Service type values, e.g., massive sizes
- [ ] Do not serve user-uploaded binary images through the Dockstore API
- [ ] Ensure that endpoints that only allow privileged access enforce that with the `@RolesAllowed` annotation
- [ ] Do not create cookies, although this may change in the future
- [ ] If this PR is for a user-facing feature, create and link a documentation ticket for this feature (usually in the same milestone as the linked issue). Style points if you create a documentation PR directly and link that instead. 
