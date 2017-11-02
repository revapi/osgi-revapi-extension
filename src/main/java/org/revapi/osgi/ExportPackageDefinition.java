package org.revapi.osgi;

import org.revapi.Element;
import org.revapi.java.spi.JavaTypeElement;

import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

final class ExportPackageDefinition {
    private final Set<String> packageNames;
    private final Set<Pattern> includes;
    private final Set<Pattern> excludes;
    private final int hashCode;

    public ExportPackageDefinition(Collection<String> packageNames, Collection<Pattern> includes, Collection<Pattern> excludes) {
        this.packageNames = Collections.unmodifiableSet(new HashSet<>(packageNames));
        this.includes = Collections.unmodifiableSet(new HashSet<>(includes));
        this.excludes = Collections.unmodifiableSet(new HashSet<>(excludes));

        int hash = packageNames.stream().sorted().collect(toList()).hashCode();
        hash = 31 * hash + includes.stream().map(Pattern::pattern).sorted().collect(toList()).hashCode();
        hash = 31 * hash + excludes.stream().map(Pattern::pattern).sorted().collect(toList()).hashCode();
        this.hashCode = hash;
    }

    public Set<String> getPackageNames() {
        return packageNames;
    }

    public Set<Pattern> getIncludes() {
        return includes;
    }

    public Set<Pattern> getExcludes() {
        return excludes;
    }

    public boolean exports(Element element) {
        if (!(element instanceof JavaTypeElement)) {
            return true;
        }

        JavaTypeElement model = (JavaTypeElement) element;
        TypeElement type = model.getDeclaringElement();

        Name packageName = model.getTypeEnvironment().getElementUtils().getPackageOf(type).getQualifiedName();
        Name className = type.getSimpleName();

        boolean packageMatches = packageNames.stream().anyMatch(p -> p.contentEquals(packageName));
        if (!packageMatches) {
            return false;
        }

        boolean included = includes.isEmpty() || includes.stream().anyMatch(p -> p.matcher(className).matches());
        boolean excluded = !excludes.isEmpty() && excludes.stream().anyMatch(p -> p.matcher(className).matches());

        return included && !excluded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExportPackageDefinition that = (ExportPackageDefinition) o;

        if (!packageNames.equals(that.packageNames)) return false;

        Set<String> thisIncludes = includes.stream().map(Pattern::pattern).collect(toSet());
        Set<String> thatIncludes = that.includes.stream().map(Pattern::pattern).collect(toSet());

        if (!thisIncludes.equals(thatIncludes)) {
            return false;
        }

        Set<String> thisExcludes = excludes.stream().map(Pattern::pattern).collect(toSet());
        Set<String> thatExcludes = that.excludes.stream().map(Pattern::pattern).collect(toSet());

        return thisExcludes.equals(thatExcludes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "ExportPackageDefinition{" +
                "packageNames=" + packageNames +
                ", includes=" + includes +
                ", excludes=" + excludes +
                '}';
    }
}
