/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package org.jvnet.hudson.test.recipes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Functions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.kohsuke.MetaInfServices;

/**
 * Annotation to be placed on a package to generate a resource {@code /plugins/$name.jpi}.
 * Classes included in the package will be packed into the plugin.
 * @see RealJenkinsRule#addPlugins
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
public @interface TestPlugin {
    /**
     * Plugin identifier ({@code Short-Name} manifest header).
     * Defaults to being calculated from the package name, replacing {@code .} with {@code -}.
     */
    String shortName() default "";
    /**
     * Plugin version string ({@code Plugin-Version} manifest header).
     */
    String version() default "999999-SNAPSHOT";
    /**
     * Other manifest headers, in {@code key=value} format.
     * Examples:
     * <ul>
     * <li>{@code Jenkins-Version=2.387.3}
     * <li>{@code Plugin-Dependencies=structs:325.vcb_307d2a_2782,support-core:1356.vd0f980edfa_46;resolution:=optional}
     * <li>{@code Long-Name=My Plugin}
     * </ul>
     */
    String[] headers() default {};

    @SupportedAnnotationTypes("org.jvnet.hudson.test.recipes.TestPlugin")
    @MetaInfServices(Processor.class)
    final class Proc extends AbstractProcessor {

        private final Map<String, PackageElement> testPlugins = new TreeMap<>();

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                for (Map.Entry<String, PackageElement> entry : testPlugins.entrySet()) {
                    String pkg = entry.getKey();
                    PackageElement el = entry.getValue();
                    TestPlugin ann = el.getAnnotation(TestPlugin.class);
                    String shortName = ann.shortName().isEmpty() ? pkg.replace('.', '-') : ann.shortName();
                    try {
                        Path output = Path.of(processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "xxx").toUri()).getParent();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                            Path metaInf = output.resolve("META-INF");
                            if (Files.isDirectory(metaInf)) {
                                zip(zos, metaInf, "META-INF/", pkg);
                            }
                            String pkgSlash = pkg.replace('.', '/');
                            Path main = output.resolve(pkgSlash);
                            if (Files.isDirectory(main)) {
                                zip(zos, main, pkgSlash + "/", null);
                            } else {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, main + " does not exist at this point");
                            }
                        }
                        FileObject fo = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "plugins/" + shortName + ".jpi", el);
                        Manifest mani = new Manifest();
                        Attributes attr = mani.getMainAttributes();
                        attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                        attr.putValue("Short-Name", shortName);
                        attr.putValue("Plugin-Version", ann.version());
                        for (String header : ann.headers()) {
                            int idx = header.indexOf('=');
                            attr.putValue(header.substring(0, idx), header.substring(idx + 1));
                        }
                        try (OutputStream os = fo.openOutputStream(); JarOutputStream jos = new JarOutputStream(os, mani)) {
                            jos.putNextEntry(new JarEntry("WEB-INF/lib/" + shortName + ".jar"));
                            jos.write(baos.toByteArray());
                        }
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated " + fo.toUri());
                    } catch (Exception x) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, Functions.printThrowable(x));
                    }
                }
                return true;
            } else {
                for (Element el : roundEnv.getElementsAnnotatedWith(TestPlugin.class)) {
                    PackageElement pkg = (PackageElement) el;
                    testPlugins.put(pkg.getQualifiedName().toString(), pkg);
                }
            }
            return false;
        }

        private void zip(ZipOutputStream zos, Path dir, String prefix, @CheckForNull String filter) throws IOException {
            try (Stream<Path> stream = Files.list(dir)) {
                Iterable<Path> iterable = stream::iterator;
                for (Path child : iterable) {
                    String name = child.getFileName().toString();
                    if (Files.isDirectory(child)) {
                        zip(zos, child, prefix + name + "/", filter);
                    } else {
                        if (filter != null) {
                            // Deliberately not using UTF-8 since the file could be binary.
                            // If the package name happened to be non-ASCII, 🤷 this could be improved.
                            if (!Files.readString(child, StandardCharsets.ISO_8859_1).contains(filter)) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Skipping " + child + " since it makes no mention of " + filter);
                                continue;
                            }
                        }
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Packing " + child);
                        zos.putNextEntry(new ZipEntry(prefix + name));
                        Files.copy(child, zos);
                    }
                }
            }
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }
}
