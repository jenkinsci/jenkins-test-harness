/*
 * The MIT License
 *
 * Copyright 2024 Olivier Lamy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.test;

import io.jenkins.test.fips.FIPSTestBundleProvider;
import jenkins.security.FIPS140;
import org.junit.Rule;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class RealJenkinsRuleFIPSTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule().prepareHomeLazily(true)
            .withDebugPort(4001).withDebugServer(false)
            .withFIPSEnabled(FIPSTestBundleProvider.get())
            .javaOptions("-Djava.security.debug=properties");

    @Test
    public void fipsMode() throws Throwable {
        rr.then(r -> {
            Provider[] providers = Security.getProviders();
            System.out.println("fipsMode providers:" + Arrays.asList(providers));

            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass("org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider");
            System.out.println("BouncyCastleFipsProvider class:" + clazz);

            Provider provider = Security.getProvider("BCFIPS");
            assertThat(provider, notNullValue());
            assertThat(provider.getClass().getName(), is("org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider"));
            assertThat(providers[0].getClass().getName(), is("org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider"));
            assertThat(providers[1].getClass().getName(), is("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider"));
            assertThat(providers[2].getClass().getName(), is("sun.security.provider.Sun"));
            assertThat(KeyStore.getDefaultType(), is("BCFKS"));
            assertThat(KeyManagerFactory.getDefaultAlgorithm(), is("PKIX"));

            assertThat(providers.length, is(3));

            assertThat(FIPS140.useCompliantAlgorithms(), is(true));
        });
    }

}
