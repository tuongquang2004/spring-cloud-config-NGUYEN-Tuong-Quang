package org.springframework.cloud.config.server.resource;

import java.io.IOException;
import java.util.Map;

import org.springframework.cloud.config.server.encryption.ResourceEncryptor;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

@RestController
@RequestMapping(method = RequestMethod.GET, path = "${spring.cloud.config.server.prefix:}")
public class ResourceController extends AbstractResourceController {

	public ResourceController(ResourceRepository resourceRepository, EnvironmentRepository environmentRepository,
			Map<String, ResourceEncryptor> resourceEncryptorMap) {
		super(resourceRepository, environmentRepository, resourceEncryptorMap);
	}

	public ResourceController(ResourceRepository resourceRepository, EnvironmentRepository environmentRepository) {
		super(resourceRepository, environmentRepository);
	}

	@GetMapping("/{name}/{profile}/{label}/**")
	public String retrieve(@PathVariable String name, @PathVariable String profile, @PathVariable String label,
			ServletWebRequest request, @RequestParam(defaultValue = "true") boolean resolvePlaceholders,
			@RequestHeader(value = HttpHeaders.ACCEPT_CHARSET, required = false,
					defaultValue = "UTF-8") String acceptedCharset) throws IOException {
		String path = getFilePath(request, name, profile, label);
		return retrieveInternal(request, name, profile, label, path, resolvePlaceholders, acceptedCharset);
	}

	@GetMapping(value = "/{name}/{profile}/{path:.*}", params = "useDefaultLabel")
	public String retrieveDefault(@PathVariable String name, @PathVariable String profile, @PathVariable String path,
			ServletWebRequest request, @RequestParam(defaultValue = "true") boolean resolvePlaceholders,
			@RequestHeader(value = HttpHeaders.ACCEPT_CHARSET, required = false,
					defaultValue = "UTF-8") String acceptedCharset) throws IOException {
		return retrieveInternal(request, name, profile, null, path, resolvePlaceholders, acceptedCharset);
	}

	@GetMapping(value = "/{name}/{profile}/{label}/**", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public byte[] binary(@PathVariable String name, @PathVariable String profile, @PathVariable String label,
			ServletWebRequest request) throws IOException {
		String path = getFilePath(request, name, profile, label);
		return binaryInternal(request, name, profile, label, path);
	}

	@GetMapping(value = "/{name}/{profile}/{path:.*}", params = "useDefaultLabel",
			produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public byte[] binaryDefault(@PathVariable String name, @PathVariable String profile, @PathVariable String path,
			ServletWebRequest request) throws IOException {
		return binaryInternal(request, name, profile, null, path);
	}

	// Used in unit tests
	String retrieve(String name, String profile, String label, String path, boolean resolvePlaceholders,
			String acceptedCharset) throws IOException {
		return retrieveInternal(null, name, profile, label, path, resolvePlaceholders, acceptedCharset);
	}

	byte[] binary(String name, String profile, String label, String path) throws IOException {
		return binaryInternal(null, name, profile, label, path);
	}
}
