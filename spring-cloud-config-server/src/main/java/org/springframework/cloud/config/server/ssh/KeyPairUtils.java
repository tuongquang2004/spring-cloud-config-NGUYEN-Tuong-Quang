package org.springframework.cloud.config.server.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collection;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.io.resource.AbstractIoResource;
import org.apache.sshd.common.util.security.SecurityUtils;

import org.springframework.util.StringUtils;

final class KeyPairUtils {

	private static final KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();

	private KeyPairUtils() {
	}

	static Collection<KeyPair> load(SessionContext session, String privateKey, String passphrase)
			throws IOException, GeneralSecurityException {

		FilePasswordProvider passwordProvider = StringUtils.hasText(passphrase) ? FilePasswordProvider.of(passphrase)
				: FilePasswordProvider.EMPTY;

		AbstractIoResource<String> resource = new AbstractIoResource<>(String.class, privateKey) {
			@Override
			public InputStream openInputStream() {
				return new ByteArrayInputStream(this.getResourceValue().getBytes());
			}
		};

		return loader.loadKeyPairs(session, resource, passwordProvider);
	}

	static boolean isValid(String privateKey, String passphrase) {
		try {
			return !KeyPairUtils.load(null, privateKey, passphrase).isEmpty();
		}
		catch (IOException | GeneralSecurityException ignored) {
			return false;
		}
	}
}
