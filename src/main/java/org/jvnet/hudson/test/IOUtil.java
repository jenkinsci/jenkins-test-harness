/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Logger;

/**
 * Copied from https://github.com/jenkinsci/acceptance-test-harness/blob/f749d27b229ce5a4737d1614fa1f31d2e6402f4f/src/main/java/org/jenkinsci/test/acceptance/utils/IOUtil.java#L80-L115
 */
class IOUtil {

    private static final Logger LOGGER = Logger.getLogger(IOUtil.class.getName());

    /**
     * Gives random available TCP port in the given range.
     *
     * @param from if &lt;=0 then default value 49152 is used
     * @param to   if &lt;=0 then default value 65535 is used
     */
    public static int randomTcpPort(int from, int to){
        from = (from <=0) ? 49152 : from;
        to = (to <= 0) ? 65535 : to;


        while(true){
            int candidate = (int) ((Math.random() * (to-from)) + from);
            if(isTcpPortFree(candidate)){
                return candidate;
            }
            LOGGER.info(String.format("Port %s is in use", candidate));
        }
    }

    /**
     * Gives random available TCP port.
     */
    public static int randomTcpPort(){
        return randomTcpPort(-1,-1);
    }

    public static boolean isTcpPortFree(int port){
        try {
            ServerSocket ss = new ServerSocket(port);
            ss.close();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
