/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import hudson.util.XStream2;
import java.io.Serializable;

/**
 * Holder for an object which is not {@link Serializable} but can be serialized safely using XStream.
 * Useful for arguments and return values of {@link RealJenkinsRule#runRemotely(RealJenkinsRule.StepWithReturnAndOneArg, Serializable)} etc.
 */
public record XStreamSerializable<T>(String xml) implements Serializable {

    // TODO as needed, add an optional enum for choice of Jenkins.XSTREAM2, Items.XSTREAM2, etc.
    // (cannot safely use a Supplier<XStream>: https://stackoverflow.com/a/27472025/12916)
    private static final XStream2 XSTREAM2 = new XStream2();

    /**
     * Serializes an object to XML.
     */
    public XStreamSerializable(T o) {
        this(XSTREAM2.toXML(o));
    }

    /**
     * Deserializes an object from XML.
     */
    @SuppressWarnings("unchecked")
    public T object() {
        return (T) XSTREAM2.fromXML(xml);
    }

}
