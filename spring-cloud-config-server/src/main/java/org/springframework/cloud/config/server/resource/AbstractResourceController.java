package org.springframework.cloud.config.server.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.encryption.ResourceEncryptor;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.prepareEnvironment;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolvePlaceholders;

public abstract class AbstractResourceController {

    protected final Log logger = LogFactory.getLog(getClass());

    protected final ResourceRepository resourceRepository;
    protected final EnvironmentRepository environmentRepository;
    protected final Map<String, ResourceEncryptor> resourceEncryptorMap = new HashMap<>();
    protected final UrlPathHelper helper = new UrlPathHelper();

    protected boolean encryptEnabled = false;
    protected boolean plainTextEncryptEnabled = false;

    protected AbstractResourceController(ResourceRepository resourceRepository, EnvironmentRepository environmentRepository) {
        this.resourceRepository = resourceRepository;
        this.environmentRepository = environmentRepository;
        this.helper.setAlwaysUseFullPath(true);
    }

    protected AbstractResourceController(ResourceRepository resourceRepository, EnvironmentRepository environmentRepository,
                                         Map<String, ResourceEncryptor> resourceEncryptorMap) {
        this(resourceRepository, environmentRepository);
        if (resourceEncryptorMap != null) {
            this.resourceEncryptorMap.putAll(resourceEncryptorMap);
        }
    }

    protected String getFilePath(ServletWebRequest request, String name, String profile, String label) {
        String stem = (label != null) ? String.format("/%s/%s/%s/", name, profile, label)
                                       : String.format("/%s/%s/", name, profile);
        String path = this.helper.getPathWithinApplication(request.getRequest());
        return path.substring(path.indexOf(stem) + stem.length());
    }

    protected synchronized String retrieveInternal(ServletWebRequest request, String name, String profile, String label, String path,
                                                   boolean resolvePlaceholders, String acceptedCharset) throws IOException {
        name = Environment.normalize(name);
        label = Environment.normalize(label);
        Resource resource = this.resourceRepository.findOne(name, profile, label, path);

        if (checkNotModified(request, resource)) {
            return null;
        }

        try (InputStream is = resource.getInputStream()) {
            Charset charset = StandardCharsets.UTF_8;
            try {
                charset = Charset.forName(acceptedCharset);
            } catch (UnsupportedCharsetException e) {
                logger.warn("The accepted charset received from the client is not supported. Using UTF-8 instead.", e);
            }

            String text = StreamUtils.copyToString(is, charset);
            String ext = StringUtils.getFilenameExtension(resource.getFilename());
            if (ext != null) {
                ext = ext.toLowerCase(Locale.ROOT);
            }
            Environment environment = this.environmentRepository.findOne(name, profile, label, false);
            if (resolvePlaceholders) {
                text = resolvePlaceholders(prepareEnvironment(environment), text);
            }
            if (ext != null && encryptEnabled && plainTextEncryptEnabled) {
                ResourceEncryptor re = this.resourceEncryptorMap.get(ext);
                if (re == null) {
                    logger.warn("Cannot decrypt for extension " + ext);
                } else {
                    text = re.decrypt(text, environment);
                }
            }
            return text;
        }
    }

    protected synchronized byte[] binaryInternal(ServletWebRequest request, String name, String profile, String label, String path) throws IOException {
        name = Environment.normalize(name);
        label = Environment.normalize(label);
        Resource resource = this.resourceRepository.findOne(name, profile, label, path);

        if (checkNotModified(request, resource)) {
            return null;
        }

        prepareEnvironment(this.environmentRepository.findOne(name, profile, label));
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToByteArray(is);
        }
    }

    protected boolean checkNotModified(ServletWebRequest request, Resource resource) {
        try {
            return request != null && request.checkNotModified(resource.lastModified());
        } catch (Exception ex) {
            // Ignore optional caching failures
        }
        return false;
    }

    public void setEncryptEnabled(boolean encryptEnabled) {
        this.encryptEnabled = encryptEnabled;
    }

    public void setPlainTextEncryptEnabled(boolean plainTextEncryptEnabled) {
        this.plainTextEncryptEnabled = plainTextEncryptEnabled;
    }
}
