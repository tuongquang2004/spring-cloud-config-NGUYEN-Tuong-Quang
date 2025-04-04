/*
 * Copyright 2018-2019 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;

/**
 * @author Dylan Roberts
 * @author Thomas Vitale
 */
@ConfigurationProperties("spring.cloud.config.server.jdbc")
public class JdbcEnvironmentProperties implements EnvironmentRepositoryProperties {

	private static final String DEFAULT_SQL = "SELECT \"KEY\", \"VALUE\" from PROPERTIES"
			+ " where APPLICATION=? and PROFILE=? and LABEL=?";

	private static final String DEFAULT_SQL_WITHOUT_PROFILE = "SELECT \"KEY\", \"VALUE\" from PROPERTIES"
			+ " where APPLICATION=? and PROFILE is null and LABEL=?";

	/**
	 * Flag to indicate that JDBC environment repository configuration is enabled.
	 */
	private boolean enabled = true;

	private int order = DEFAULT_ORDER - 10;

	/** SQL used to query database for keys and values. */
	private String sql = DEFAULT_SQL;

	/** SQL used to query database for keys and values when profile is null. */
	private String sqlWithoutProfile = DEFAULT_SQL_WITHOUT_PROFILE;

	/**
	 * Flag to determine how to handle query exceptions.
	 */
	private boolean failOnError = true;

	private String defaultLabel = "master";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getOrder() {
		return this.order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

	public String getSql() {
		return this.sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	public String getSqlWithoutProfile() {
		return this.sqlWithoutProfile;
	}

	public void setSqlWithoutProfile(String sqlWithoutProfile) {
		this.sqlWithoutProfile = sqlWithoutProfile;
	}

	public boolean isFailOnError() {
		return failOnError;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public boolean isConfigIncomplete() {
		// sql and sqlWithoutProfile should be customized at the same time
		return !this.sql.equals(DEFAULT_SQL) && this.sqlWithoutProfile.equals(DEFAULT_SQL_WITHOUT_PROFILE);
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

}
