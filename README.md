# Jenkins Unit Test Harness
Defines test harness for Jenkins core and plugins that you can use during the `mvn test` phase.
See [wiki page](//wiki.jenkins-ci.org/display/JENKINS/Unit+Test)

## Changelog

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
