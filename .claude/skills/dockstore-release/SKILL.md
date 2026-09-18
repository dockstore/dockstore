---
name: dockstore-release
description: Cut a new tagged release of the dockstore/dockstore webservice (alpha/beta/rc/stable/hotfix), following the dockstore-deploy wiki's Hubflow + Maven CI-friendly-versions process. Use when asked to release, tag, or cut a new Dockstore webservice version.
---

# Dockstore Release

Guides a webservice release for `dockstore/dockstore`, based on the `dockstore-deploy` wiki
page ["Dockstore Releases"](https://github.com/dockstore/dockstore-deploy/wiki/Dockstore-Releases)
(webservice / "Friendly CI versions and a GitHub Action" section), adjusted for what the team
actually does in practice — see **Notes / gotchas** below for where practice diverges from the
wiki text.

This process touches shared state (git tags, branches, Artifactory, quay.io images, and
potentially `master`). Never run the build/tag/push/finish commands without showing the exact
command first and getting explicit confirmation — treat this the same as any other
hard-to-reverse, shared-state action.

## Step 0 — Ask the two key questions

Ask up front, via AskUserQuestion:

1. **Tag name** — the exact version to release, e.g. `1.21.0-alpha.6`, `1.21.0-beta.0`,
   `1.21.0`, or a hotfix like `1.20.1`.
2. **Unstable or stable?**
   - *Unstable*: any `alpha`/`beta`/`rc` prerelease tag.
   - *Stable*: a final `X.Y.Z` tag (no prerelease suffix) or a hotfix release.
   - Don't infer this from the tag string alone — confirm explicitly, since it changes the
     entire back half of the process (see Step 3 vs Step 4).

If it's not obvious from context, also confirm whether this is a **release** (branched from
`develop`) or a **hotfix** (branched from `master`).

## Step 1 — Reconnaissance before touching anything

Before running any command, check and report findings to the user:

- `git status` on the current checkout — must be clean before starting.
- `git fetch origin --tags`, then confirm the target tag doesn't already exist
  (`git tag -l "<tag>"`, `gh release view <tag> --repo dockstore/dockstore`). If it exists, stop
  and ask rather than overwriting/re-tagging.
- Look for stale `release/*`/`hotfix/*` branches (local and `origin/`) that might collide with
  or be confused for the new branch. If any look abandoned, confirm with the user before
  deleting (`git branch -d`/`-D`, `git push origin --delete`) — don't delete unasked.
- Read `<revision>`/`<changelist>` in the root `pom.xml` to sanity-check against the target
  version (normally sits at `.0-SNAPSHOT` on `develop`).

## Step 2 — Present the plan, then execute with checkpoints

Show the full tailored command sequence (below, with the real tag substituted) before running
anything. Then execute step by step:

- Read-only checks (git status, log, tag/branch lookups) can run without asking each time.
- Anything that mutates shared state — `git hf release start/finish`, the `mvnw` build, `git
  commit`, `git tag`, `git push`, branch deletion, drafting/publishing a GitHub Release — gets a
  confirmation checkpoint first. Batch tightly-coupled steps (e.g. commit+tag+push) into one
  confirmation once the diff has been shown, rather than asking before every single command.

## Step 3 — Unstable release path (alpha/beta/rc)

```
git checkout develop && git pull        # or master, for a hotfix
git hf release start <tag>              # or: git hf hotfix start <tag>

./mvnw clean install -Dchangelist=<suffix> -DskipTests
git add dockstore-webservice/src/main/resources/openapi3/openapi.yaml **/generated/**/pom.xml
git commit -m "Update artifacts"
git tag <tag> -a -m "release process"
git push origin <tag>
git push origin release/<tag>
```

`<suffix>` is the changelist override that reproduces the tag, i.e. everything after
`<revision>` in the version string — tag `1.21.0-alpha.5` (with `<revision>1.21</revision>`)
means `-Dchangelist=.0-alpha.5`.

Before committing, verify the build only touched
`dockstore-webservice/src/main/resources/openapi3/openapi.yaml` and each module's
`generated/src/main/resources/pom.xml` — if anything else changed, stop and ask.

Then:

- Watch the [Deploy artifacts action](https://github.com/dockstore/dockstore/actions/workflows/deploy_artifacts.yml)
  run to completion: `gh run list --repo dockstore/dockstore --workflow=deploy_artifacts.yml`,
  then `gh run view <run-id> --repo dockstore/dockstore`. It publishes to OICR Artifactory and
  pushes a Docker image to `quay.io/dockstore/dockstore-webservice`.
- **Confirmed team practice: that's the whole release.** Despite the wiki describing a
  "Reset version" commit + merge of the release branch back into `develop`, in practice this is
  skipped for unstable tags — leave `release/<tag>` as pushed, don't reset generated files,
  don't merge it anywhere, and don't draft a GitHub Release, unless the user explicitly asks for
  one of those. Never run `git hf release finish` for an unstable tag.

## Step 4 — Stable release path (final X.Y.Z or hotfix)

Higher stakes than Step 3: this reaches `master` and produces a public release. Confirm each
stage explicitly — don't chain through to `git hf release finish` without a checkpoint.

```
git checkout develop && git pull        # or master, for a hotfix
git hf release start <tag>              # or: git hf hotfix start <tag>

./mvnw clean install -Dchangelist=<suffix> -DskipTests
git add dockstore-webservice/src/main/resources/openapi3/openapi.yaml **/generated/**/pom.xml
git commit -m "Update artifacts"
git tag <tag> -a -m "release process"
git push origin <tag>
git push origin release/<tag>
```

Wait for the Deploy artifacts action to pass, then reset generated files before merging back:

```
./mvnw clean install -DskipTests
git add dockstore-webservice/src/main/resources/openapi3/openapi.yaml dockstore-webservice/src/main/resources/swagger.yaml **/generated/**/pom.xml
git commit -m "Reset version"
git push
```

Finishing hubflow **requires Dockstore GitHub admin / `release-leads` team membership**:

```
git hf release finish <tag>
# or: git hf hotfix finish <tag>
```

This merges into both `master` and `develop`. Expect merge conflicts on the `develop` side —
resolve in favor of `develop`'s content (except take the incoming version bump), verify with a
full `./mvnw clean install`, then check `https://github.com/dockstore/dockstore/compare/develop...master`
shows no pending diff. If conflicts are painful, the wiki's fallback (checkout the tag, diff
against `master`, apply as a patch, commit directly to `master`) is documented as a last resort
— surface it to the user rather than reaching for it unprompted.

Finally, draft the GitHub Release from the tag (confirm with the user before publishing, since
this is a shared, externally-visible, hard-to-reverse action):

- "Create release from tag" on GitHub, title = tag name.
- Leave "This is a pre-release" checked, and don't mark it "latest", until it's confirmed live
  in dev/staging/prod.
- Pick the correct "Previous tag" and click "Generate release notes".

## Notes / gotchas learned from actual releases

- Always use the wrapper (`./mvnw`), never system `mvn`, and always build the full reactor from
  repo root — never `-pl`/`-am` a subset — since generated `pom.xml` files and
  `THIRD-PARTY-LICENSES.txt` are derived from the complete module set.
- `<revision>`/`<changelist>` in the root `pom.xml` normally stay at `.0-SNAPSHOT` on `develop`;
  the release version comes purely from the `-Dchangelist=...` build-time override, not from
  editing `pom.xml` directly.
- Pushing directly to a `release/*` branch can bypass a "changes must be made through a pull
  request" branch-protection rule for admins — expected, but call it out when it happens rather
  than treating the bypass notice as an error.
- Historically, unstable `release/<tag>` branches are left behind (never merged or deleted) —
  that's normal; don't clean up old ones proactively without asking first.
