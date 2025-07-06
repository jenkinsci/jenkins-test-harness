package jenkins.model;

/**
 * @deprecated use {@link Jenkins#setQuietPeriod}
 */
@Deprecated
public class JenkinsAdaptor {
    public static void setQuietPeriod(Jenkins jenkins, int quietPeriod) {
        jenkins.quietPeriod = quietPeriod;
    }
}
