# Jenkins Unit Test Harness

[![GitHub release](https://img.shields.io/github/release/jenkinsci/jenkins-test-harness.svg?label=release)](https://github.com/jenkinsci/jenkins-test-harness/releases/latest)

Defines test harness for Jenkins core and plugins that you can use during the `mvn test` phase.
See the [Developer Guide / Jenkins Testing](https://www.jenkins.io/doc/developer/testing/)
for more information and usage guidelines.

## Javadoc

See https://javadoc.jenkins.io/component/jenkins-test-harness/

## Changelog

* See [GitHub Releases](https://github.com/jenkinsci/jenkins-test-harness/releases).
* For releases before `2.49`, see [this archive](./docs/CHANGELOG-OLD.md)

## Fast-tests opt-in filter

A JUnit Platform PostDiscoveryFilter is provided to allow excluding tests that boot a real Jenkins instance
(i.e., tests annotated with `@WithJenkins` or `@WithJenkinsConfiguredWithCode` whose method declares the
corresponding rule parameter). To enable exclusion set `-Djenkins.tests.excludeJenkins=true` in Surefire/Failsafe
when running tests (opt-in). By default the filter is disabled.
