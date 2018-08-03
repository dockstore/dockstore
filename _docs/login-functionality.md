---
title: Login Functionality
permalink: /docs/publisher-tutorials/login-functionality/
---

# Login Functionality

Currently there are two third party application to login with: 
- GitHub 
- Google

If you have no Dockstore accounts, click either of the "Login with" buttons to create a new Dockstore account.  The username will be auto-generated.

At this point, you will be able to link as many tokens as you want (Quay.io, Bitbucket, GitLab, Google/GitHub).  If the first Dockstore account was created with GitHub and then a Google token was linked (i.e. you have a Google and GitHub token linked to the same account), you are then able to login to that same account with either "Login with Google" or "Login with GitHub".  

## Multiple Accounts Situation

There is a strange situation where multiple accounts can be created by a single person.  Which in reality should not happen that often...
1. A new Dockstore account was created using "Login with GitHub"
2. Signed out of new account
3. Clicked "Login with Google" 

This will end up creating two completely new Dockstore accounts (Google account with Google token and GitHub account with GitHub token) because Dockstore has no information to suggest otherwise that the GitHub account is related to the Google account. These accounts can then have as many tokens as possible linked - Google account with every token linked and GitHub account with every token linked.  Clicking "Login with GitHub" will only log into the GitHub account (even though there's a GitHub token linked in the Google account) and clicking "Login with Google" will only log into the Google account (even though there's a Google token in the GitHub account).

The accounts are now in an odd state because there's two easily accessible accounts.  For those who want to login to a single account of their choosing using both "Login with" buttons, there is a solution.

"Login with Google" would direct you to your Google-created account.  To change it so that clicking "Login with Google" ends up with the GitHub-created Dockstore account, follow these steps:
- Make sure that the Google token is linked to the GitHub-created Dockstore account
- Unlink the Google token from the Google-created Dockstore account 
- Logout and then click "Login with Google"

This is because Dockstore prioritizes logging into an acccount that actually has the token presently over an account that was originally created with the token but no longer has it.  If both accounts have the token or both don't have the token, then Dockstore will log into the account that was originally created with the token (i.e. "Login with Google" logs into the Google account).  

The below 2D lookup table sums up what happens in different scenarios when the "Login with Google" is pressed:

| | Have GitHub account no Google Token (no GitHub account) | Have GitHub account with Google token |
| ----- |:----:|---:|
| <b>Have Google Account no Google token | Login with Google account (1) | Login with GitHub account(2) |
| <b>Have Google Account with Google token | Login with Google account (3) | Login with Google account (4) |
| <b>No Google Account | Create Google account (5) | Login with GitHub account (6) |
