package org.encinet.mik;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class MikLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder builder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());
        resolver.addRepository(new RemoteRepository.Builder(
                "quickwrite-net-fluent4j",
                "default",
                "https://dl.cloudsmith.io/public/quickwrite-net/fluent4j/maven/"
        ).build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.code.gson:gson:2.11.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("net.quickwrite:fluent-builder:1.0.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.49.1.0"), null));
        builder.addLibrary(resolver);
    }
}
