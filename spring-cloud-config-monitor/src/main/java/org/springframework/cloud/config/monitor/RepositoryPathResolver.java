package org.springframework.cloud.config.monitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.config.server.environment.AbstractScmEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RepositoryPathResolver {

    private static final Log log = LogFactory.getLog(RepositoryPathResolver.class);

    public Set<Path> resolvePaths(List<AbstractScmEnvironmentRepository> scmRepositories,
                                  NativeEnvironmentRepository nativeEnvironmentRepository,
                                  ResourceLoader resourceLoader) {
        Set<Path> paths = new LinkedHashSet<>();

        if (scmRepositories != null) {
            for (AbstractScmEnvironmentRepository repo : scmRepositories) {
                String uri = repo.getUri();
                try {
                    Resource resource = resourceLoader.getResource(uri);
                    if (resource instanceof FileSystemResource || resource instanceof FileUrlResource) {
                        paths.add(Paths.get(resource.getURI()));
                    }
                } catch (IOException e) {
                    log.error("Cannot resolve URI for path: " + uri, e);
                }
            }
        }

        if (nativeEnvironmentRepository != null) {
            for (String location : nativeEnvironmentRepository.getSearchLocations()) {
                try {
                    Resource resource = resourceLoader.getResource(location);
                    if (resource.exists()) {
                        paths.add(Paths.get(resource.getURI()));
                    }
                } catch (Exception e) {
                    log.error("Cannot resolve URI for path: " + location, e);
                }
            }
        }

        return paths;
    }
} 
