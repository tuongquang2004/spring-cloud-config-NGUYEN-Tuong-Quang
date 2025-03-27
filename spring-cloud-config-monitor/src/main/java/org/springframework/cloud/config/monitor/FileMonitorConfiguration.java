/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package org.springframework.cloud.config.monitor;

 import org.apache.commons.logging.Log;
 import org.apache.commons.logging.LogFactory;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.cloud.config.server.environment.AbstractScmEnvironmentRepository;
 import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
 import org.springframework.context.ResourceLoaderAware;
 import org.springframework.context.SmartLifecycle;
 import org.springframework.context.annotation.Configuration;
 import org.springframework.core.io.ResourceLoader;
 import org.springframework.http.HttpHeaders;
 import org.springframework.scheduling.annotation.EnableScheduling;
 import org.springframework.scheduling.annotation.Scheduled;
 
 import java.io.File;
 import java.io.IOException;
 import java.nio.file.Path;
 import java.util.Collections;
 import java.util.List;
 import java.util.Set;
 
 @Configuration(proxyBeanMethods = false)
 @EnableScheduling
 public class FileMonitorConfiguration implements SmartLifecycle, ResourceLoaderAware {
 
	 private static final Log log = LogFactory.getLog(FileMonitorConfiguration.class);
 
	 @Autowired
	 private PropertyPathEndpoint endpoint;
 
	 @Autowired(required = false)
	 private List<AbstractScmEnvironmentRepository> scmRepositories;
 
	 @Autowired(required = false)
	 private NativeEnvironmentRepository nativeEnvironmentRepository;
 
	 private boolean running = false;
 
	 private ResourceLoader resourceLoader;
 
	 private FileWatcherService fileWatcherService;
 
	 private final String[] excludes = new String[]{".*", "#*", "*#"};
 
	 @Override
	 public void setResourceLoader(ResourceLoader resourceLoader) {
		 this.resourceLoader = resourceLoader;
	 }
 
	 @Override
	 public synchronized void start() {
		 if (!this.running) {
			 Set<Path> directories = new RepositoryPathResolver()
					 .resolvePaths(scmRepositories, nativeEnvironmentRepository, resourceLoader);
			 if (directories != null && !directories.isEmpty()) {
				 try {
					 this.fileWatcherService = new FileWatcherService(directories, excludes);
					 log.info("Monitoring for local config changes: " + directories);
				 } catch (IOException e) {
					 log.error("Failed to initialize watcher", e);
				 }
			 } else {
				 log.info("Not monitoring for local config changes");
			 }
			 this.running = true;
		 }
	 }
 
	 @Override
	 public synchronized void stop() {
		 if (this.running && this.fileWatcherService != null) {
			 try {
				 this.fileWatcherService.close();
			 } catch (IOException e) {
				 log.error("Failed to close file watcher service", e);
			 }
			 this.running = false;
		 }
	 }
 
	 @Override
	 public void stop(Runnable callback) {
		 stop();
		 callback.run();
	 }
 
	 @Override
	 public boolean isRunning() {
		 return this.running;
	 }
 
	 @Override
	 public boolean isAutoStartup() {
		 return true;
	 }
 
	 @Override
	 public int getPhase() {
		 return 0;
	 }
 
	 @Scheduled(fixedRateString = "${spring.cloud.config.server.monitor.fixedDelay:5000}")
	 public void poll() {
		 if (fileWatcherService == null) return;
 
		 for (File file : fileWatcherService.pollEvents()) {
			 this.endpoint.notifyByPath(new HttpHeaders(),
					 Collections.singletonMap("path", file.getAbsolutePath()));
		 }
	 }
 } 
 