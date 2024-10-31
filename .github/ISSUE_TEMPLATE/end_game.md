---
name: Milestone Endgame
about: Ship a milestone!
title: 'v0.x.x endgame'
labels: 'kind/endgame'
assignees: ''
---

## DRIs

|         | DRI |
| ------- | --- |
| Endgame |     |
| QA      |     |
| Docs    |     |

## Planning Checklist

- [ ] Review the specific [GitHub Milestone](https://github.com/spiceai/spice-java/milestones)

## Release Checklist

- [ ] All features/bugfixes to be included in the release have been merged to trunk
- [ ] Full test pass and update if necessary over [README.md](https://github.com/spiceai/spice-java/blob/trunk/README.md)
- [ ] Full test pass and update if necessary over Docs
  - [ ] [docs.spiceai.org](https://docs.spiceai.org/sdks/java)
  - [ ] [docs.spice.ai](https://github.com/spicehq/docs/blob/trunk/sdks/java-sdk.md)
- [ ] Test the [`spice-java` sample](https://github.com/spiceai/samples/tree/trunk/client-sdk/spice-java-sdk-sample) using the latest `trunk` SDK version.
- [ ] Update [release notes](https://github.com/spiceai/spice-java/blob/trunk/docs/release_notes)
  - [ ] Ensure any external contributors have been acknowledged.
- [ ] Verify `Version.java` and version in `pom.xml` are correct and match the milestone version.
- [ ] Run [Test CI](https://github.com/spiceai/spice-java/actions/workflows/build.yml) and ensure it is green on the trunk branch.
- [ ] QA DRI sign-off
- [ ] Docs DRI sign-off
- [ ] Create a new branch `release-v[semver]` for the release from trunk. E.g. `release-v0.17.0-beta`
- [ ] Release the new version by creating and publishing a latest [GitHub Release](https://github.com/spiceai/spice-java/releases/new) with the tag from the release branch. E.g. `v0.17.0-beta`.
- [ ] Ensure the [publish](https://github.com/spiceai/spice-java/actions/workflows/publish.yml) workflow has triggered, and successfully published the package.
- [ ] Run a test pass using the [`spice-java` sample](https://github.com/spiceai/samples/tree/trunk/client-sdk/spice-java-sdk-sample) using the latest published version.
- [ ] Update `Version.java` and version in `pom.xml` to the next release version.
- [ ] The SDK release is added to the next [Spice release notes](https://github.com/spiceai/spiceai/tree/trunk/docs/release_notes)
