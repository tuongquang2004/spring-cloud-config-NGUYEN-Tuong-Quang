/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.StringUtils;

import org.springframework.cloud.config.server.exception.ConfigurationRetrievalException;


/**
 * An {@link EnvironmentRepository} that picks up data from a relational database. The
 * database should have a table called "PROPERTIES" with columns "APPLICATION", "PROFILE",
 * "LABEL" (with the usual {@link Environment} meaning), plus "KEY" and "VALUE" for the
 * key and value pairs in {@link Properties} style. Property values behave in the same way
 * as they would if they came from Spring Boot properties files named
 * <code>{application}-{profile}.properties</code>, including all the encryption and
 * decryption, which will be applied as post-processing steps (i.e. not in this repository
 * directly).
 *
 * @author Dave Syer
 *
 */
public class JdbcEnvironmentRepository implements EnvironmentRepository, Ordered {

	// 1. private with static comes first
	private static final Log logger = LogFactory.getLog(JdbcEnvironmentRepository.class);

	// 2. instance variables come second
	private final JdbcTemplate jdbc;
	private final PropertiesResultSetExtractor extractor;

	private int order;
	private String sql;
	private String sqlWithoutProfile;
	private boolean failOnError;
	private boolean configIncomplete;
	private String defaultLabel;

	// 3. Constructor comes third
	@Deprecated
	public JdbcEnvironmentRepository(JdbcTemplate jdbc, JdbcEnvironmentProperties properties) {
		this(jdbc, properties, new PropertiesResultSetExtractor());
	}

	public JdbcEnvironmentRepository(JdbcTemplate jdbc, JdbcEnvironmentProperties properties,
			PropertiesResultSetExtractor extractor) {
		this.jdbc = jdbc;
		this.order = properties.getOrder();
		this.sql = properties.getSql();
		this.sqlWithoutProfile = properties.getSqlWithoutProfile();
		this.failOnError = properties.isFailOnError();
		this.extractor = extractor;
		this.configIncomplete = properties.isConfigIncomplete();
		this.defaultLabel = properties.getDefaultLabel();
	}

	// 4. all public methods come fourth
	@Override
	public Environment findOne(String application, String profile, String label) {
    	Environment environment = new Environment(application, getProfiles(profile), label, null, null);
				List<String> applications = getApplications(application);
				List<String> envs = getEnvironments(profile);
				List<String> labels = getLabels(label);
		
				for (String l : labels) {
					for (String env : envs) {
						for (String app : applications) {
							addPropertySource(environment, app, env, l);
						}
					}
					if (!configIncomplete) {
						for (String app : applications) {
							addPropertySource(environment, app, null, l);
						}
					}
				}
				return environment;
			}
		
			private String[] getProfiles(String profile) {
				// TODO Auto-generated method stub
				throw new UnsupportedOperationException("Unimplemented method 'getProfiles'");
			}
		
			private List<String> getApplications(String application) {
    	if (!application.startsWith("application")) {
        	application = "application," + application;
    	}
    	List<String> applications = new ArrayList<>(
        	new LinkedHashSet<>(Arrays.asList(StringUtils.commaDelimitedListToStringArray(application))));
    	Collections.reverse(applications);
    	return applications;
	}

	private List<String> getEnvironments(String profile) {
    	if (!StringUtils.hasText(profile)) {
        	profile = "default";
    	}
    	if (configIncomplete && !profile.startsWith("default")) {
        	profile = "default," + profile;
    	}
    	List<String> envs = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(StringUtils.commaDelimitedListToStringArray(profile))));
    	Collections.reverse(envs);
    	return envs;
	}

	private List<String> getLabels(String label) {
    	if (StringUtils.isEmpty(label)) {
        	label = this.defaultLabel;
    	}
    	List<String> labels;
    	if (label.contains(",")) {
        	labels = Arrays.asList(StringUtils.commaDelimitedListToStringArray(label));
        	Collections.reverse(labels);
    	} else {
        	labels = Collections.singletonList(label);
    	}
    	return labels;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public boolean isFailOnError() {
		return failOnError;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public String getSql() {
		return this.sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	// 5. Private method comes fifth
	private void addPropertySource(Environment environment, String application, String profile, String label) {
		try {
			Map<String, Object> source;
			String name;
			String query = (profile != null) ? this.sql : this.sqlWithoutProfile;
			Object[] params = (profile != null) ? new Object[]{application, profile, label} : new Object[]{application, label};

			source = this.jdbc.query(query, this.extractor, params);
			name = (profile != null) ? application + "-" + profile : application;
			if (source != null && !source.isEmpty()) {
				environment.add(new PropertySource(name, source));
			}
		}
		catch (DataAccessException e) {
			if (!failOnError) {
				logger.warn("Database query failed, returning empty configuration.", e);
				return; 
			}
			throw new ConfigurationRetrievalException("Error retrieving configuration from database", e);
		}
	}

	// 6. Static class comes last
	public static class PropertiesResultSetExtractor implements ResultSetExtractor<Map<String, Object>> {

		@Override
		public Map<String, Object> extractData(ResultSet rs) throws SQLException, DataAccessException {
			Map<String, Object> map = new LinkedHashMap<>();
			while (rs.next()) {
				map.put(rs.getString(1), rs.getString(2));
			}
			return map;
		}

	}

}

