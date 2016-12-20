# Jenkins Unit Test Harness
Defines test harness for Jenkins core and plugins that you can use during the `mvn test` phase.
See [wiki page](//wiki.jenkins-ci.org/display/JENKINS/Unit+Test)

## Changelog

### 2.18 (2016 Dec 20)

* Fixed `JenkinsComputerConnectorTester` so `ComputerConnector`s can be tested through `JenkinsRule.configRoundTrip`.
* Improved `MemoryAssert.assertGC`: now catches more root references, and can run with the environment variable `ASSERT_GC_VERBOSE=true` to track down `SoftReference` leaks.
* Better report `java.lang.IllegalArgumentException: URI is not hierarchical`; generally this is a symptom of a plugin missing a root `index.jelly`.

### 2.17 (2016 Oct 10)

* `JenkinsRule.getLog` fixed to make fewer assumptions about the implementation of `Run.getLogText`.

### 2.16 and earlier

Not recorded.
