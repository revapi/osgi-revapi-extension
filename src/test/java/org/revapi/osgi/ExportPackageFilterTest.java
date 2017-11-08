package org.revapi.osgi;

import static java.util.Collections.emptySet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.AbstractMap;
import java.util.Map;

import org.jboss.dmr.ModelNode;
import org.junit.Rule;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.ElementForest;
import org.revapi.java.JavaApiAnalyzer;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.test.support.Jar;
import org.revapi.simple.FileArchive;

public class ExportPackageFilterTest {

    @Rule
    public Jar jar = new Jar();

    @Test
    public void testNoExportsMeanEverythingIncluded() throws Exception {
        Jar.BuildOutput env = jar.from()
                .classPathSources("/test-bundle/", "exported/ExportedClass.java", "UnexportedClass.java")
                .build();

        Map.Entry<ElementForest, ExportPackageFilter> classesAndFilter = prepare(env);
        ElementForest forest = classesAndFilter.getKey();
        ExportPackageFilter filter = classesAndFilter.getValue();

        JavaTypeElement unexportedClass = forest.getRoots().stream()
                .filter(t -> "class UnexportedClass".equals(t.getFullHumanReadableString()))
                .findFirst()
                .map(t -> (JavaTypeElement) t)
                .orElseThrow(() -> new AssertionError("Should have found the 'UnexportedClass'"));

        JavaTypeElement exportedClass = forest.getRoots().stream()
                .filter(t -> "class exported.ExportedClass".equals(t.getFullHumanReadableString()))
                .findFirst()
                .map(t -> (JavaTypeElement) t)
                .orElseThrow(() -> new AssertionError("Should have found the 'ExportedClass'"));

        assertTrue(filter.applies(exportedClass));
        assertTrue(filter.applies(unexportedClass));
    }

    @Test
    public void testUnexportedPackagesFilteredOut() throws Exception {
        Jar.BuildOutput env = jar.from()
                .classPathSources("/test-bundle/", "exported/ExportedClass.java", "UnexportedClass.java")
                .classPathResources("/test-bundle/", "META-INF/MANIFEST.MF")
                .build();

        Map.Entry<ElementForest, ExportPackageFilter> classesAndFilter = prepare(env);
        ElementForest forest = classesAndFilter.getKey();
        ExportPackageFilter filter = classesAndFilter.getValue();

        JavaTypeElement unexportedClass = forest.getRoots().stream()
                .filter(t -> "class UnexportedClass".equals(t.getFullHumanReadableString()))
                .findFirst()
                .map(t -> (JavaTypeElement) t)
                .orElseThrow(() -> new AssertionError("Should have found the 'UnexportedClass'"));

        JavaTypeElement exportedClass = forest.getRoots().stream()
                .filter(t -> "class exported.ExportedClass".equals(t.getFullHumanReadableString()))
                .findFirst()
                .map(t -> (JavaTypeElement) t)
                .orElseThrow(() -> new AssertionError("Should have found the 'ExportedClass'"));

        assertTrue(filter.applies(exportedClass));
        assertFalse(filter.applies(unexportedClass));
    }

    private Map.Entry<ElementForest, ExportPackageFilter> prepare(Jar.BuildOutput build) {
        API oldApi = API.of(new FileArchive(build.jarFile())).build();
        API newApi = API.of(new FileArchive(build.jarFile())).build();

        AnalysisContext ctx = AnalysisContext.builder().withOldAPI(oldApi).withNewAPI(newApi).build();

        // make "extension specific" configuration
        ctx = ctx.copyWithConfiguration(ModelNode.fromString("{}"));

        JavaApiAnalyzer apiAnalyzer = new JavaApiAnalyzer(emptySet());
        apiAnalyzer.initialize(ctx);
        ArchiveAnalyzer analyzer = apiAnalyzer.getArchiveAnalyzer(oldApi);

        ElementForest forest = analyzer.analyze();

        ExportPackageFilter filter = new ExportPackageFilter();
        filter.initialize(ctx);

        return new AbstractMap.SimpleImmutableEntry<>(forest, filter);
    }
}
