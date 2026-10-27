package com.github.yehortpk.router.services;

import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and resolves the long-lived API key a user pastes into a messenger to link it.
 *
 * <p>The key reads {@code vv_<prefix>_<secret>}. Only the secret is hashed; the prefix is
 * stored in the clear and indexed, which turns {@code /link} into one row read plus one
 * BCrypt comparison. Hashing the whole key instead would mean BCrypt-comparing against every
 * account on every link attempt, which does not scale and is the reason the prefix exists.
 *
 * <p>Regenerating replaces both columns, so the previous key stops working. Connections
 * already made survive it: the key authorizes new links, it is not a session credential.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private static final String KEY_PREFIX = "vv";
    private static final String KEY_SEPARATOR = "_";
    private static final int PREFIX_BYTES = 6;
    private static final int SECRET_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    private String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A random token with the separator removed, for the prefix.
     *
     * <p>The prefix is read back by splitting on the separator, so it must not contain one;
     * Base64url's alphabet does. Dropping the character shortens the prefix slightly, which
     * costs nothing — the prefix is only a lookup shortcut and carries no secrecy.
     */
    private String randomPrefix() {
        return randomToken(PREFIX_BYTES).replace(KEY_SEPARATOR, "");
    }

    /**
     * Splits a pasted key into its prefix and secret halves.
     *
     * <p>Split with a limit, and only on the first two separators: Base64url's alphabet
     * includes the separator itself, so a secret is quite likely to contain one and an
     * unbounded split would shred a perfectly valid key into four or more parts.
     *
     * @return empty if the key is not shaped like one this service issues
     */
    private Optional<String[]> splitKey(String apiKey) {
        if (apiKey == null) {
            return Optional.empty();
        }

        String[] parts = apiKey.strip().split(KEY_SEPARATOR, 3);
        if (parts.length != 3 || !KEY_PREFIX.equals(parts[0])
                || parts[1].isEmpty() || parts[2].isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(parts);
    }

    /**
     * Generates a new key for the user, replacing any previous one.
     *
     * @return the full key in clear text — the only time it is ever available
     */
    @Transactional
    public String generateApiKey(long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow(
                () -> new IllegalStateException("Authenticated account " + userId + " no longer exists"));

        String prefix = randomPrefix();
        String secret = randomToken(SECRET_BYTES);

        user.setApiKeyPrefix(prefix);
        user.setApiKeyHash(passwordEncoder.encode(secret));
        userRepository.save(user);

        return KEY_PREFIX + KEY_SEPARATOR + prefix + KEY_SEPARATOR + secret;
    }

    /**
     * Resolves the account a pasted key belongs to.
     *
     * @return empty if the key is malformed, its prefix is unknown, its secret does not
     *         match, or the account is disabled
     */
    public Optional<AppUser> findUserByApiKey(String apiKey) {
        Optional<String[]> parts = splitKey(apiKey);
        if (parts.isEmpty()) {
            return Optional.empty();
        }

        String prefix = parts.get()[1];
        String secret = parts.get()[2];

        return userRepository.findByApiKeyPrefix(prefix)
                .filter(AppUser::isEnabled)
                .filter(user -> user.getApiKeyHash() != null)
                .filter(user -> passwordEncoder.matches(secret, user.getApiKeyHash()));
    }
}
