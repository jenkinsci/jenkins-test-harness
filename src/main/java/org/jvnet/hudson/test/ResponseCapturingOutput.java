/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import net.sf.json.JsonConfig;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Flavor;

/**
 * Not meant to be used by code outside of {@link JenkinsRule#createApiToken(User)} but at the same time,
 * Too big to be put as an internal class.
 */
class ResponseCapturingOutput implements StaplerResponse {
    
    private final CapturingServletOutputStream capturingServletOutputStream = new CapturingServletOutputStream();
    
    public String getOutputContent() {
        return capturingServletOutputStream.getOutputContent();
    }

    // Methods used by HttpResponses.JSONObjectResponse#generateResponse

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return capturingServletOutputStream;
    }
    
    @Override
    public void setContentLength(int len) {
    }

    @Override
    public void setContentType(String type) {
    }
    
    private static final class CapturingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public String getOutputContent() {
            return baos.toString(StandardCharsets.UTF_8);
        }
        
        @Override
        public void write(int b) throws IOException {
            baos.write(b);
        }

        @Override 
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }
    
    // Rest of the non-implementation
    
    @Override
    public void forward(Object it, String url, StaplerRequest request) throws ServletException, IOException {
    }

    @Override
    public void forwardToPreviousPage(StaplerRequest request) throws ServletException, IOException {
    }

    @Override
    public void sendRedirect2(@NonNull String url) throws IOException {
    }

    @Override
    public void sendRedirect(int statusCore, @NonNull String url) throws IOException {
    }

    @Override
    public void serveFile(StaplerRequest request, URL res) throws ServletException, IOException {
    }

    @Override
    public void serveFile(StaplerRequest request, URL res, long expiration) throws ServletException, IOException {
    }

    @Override
    public void serveLocalizedFile(StaplerRequest request, URL res) throws ServletException, IOException {
    }

    @Override
    public void serveLocalizedFile(StaplerRequest request, URL res, long expiration) throws ServletException, IOException {
    }

    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, long expiration, long contentLength, String fileName) throws ServletException, IOException {
    }

    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, long expiration, int contentLength, String fileName) throws ServletException, IOException {
    }

    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, long contentLength, String fileName) throws ServletException, IOException {
    }

    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, int contentLength, String fileName) throws ServletException, IOException {
    }

    @Override
    public void serveExposedBean(StaplerRequest req, Object exposedBean, Flavor flavor) throws ServletException, IOException {
    }

    @Override
    public OutputStream getCompressedOutputStream(HttpServletRequest req) throws IOException {
        return null;
    }

    @Override
    public Writer getCompressedWriter(HttpServletRequest req) throws IOException {
        return null;
    }

    @Override
    public int reverseProxyTo(URL url, StaplerRequest req) throws IOException {
        return 0;
    }

    @Override
    public void setJsonConfig(JsonConfig config) {
    }

    @Override
    public JsonConfig getJsonConfig() {
        return null;
    }

    @Override
    public void addCookie(Cookie cookie) {
    }

    @Override
    public boolean containsHeader(String name) {
        return false;
    }

    @Override
    public String encodeURL(String url) {
        return null;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return null;
    }

    @Override
    public String encodeUrl(String url) {
        return null;
    }

    @Override
    public String encodeRedirectUrl(String url) {
        return null;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
    }

    @Override
    public void sendError(int sc) throws IOException {
    }

    @Override
    public void sendRedirect(String location) throws IOException {
    }

    @Override
    public void setDateHeader(String name, long date) {
    }

    @Override
    public void addDateHeader(String name, long date) {
    }

    @Override
    public void setHeader(String name, String value) {
    }

    @Override
    public void addHeader(String name, String value) {
    }

    @Override
    public void setIntHeader(String name, int value) {
    }

    @Override
    public void addIntHeader(String name, int value) {
    }

    @Override
    public void setStatus(int sc) {
    }

    @Override
    public void setStatus(int sc, String sm) {
    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public String getHeader(String name) {
        return null;
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return null;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return null;
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return null;
    }

    @Override
    public void setCharacterEncoding(String charset) {
    }

    @Override
    public void setContentLengthLong(long len) {
    }

    @Override
    public void setBufferSize(int size) {
    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void flushBuffer() throws IOException {
    }

    @Override
    public void resetBuffer() {
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void reset() {
    }

    @Override
    public void setLocale(Locale loc) {
    }

    @Override
    public Locale getLocale() {
        return null;
    }
}
