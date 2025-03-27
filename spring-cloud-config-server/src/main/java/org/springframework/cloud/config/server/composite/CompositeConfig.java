package org.springframework.cloud.config.server.composite;

import java.util.List;
import java.util.Map;

public class CompositeConfig {

	private List<Map<String, Object>> composite;

	public List<Map<String, Object>> getComposite() {
		return this.composite;
	}

	public void setComposite(List<Map<String, Object>> composite) {
		this.composite = composite;
	}
}
