package org.springframework.cloud.config.monitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.PatternMatchUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.Set;

public class FileWatcherService implements AutoCloseable {

    private static final Log log = LogFactory.getLog(FileWatcherService.class);

    private final WatchService watcher;
    private final Set<Path> directories;
    private final String[] excludes;

    public FileWatcherService(Set<Path> directories, String[] excludes) throws IOException {
        this.directories = directories;
        this.excludes = excludes;
        this.watcher = FileSystems.getDefault().newWatchService();
        for (Path dir : directories) {
            walkAndRegister(dir);
        }
    }

    public Set<File> pollEvents() {
        Set<File> files = new LinkedHashSet<>();
        WatchKey key = this.watcher.poll();
        while (key != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                Path item = (Path) event.context();
                Path dir = (Path) key.watchable();
                Path fullPath = dir.resolve(item);
                File file = fullPath.toFile();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    for (Path path : directories) {
                        files.addAll(walkDirectory(path));
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (file.isDirectory()) {
                        files.addAll(walkDirectory(file.toPath()));
                    } else if (!file.getPath().contains(".git") && !PatternMatchUtils.simpleMatch(this.excludes, file.getName())) {
                        if (log.isDebugEnabled()) {
                            log.debug("Watch Event: " + kind + ": " + file);
                        }
                        files.add(file);
                    }
                }
            }
            key.reset();
            key = this.watcher.poll();
        }
        return files;
    }

    private Set<File> walkDirectory(Path directory) {
        Set<File> walkedFiles = new LinkedHashSet<>();
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    walkedFiles.add(file.toFile());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk directory: " + directory.toString(), e);
        }
        return walkedFiles;
    }
    
    private void walkAndRegister(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.toFile().getPath().contains(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                register(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Registering directory for watch: " + dir);
        }
        dir.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
    }

    @Override
    public void close() throws IOException {
        this.watcher.close();
    }
} 
