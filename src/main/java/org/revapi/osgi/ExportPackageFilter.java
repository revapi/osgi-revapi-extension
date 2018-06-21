package org.revapi.osgi;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.java.spi.JavaTypeElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExportPackageFilter implements ElementFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ExportPackageFilter.class);

    private Map<API, Set<ExportPackageDefinition>> exportedPackages;

    public void close() throws Exception {

    }

    public String getExtensionId() {
        return "revapi.osgi";
    }

    public Reader getJSONSchema() {
        return null;
    }

    public void initialize(AnalysisContext analysisContext) {
        exportedPackages = new HashMap<>();
        Function<API, Set<ExportPackageDefinition>> getExportedPackages = api -> {
            Set<ExportPackageDefinition> exportedPackages = new HashSet<>();
            api.getArchives().forEach(a -> addExportedPackages(a, exportedPackages));
            return exportedPackages;

        };

        exportedPackages.computeIfAbsent(analysisContext.getOldApi(), getExportedPackages);
        exportedPackages.computeIfAbsent(analysisContext.getNewApi(), getExportedPackages);
    }

    public boolean applies(Element element) {
        if (!(element instanceof JavaTypeElement)) {
            return true;
        }

        Set<ExportPackageDefinition> exportDefinitions = exportedPackages.get(element.getApi());
        if (exportDefinitions == null || exportDefinitions.isEmpty()) {
            return true;
        }

        return exportDefinitions.stream().anyMatch(d -> d.exports(element));
    }

    public boolean shouldDescendInto(Object element) {
        return true;
    }

    private void addExportedPackages(Archive archive, Set<ExportPackageDefinition> exportedPackages) {
        try (JarInputStream jar = new JarInputStream(archive.openStream())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return;
            }

            String directive = manifest.getMainAttributes().getValue("Export-Package");
            if (directive != null) {
                ExportPackageEntryParser.parse(directive, exportedPackages);
            }
        } catch (IOException e) {
            LOG.debug("Failed to open the archive " + archive + " as a jar.", e);
        }
    }
}
