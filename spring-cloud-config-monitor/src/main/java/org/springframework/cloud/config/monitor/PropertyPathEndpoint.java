package org.springframework.cloud.config.monitor;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.config.monitor.strategy.NotificationStrategy;
import org.springframework.cloud.config.monitor.strategy.DelegatingNotificationExtractor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "${spring.cloud.config.monitor.endpoint.path:}/monitor")
public class PropertyPathEndpoint implements ApplicationEventPublisherAware {

	private static final Log log = LogFactory.getLog(PropertyPathEndpoint.class);

	private final PropertyPathNotificationExtractor extractor;
	private ApplicationEventPublisher applicationEventPublisher;
	private final String busId;

	public PropertyPathEndpoint(List<NotificationStrategy> strategies, String busId) {
		this.extractor = new DelegatingNotificationExtractor(strategies);
		this.busId = busId;
	}

	/* for testing */
	String getBusId() {
		return this.busId;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@PostMapping
	public Set<String> notifyByPath(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> request) {
		PropertyPathNotification notification = this.extractor.extract(headers, request);
		if (notification != null) {
			Set<String> services = new LinkedHashSet<>();
			for (String path : notification.getPaths()) {
				services.addAll(guessServiceName(path));
			}
			if (this.applicationEventPublisher != null) {
				for (String service : services) {
					log.info("Refresh for: " + service);
					this.applicationEventPublisher.publishEvent(new RefreshRemoteApplicationEvent(this, this.busId, service));
				}
				return services;
			}
		}
		return Collections.emptySet();
	}

	@PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public Set<String> notifyByForm(@RequestHeader HttpHeaders headers, @RequestParam("path") List<String> request) {
		Map<String, Object> map = new HashMap<>();
		map.put("path", request);
		return notifyByPath(headers, map);
	}

	private Set<String> guessServiceName(String path) {
		Set<String> services = new LinkedHashSet<>();
		if (path != null) {
			String stem = StringUtils.stripFilenameExtension(StringUtils.getFilename(StringUtils.cleanPath(path)));
			String name = stem + "-";
			int index;
			while ((index = name.lastIndexOf("-")) >= 0) {
				name = name.substring(0, index);
				if ("application".equals(name)) {
					services.add("*");
				} else {
					services.add(name);
				}
			}
		}
		return services;
	}
}
