# Jenkins Unit Test Harness
Defines test harness for Jenkins core and plugins that you can use during the `mvn test` phase.
See [wiki page](//wiki.jenkins-ci.org/display/JENKINS/Unit+Test)

## Changelog

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
