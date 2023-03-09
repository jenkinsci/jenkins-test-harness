# Changelog Archive

| WARNING: Changelogs have been moved to [GitHub Releases](https://github.com/jenkinsci/jenkins-test-harness/releases) |
| --- |

### 2.49 (2019 Apr 02)

* [PR-127](https://github.com/jenkinsci/jenkins-test-harness/pull/127): `JenkinsRule.showAgentLogs` utility
* [PR-128](https://github.com/jenkinsci/jenkins-test-harness/pull/128): Work with `version-number` as shipped in `jenkins-core`

### 2.48 (2019 Mar 08)

* [PR-126](https://github.com/jenkinsci/jenkins-test-harness/pull/126): Skip `MemoryAssert.assertGC()` on JRE > 8

### 2.47 (2019 Feb 22)

* [PR-118](https://github.com/jenkinsci/jenkins-test-harness/pull/118): Internal code improvements

### 2.46 (2019 Jan 22)

* Bump jenkins-test-harness-htmlunit to 2.31-2, which disables JAXP registration of Xalan-J and Xerces
(fixes regressions introduced in 2.45 by space in temporary paths because of a Xalan-J bug)

### 2.45 (2019 Jan 10)

* Use DisableOnDebug’s logic rather than rolling our own
* SUREFIRE-1588 workaround
* JDK 11 upgrades
* Spurious temporary dir name log to `System.err` removed
* Fix tests when run under Jenkins 2.x
* Add Configuration-as-Code support to TestCrumbIssuer
* Temporary directories now by default have a space in their path, to flush out path handling mistakes. (The system property `jenkins.test.noSpaceInTmpDirs=true` suppresses this.)

### 2.44 (2018 Oct 05)

* Extending path shortening fix to users of `RestartableJenkinsRule`.

### 2.43 (2018 Oct 02)

* Shorter temporary directory paths.

### 2.42 (2018 Sep 28)

* Updated HTMLUnit.
* New convenience methods in `WebClient`.
* [JENKINS-53823](https://issues.jenkins.io/browse/JENKINS-53823) -
JDK 11 compatibility.

### 2.41.1 (2018 Nov 13)

This is a custom release without HTMLUnit upgrade

* [JENKINS-53823](https://issues.jenkins.io/browse/JENKINS-53823) -
JDK 11 compatibility.

### 2.41 (2018 Sep 21)

* Fixing a serialization issue in `MockAuthorizationStrategy`.

### 2.40 (2018 Jul 20)

* [JENKINS-49046](https://issues.jenkins.io/browse/JENKINS-49046): Fix `@WithTimeout` handling for `JenkinsRule`.

### 2.39 (2018 Jun 05)

* Make `RunLoadCounter` compatible with Pipeline (`WorkflowJob` / `WorkflowRun`).
* Prevent agent processes from stealing focus on OS X.
* Make the `NoListenerConfiguration` constructor `public`.

### 2.38 (2018 Apr 09)

* [JENKINS-50598](https://issues.jenkins.io/browse/JENKINS-50598): ability to run `JenkinsRule`-based tests with a custom WAR file.
* [JENKINS-50590](https://issues.jenkins.io/browse/JENKINS-50590): fix combination of crumbs with existing request parameters.

### 2.37 (2018 Apr 06)

* Improved `RestartableJenkinsRule.simulateAbruptShutdown`.

### 2.36 (2018 Apr 04)

* JENKINS-50476:: offer a way to assert that Jenkins won't start

### 2.35 (2018 Apr 04)

*Burned*

### 2.34 (2018 Jan 29)

* Added `CLICommandInvoker.Result.stdoutBinary` and `.stderrBinary`.
* Deprecated `PresetData`.

### 2.33 (2017 Dec 21)

* `RestartableJenkinsRule` utilities to simulate abrupt (i.e., unplanned) shutdowns.

### 2.32 (2017 Oct 28)

* Added `LoggerRule.recordPackage` as a convenience.
* Added `LoggerRule.recorded` methods returning matchers to simplify checking for log records.
* Added `WebClient.withBasicCredentials` and `.withBasicApiToken` methods to simplify passing authentication to REST requests as an alternative to `.login`.
* `waitOnline` with `JNLPLauncher` failed rather than waiting.
* Do not even try to persist an `Authentication` via XStream.

### 2.31 (2017 Oct 17)

* Introduced `RestartableJenkinsRule.createJenkinsRule`.
* Changed `JenkinsRule.createComputerLauncher` to return the more generic type `ComputerLauncher` rather than `CommandLauncher`.

### 2.30 (2017 Oct 10)

* Avoiding `JenkinsRule` plugin setup error involving excluded optional transitive dependencies.

### 2.29 (2017 Oct 10)

* Introduced `JenkinsRule.waitOnline`, improving diagnostics for `createSlave` and `createOnlineSlave`.
* Issue a friendlier warning for a harmless `@TestExtension` binary incompatibility issue.

### 2.28 (2017 Sep 26)

* Fixing a regression in `LoggerRule` in 2.26, and improving log appearance further.

### 2.27 (2017 Sep 20)

* JENKINS-45245: work around incorrect Maven test classpath in IntelliJ IDEA.
* More readable timestamps in log messages during tests.

### 2.26 (2017 Sep 13)

* Adjust Jetty configuration to use a fixed number of “acceptors” and “selectors”. This has been observed to fix JENKINS-43666-like test failures in some CI environments, depending on the reported number of CPU cores.
* Improve display of messages when using `LoggerRule`.

### 2.25 (2017 Sep 12)

* More reliable test timeout system in `JenkinsRule`.

### 2.24 (2017 Aug 02)

* Added `RestartableJenkinsRule.then` with a Java 8-friendly signature.
* Upgrade to Jetty 9.4.

### 2.23 (2017 Jun 26)

* JENKINS-41631: removing the Maven Embedder dependency from the harness.
* JENKINS-44453: `JenkinsRule` should ensure that Jenkins reaches the `COMPLETED` milestone.

### 2.22 (2017 May 02)

* Make `FakeChangeLogSCM` support `Run` rather than just `AbstractBuild`.

### 2.21 (2017 Apr 25)

* Fixed a regression in 2.20 affecting especially `InjectedTest` on Jenkins 2.x.

### 2.20 (2017 Apr 20)

* “Detached” plugins in Jenkins 2.x are no longer loaded implicitly during tests. You should declare `test`-scoped dependencies on plugins you expect to use during your tests, in the desired versions. `TestPluginManager.installResourcePlugin` has been removed, and `installDetachedPlugin` added for unusual cases.
* Avoid using methods deleted in newer version of HtmlUnit.
* `waitForMessage` can fail immediately if the build is completed.
* Possible to override `JenkinsRule.createWebServer` more easily.
* Deprecating `CLICommandInvoker.authorizedTo` in favor of a simpler `asUser`.
* Make Jetty be quiet during functional tests.
* Pick up `jetty-io` and `jetty-util` from our specified version to avoid conflicts.

### 2.19 (2017 Feb 27)

* Introduced `allowSoft` parameter to `assertGC`.
* Avoid any fixed timeout on `waitUntilNoActivity`.

### 2.18 (2016 Dec 20)

* Fixed `JenkinsComputerConnectorTester` so `ComputerConnector`s can be tested through `JenkinsRule.configRoundTrip`.
* Improved `MemoryAssert.assertGC`: now catches more root references, and can run with the environment variable `ASSERT_GC_VERBOSE=true` to track down `SoftReference` leaks.
* Better report `java.lang.IllegalArgumentException: URI is not hierarchical`; generally this is a symptom of a plugin missing a root `index.jelly`.

### 2.17 (2016 Oct 10)

* `JenkinsRule.getLog` fixed to make fewer assumptions about the implementation of `Run.getLogText`.

### 2.16 and earlier

Not recorded.
