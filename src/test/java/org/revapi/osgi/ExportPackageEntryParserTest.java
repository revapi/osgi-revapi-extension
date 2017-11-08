package org.revapi.osgi;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.Test;

public class ExportPackageEntryParserTest {

    @Test
    public void testParsesSimplePackage() {
        test("a.b.c", exports -> {
            assertEquals(1, exports.size());
            ExportPackageDefinition def = exports.iterator().next();
            assertEquals(singleton("a.b.c"), def.getPackageNames());
            assertTrue(def.getIncludes().isEmpty());
            assertTrue(def.getExcludes().isEmpty());
        });
    }

    @Test
    public void testParsesMultipleSimplePackages() {
        test("a.b.c;d.e.f", exports -> {
            assertEquals(1, exports.size());
            ExportPackageDefinition def = exports.iterator().next();
            assertEquals(setOf("a.b.c", "d.e.f"), def.getPackageNames());
            assertTrue(def.getIncludes().isEmpty());
            assertTrue(def.getExcludes().isEmpty());
        });
    }

    @Test
    public void testParsesSinglePackageWithSingleDirective() {
        test("a.b.c;include:=X*", exports -> {
            assertEquals(1, exports.size());
            ExportPackageDefinition def = exports.iterator().next();
            assertEquals(setOf("a.b.c"), def.getPackageNames());
            assertEquals(1, def.getIncludes().size());
            Pattern className = def.getIncludes().iterator().next();
            assertEquals("X.*", className.pattern());
        });
    }

    @Test
    public void testParsesSinglePackageWithMultipleDirectives() {
        test("a.b.c;include:=\"X*,*Y\";exclude:=A", exports -> {
            assertEquals(1, exports.size());
            ExportPackageDefinition def = exports.iterator().next();
            assertEquals(setOf("a.b.c"), def.getPackageNames());
            assertEquals(2, def.getIncludes().size());
            assertEquals(setOf("X.*",".*Y"), def.getIncludes().stream().map(Pattern::pattern).collect(toSet()));
            assertEquals(1, def.getExcludes().size());
            assertEquals("A", def.getExcludes().iterator().next().pattern());
        });
    }

    @Test
    public void testParsesMultiplePackagesWithSingleDirective() {
        test("a.b.c;d.e.f;include:=\"X*,*Y\"", exports -> {
            assertEquals(1, exports.size());
            ExportPackageDefinition def = exports.iterator().next();
            assertEquals(setOf("a.b.c", "d.e.f"), def.getPackageNames());
            assertEquals(2, def.getIncludes().size());
            assertEquals(setOf("X.*",".*Y"), def.getIncludes().stream().map(Pattern::pattern).collect(toSet()));
            assertTrue(def.getExcludes().isEmpty());
        });
    }

    @Test
    public void testParsesMultiplePackagesWithMultipleDirectives() {
        test("a.b.c;d.e.f;include:=\"X*,*Y\";exclude:=\"A\"", exports -> {
            assertEquals(1, exports.size());
            ExportPackageDefinition def = exports.iterator().next();
            assertEquals(setOf("a.b.c", "d.e.f"), def.getPackageNames());
            assertEquals(2, def.getIncludes().size());
            assertEquals(setOf("X.*",".*Y"), def.getIncludes().stream().map(Pattern::pattern).collect(toSet()));
            assertEquals(1, def.getExcludes().size());
            assertEquals("A", def.getExcludes().iterator().next().pattern());
        });
    }

    @Test
    public void testParsesMultipleExportsWithSinglePackage() {
        test("a.b.c,d.e.f", exports -> {
            ExportPackageDefinition a = new ExportPackageDefinition(setOf("a.b.c"), emptySet(), emptySet());
            ExportPackageDefinition d = new ExportPackageDefinition(setOf("d.e.f"), emptySet(), emptySet());

            assertEquals(setOf(a, d), exports);
        });
    }

    @Test
    public void testParsesMultipleExportsWithMultiplePackages() {
        test("a.b.c;d.e.f,g.h.i;j.k.l", exports -> {
            ExportPackageDefinition ad = new ExportPackageDefinition(setOf("a.b.c", "d.e.f"), emptySet(), emptySet());
            ExportPackageDefinition gj = new ExportPackageDefinition(setOf("g.h.i", "j.k.l"), emptySet(), emptySet());

            assertEquals(setOf(ad, gj), exports);
        });
    }

    @Test
    public void testParsesMultipleExportsWithMultiplePackagesWithSingleDirective() {
        test("a.b.c;d.e.f;include:=X,g.h.i;j.k.l;exclude:=*Y", exports -> {
            ExportPackageDefinition ad = new ExportPackageDefinition(setOf("a.b.c", "d.e.f"), setOf(Pattern.compile("X")), emptySet());
            ExportPackageDefinition gj = new ExportPackageDefinition(setOf("g.h.i", "j.k.l"), emptySet(), setOf(Pattern.compile(".*Y")));

            assertEquals(setOf(ad, gj), exports);
        });
    }

    @Test
    public void testParsesMultipleExportsWithMultiplePackagesWithMultipleDirectives() {
        test("a.b.c;d.e.f;include:=X;someOther:=blah;attr=bar;exclude:=*X;,g.h.i;j.k.l;include:=Y;someOther:=blah;attr=bar;exclude:=*Y", exports -> {
            ExportPackageDefinition ad = new ExportPackageDefinition(setOf("a.b.c", "d.e.f"), setOf(Pattern.compile("X")), setOf(Pattern.compile(".*X")));
            ExportPackageDefinition gj = new ExportPackageDefinition(setOf("g.h.i", "j.k.l"), setOf(Pattern.compile("Y")), setOf(Pattern.compile(".*Y")));

            assertEquals(setOf(ad, gj), exports);
        });

    }

    private void test(String directive, Consumer<Set<ExportPackageDefinition>> test) {
        Set<ExportPackageDefinition> res = new HashSet<>();
        ExportPackageEntryParser.parse(directive, res);
        test.accept(res);
    }

    private static <T> Set<T> setOf(T... stuff) {
        return new HashSet<>(Arrays.asList(stuff));
    }
}
