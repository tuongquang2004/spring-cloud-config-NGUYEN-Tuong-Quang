/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.cloud.bootstrap.encrypt.RsaProperties;
import org.springframework.cloud.bootstrap.encrypt.TextEncryptorUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.CONFIG_DISCOVERY_ENABLED;

public class ConfigServerConfigDataLocationResolver
		implements ConfigDataLocationResolver<ConfigServerConfigDataResource>, Ordered {

	/**
	 * Prefix for Config Server imports.
	 */
	public static final String PREFIX = "configserver:";
	static final boolean RSA_IS_PRESENT = ClassUtils
		.isPresent("org.springframework.security.rsa.crypto.RsaSecretEncryptor", null);

	private final Log log;

	public ConfigServerConfigDataLocationResolver(DeferredLogFactory factory) {
		this.log = factory.getLog(ConfigServerConfigDataLocationResolver.class);
	}

	@Override
	public int getOrder() {
		return -1;
	}

	/*
	 * Depending on whether encrypt.key is set as an environment or system property we may
	 * have created a TextEncryptor implementation or just created a
	 * FailsafeTextEncryptor. This is because when TextEncryptorConfigBootstrapper runs we
	 * have not yet loaded any configuration files (application.yaml | properties etc).
	 * However, at this point when the ConfigServerConfigDataLocationResolver is resolving
	 * configuration we would have resolved the configuration files on the classpath at
	 * least so we can potentially create a properly configured TextEncryptor. So if the
	 * FailsafeTextEncryptor is in the context and we can create a TextEncryptor then we
	 * set the delegate in the FailsafeTextEncryptor so that we can decrypt any encrypted
	 * properties at this point.
	 */
	protected void setTextEncryptorDelegate(ConfigDataLocationResolverContext context) {
		if (context.getBootstrapContext().isRegistered(TextEncryptor.class)) {
			Binder binder = context.getBinder();
			KeyProperties keyProperties = binder.bindOrCreate(KeyProperties.PREFIX, Bindable.of(KeyProperties.class));
			boolean textEncryptorRegistered = context.getBootstrapContext().isRegistered(TextEncryptor.class);
			if (TextEncryptorUtils.keysConfigured(keyProperties) && textEncryptorRegistered) {
				TextEncryptor textEncryptor = context.getBootstrapContext().get(TextEncryptor.class);
				if (textEncryptor instanceof TextEncryptorUtils.FailsafeTextEncryptor failsafeTextEncryptor) {
					TextEncryptor delegate;
					if (RSA_IS_PRESENT) {
						RsaProperties rsaProperties = binder.bindOrCreate(RsaProperties.PREFIX,
								Bindable.of(RsaProperties.class));
						delegate = TextEncryptorUtils.createTextEncryptor(keyProperties, rsaProperties);
					}
					else {
						delegate = new EncryptorFactory(keyProperties.getSalt()).create(keyProperties.getKey());
					}
					failsafeTextEncryptor.setDelegate(delegate);
				}
			}
		}
	}

	protected PropertyHolder loadProperties(ConfigDataLocationResolverContext context, String uris) {
		Binder binder = context.getBinder();
		BindHandler bindHandler = getBindHandler(context);
	
		// Create ConfigClientProperties from binder
		ConfigClientProperties configClientProperties = binder
			.bind(ConfigClientProperties.PREFIX, Bindable.of(ConfigClientProperties.class), bindHandler)
			.orElseGet(ConfigClientProperties::new);
	
		// Update configClientProperties (URI, username, password, application name)
		updateConfigClientProperties(context, configClientProperties, uris);
	
		PropertyHolder holder = new PropertyHolder();
		holder.properties = configClientProperties;
	
		// Bind retry properties
		holder.retryProperties = binder.bind(RetryProperties.PREFIX, RetryProperties.class)
			.orElseGet(RetryProperties::new);
	
		// Update retry properties if URIs have query params
		if (StringUtils.hasText(uris)) {
			String[] uriArray = StringUtils.commaDelimitedListToStringArray(uris);
			String paramStr = null;
			for (int i = 0; i < uriArray.length; i++) {
				int paramIdx = uriArray[i].indexOf('?');
				if (paramIdx > 0) {
					if (i == 0) {
						paramStr = uriArray[i].substring(paramIdx + 1);
					}
					uriArray[i] = uriArray[i].substring(0, paramIdx);
				}
			}
	
			if (StringUtils.hasText(paramStr)) {
				Properties propertiesMap = StringUtils
					.splitArrayElementsIntoProperties(StringUtils.delimitedListToStringArray(paramStr, "&"), "=");
				if (propertiesMap != null) {
					PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
					map.from(() -> propertiesMap.getProperty("fail-fast"))
						.as(Boolean::valueOf)
						.to(configClientProperties::setFailFast);
					map.from(() -> propertiesMap.getProperty("max-attempts"))
						.as(Integer::valueOf)
						.to(holder.retryProperties::setMaxAttempts);
					map.from(() -> propertiesMap.getProperty("max-interval"))
						.as(Long::valueOf)
						.to(holder.retryProperties::setMaxInterval);
					map.from(() -> propertiesMap.getProperty("multiplier"))
						.as(Double::valueOf)
						.to(holder.retryProperties::setMultiplier);
					map.from(() -> propertiesMap.getProperty("initial-interval"))
						.as(Long::valueOf)
						.to(holder.retryProperties::setInitialInterval);
				}
			}
	
			configClientProperties.setUri(uriArray);
		}
	
		return holder;
	}
	
	/**
	 * Update ConfigClientProperties
	 */
	private void updateConfigClientProperties(ConfigDataLocationResolverContext context,
											  ConfigClientProperties properties, String uris) {
		Binder binder = context.getBinder();
		BindHandler bindHandler = getBindHandler(context);
	
		// If discovery enabled, take data from ConfigServerInstanceMonitor
		boolean discoveryEnabled = binder
			.bind(CONFIG_DISCOVERY_ENABLED, Bindable.of(Boolean.class), bindHandler)
			.orElse(false);
	
		if (discoveryEnabled && context.getBootstrapContext().isRegistered(ConfigServerInstanceMonitor.class)) {
			ConfigServerInstanceMonitor instanceMonitor = context.getBootstrapContext()
				.get(ConfigServerInstanceMonitor.class);
	
			properties.setUri(instanceMonitor.getUri());
			properties.setPassword(instanceMonitor.getPassword());
			properties.setUsername(instanceMonitor.getUsername());
		}
	
		// If didn't have application name, take one from spring.application.name
		if (!StringUtils.hasText(properties.getName()) || "application".equals(properties.getName())) {
			String applicationName = binder.bind("spring.application.name", Bindable.of(String.class), bindHandler)
				.orElse("application");
			properties.setName(applicationName);
		}
	}
	

	private BindHandler getBindHandler(ConfigDataLocationResolverContext context) {
		return context.getBootstrapContext().getOrElse(BindHandler.class, null);
	}

	@Deprecated
	protected RestTemplate createRestTemplate(ConfigClientProperties properties) {
		return null;
	}

	protected Log getLog() {
		return this.log;
	}

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		if (!location.hasPrefix(getPrefix())) {
			return false;
		}
		return context.getBinder().bind(ConfigClientProperties.PREFIX + ".enabled", Boolean.class).orElse(true);
	}

	protected String getPrefix() {
		return PREFIX;
	}

	@Override
	public List<ConfigServerConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location)
			throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
		return resolveProfileSpecific(context, location, null);
	}

	@Override
	public List<ConfigServerConfigDataResource> resolveProfileSpecific(
			ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location, Profiles profiles)
			throws ConfigDataLocationNotFoundException {
		setTextEncryptorDelegate(resolverContext);
		String uris = location.getNonPrefixedValue(getPrefix());
		PropertyHolder propertyHolder = loadProperties(resolverContext, uris);
		ConfigClientProperties properties = propertyHolder.properties;

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		bootstrapContext.register(ConfigClientProperties.class,
				InstanceSupplier.of(properties).withScope(BootstrapRegistry.Scope.PROTOTYPE));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext()
			.getBeanFactory()
			.registerSingleton("configDataConfigClientProperties",
					event.getBootstrapContext().get(ConfigClientProperties.class)));

		bootstrapContext.registerIfAbsent(ConfigClientRequestTemplateFactory.class,
				context -> new ConfigClientRequestTemplateFactory(log, context.get(ConfigClientProperties.class)));

		bootstrapContext.registerIfAbsent(RestTemplate.class, context -> {
			ConfigClientRequestTemplateFactory factory = context.get(ConfigClientRequestTemplateFactory.class);
			RestTemplate restTemplate = createRestTemplate(factory.getProperties());
			if (restTemplate != null) {
				// shouldn't normally happen
				return restTemplate;
			}
			return factory.create();
		});

		bootstrapContext.registerIfAbsent(PropertyResolver.class,
				context -> new PropertyResolver(resolverContext.getBinder(), getBindHandler(resolverContext)));

		ConfigServerConfigDataResource resource = new ConfigServerConfigDataResource(properties, location.isOptional(),
				profiles);
		resource.setProfileSpecific(!ObjectUtils.isEmpty(profiles));
		resource.setLog(log);
		resource.setRetryProperties(propertyHolder.retryProperties);

		boolean discoveryEnabled = resolverContext.getBinder()
			.bind(CONFIG_DISCOVERY_ENABLED, Bindable.of(Boolean.class), getBindHandler(resolverContext))
			.orElse(false);

		boolean retryEnabled = resolverContext.getBinder()
			.bind(ConfigClientProperties.PREFIX + ".fail-fast", Bindable.of(Boolean.class),
					getBindHandler(resolverContext))
			.orElse(false);

		if (discoveryEnabled) {
			log.debug(LogMessage.format("discovery enabled"));
			// register ConfigServerInstanceMonitor
			bootstrapContext.registerIfAbsent(ConfigServerInstanceMonitor.class, context -> {
				ConfigServerInstanceProvider.Function function = context
					.get(ConfigServerInstanceProvider.Function.class);

				ConfigServerInstanceProvider instanceProvider;
				if (ConfigClientRetryBootstrapper.RETRY_IS_PRESENT && retryEnabled) {
					log.debug(LogMessage.format("discovery plus retry enabled"));
					RetryTemplate retryTemplate = RetryTemplateFactory.create(propertyHolder.retryProperties, log);
					instanceProvider = new ConfigServerInstanceProvider(function, resolverContext.getBinder(),
							getBindHandler(resolverContext)) {
						@Override
						public List<ServiceInstance> getConfigServerInstances(String serviceId) {
							return retryTemplate.execute(retryContext -> super.getConfigServerInstances(serviceId));
						}
					};
				}
				else {
					instanceProvider = new ConfigServerInstanceProvider(function, resolverContext.getBinder(),
							getBindHandler(resolverContext));
				}
				instanceProvider.setLog(log);

				ConfigClientProperties clientProperties = context.get(ConfigClientProperties.class);
				ConfigServerInstanceMonitor instanceMonitor = new ConfigServerInstanceMonitor(log, clientProperties,
						instanceProvider);
				instanceMonitor.setRefreshOnStartup(false);
				instanceMonitor.refresh();
				return instanceMonitor;
			});
			// promote ConfigServerInstanceMonitor to bean so updates can be made to
			// config client uri
			bootstrapContext.addCloseListener(event -> {
				ConfigServerInstanceMonitor configServerInstanceMonitor = event.getBootstrapContext()
					.get(ConfigServerInstanceMonitor.class);
				event.getApplicationContext()
					.getBeanFactory()
					.registerSingleton("configServerInstanceMonitor", configServerInstanceMonitor);
			});
		}

		List<ConfigServerConfigDataResource> locations = new ArrayList<>();
		locations.add(resource);

		return locations;
	}

	public static class PropertyResolver {

		private final Binder binder;

		private final BindHandler bindHandler;

		public PropertyResolver(Binder binder, BindHandler bindHandler) {
			this.binder = binder;
			this.bindHandler = bindHandler;
		}

		public <T> T get(String key, Class<T> type, T defaultValue) {
			return binder.bind(key, Bindable.of(type)).orElse(defaultValue);
		}

		public <T> T resolveConfigurationProperties(String prefix, Class<T> type, Supplier<T> defaultValue) {
			return binder.bind(prefix, Bindable.of(type), bindHandler).orElseGet(defaultValue);
		}

		public <T> T resolveOrCreateConfigurationProperties(String prefix, Class<T> type) {
			return binder.bindOrCreate(prefix, Bindable.of(type), bindHandler);
		}

	}

	private class PropertyHolder {

		ConfigClientProperties properties;

		RetryProperties retryProperties;

	}

}
