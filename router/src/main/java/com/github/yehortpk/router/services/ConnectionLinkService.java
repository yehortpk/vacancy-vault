package com.github.yehortpk.router.services;

import com.github.yehortpk.router.exception.DestinationAlreadyLinkedException;
import com.github.yehortpk.router.exception.InvalidLinkTokenException;
import com.github.yehortpk.router.exception.LinkSessionNotFoundException;
import com.github.yehortpk.router.exception.ProviderNotAvailableException;
import com.github.yehortpk.router.models.connection.*;
import com.github.yehortpk.router.models.connector.ConnectorStatusDTO;
import com.github.yehortpk.router.models.provider.Provider;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.providers.ConnectionProvider;
import com.github.yehortpk.router.providers.ConnectionProviderRegistry;
import com.github.yehortpk.router.repositories.ConnectionRepository;
import com.github.yehortpk.router.repositories.LinkSessionRepository;
import com.github.yehortpk.router.repositories.ProviderRepository;
import com.github.yehortpk.router.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * The provider-independent half of linking: one way in, {@link #startLink}, and one signal
 * back, {@link #completeLink}.
 *
 * <p>Starting creates a link session with a one-time token and renders the provider's
 * {@code link_instructions} with it; the provider is not called at this point. Whatever
 * happens next is the provider's business, until its service reports the token back with a
 * destination. The core then checks the token, enforces that the
 * destination belongs to one account, and creates the {@code connection} row and the
 * provider's details row in one transaction.
 */
@Service
@RequiredArgsConstructor
public class ConnectionLinkService {
    private static final int TOKEN_BYTES = 32;
    /** Where the token goes in a provider's {@code link_instructions}; a CHECK requires it. */
    private static final String TOKEN_PLACEHOLDER = "{token}";

    private final ConnectionProviderRegistry connectionProviderRegistry;
    private final ConnectionService connectionService;
    private final ConnectionRepository connectionRepository;
    private final LinkSessionRepository linkSessionRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${link.session-ttl}")
    private Duration sessionTTL;

    /** 256 random bits, Base64url: 43 characters, short enough for a Telegram deep link. */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String normalizeTitle(String title) {
        return title == null || title.isBlank() ? null : title.strip();
    }

    private LinkSessionStatus resolveStatus(LinkSession session) {
        if (session.getConsumedAt() != null) {
            return LinkSessionStatus.LINKED;
        }
        return LocalDateTime.now().isBefore(session.getExpiresAt())
                ? LinkSessionStatus.PENDING
                : LinkSessionStatus.EXPIRED;
    }

    private static String renderInstructions(Provider provider, String token) {
        return provider.getLinkInstructions().replace(TOKEN_PLACEHOLDER, token);
    }

    private LinkSessionDTO toDTO(LinkSession session, String token, String instructions) {
        return new LinkSessionDTO(
                session.getId(),
                session.getProvider().getId(),
                resolveStatus(session),
                session.getExpiresAt(),
                token,
                instructions,
                session.getConnection() == null ? null : connectionService.toDTO(session.getConnection()));
    }

    private <D> Optional<Long> findConnectionId(ConnectionProvider<D> provider, Map<String, String> fields) {
        return provider.findConnectionId(provider.readDestination(fields));
    }

    /**
     * Inserts the {@code connection} row, then the provider's details row.
     *
     * <p>The lookup in {@link #linkDestination} has already ruled out a destination held by
     * another account, so a uniqueness violation here means a concurrent link for the same
     * destination committed in between. It is reported the same way.
     */
    private <D> Connection createConnection(ConnectionProvider<D> provider, LinkSession session,
                                            D destination, String title) {
        Connection connection = new Connection();
        connection.setUser(session.getUser());
        connection.setProvider(session.getProvider());
        connection.setTitle(normalizeTitle(title));
        connection.setLinkedAt(LocalDateTime.now());
        connectionRepository.saveAndFlush(connection);

        try {
            provider.saveDestination(connection, destination);
        } catch (DataIntegrityViolationException ex) {
            throw new DestinationAlreadyLinkedException();
        }
        return connection;
    }

    /**
     * Links a destination to the session's owner and consumes the session.
     *
     * <p>A destination the owner has already linked is accepted and returns the existing
     * connection: linking twice is a user mistake, not an error. One held by a different
     * account is refused.
     */
    private <D> ConnectionDTO linkDestination(ConnectionProvider<D> provider, LinkSession session,
                                              D destination, String title) {
        AppUser owner = session.getUser();

        Connection connection = provider.findConnectionId(destination)
                .flatMap(connectionRepository::findById)
                .map(existing -> {
                    if (existing.getUser().getId() != owner.getId()) {
                        throw new DestinationAlreadyLinkedException();
                    }
                    return existing;
                })
                .orElseGet(() -> createConnection(provider, session, destination, title));

        session.setConsumedAt(LocalDateTime.now());
        session.setConnection(connection);
        linkSessionRepository.save(session);

        return connectionService.toDTO(connection);
    }

    private <D> ConnectionDTO linkReportedDestination(ConnectionProvider<D> provider, LinkSession session,
                                                      Map<String, String> fields, String title) {
        return linkDestination(provider, session, provider.readDestination(fields), title);
    }

    /**
     * Starts linking a destination of the given provider for the user.
     *
     * <p>A new session supersedes the user's unconsumed ones for the same provider, so only the
     * latest token works and abandoned sessions do not pile up. The token is returned here and
     * nowhere else, since only its hash is stored.
     *
     * @throws ProviderNotAvailableException if the provider is unknown or disabled
     */
    @Transactional
    public LinkSessionDTO startLink(long userId, long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .filter(Provider::isEnabled)
                .orElseThrow(() -> new ProviderNotAvailableException(providerId));
        AppUser user = userRepository.findById(userId).orElseThrow(
                () -> new IllegalStateException("Authenticated account " + userId + " no longer exists"));

        linkSessionRepository.deleteUnconsumed(userId, providerId);

        String token = generateToken();
        LocalDateTime now = LocalDateTime.now();

        LinkSession session = new LinkSession();
        session.setUser(user);
        session.setProvider(provider);
        session.setTokenHash(hashToken(token));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(sessionTTL));
        linkSessionRepository.save(session);

        return toDTO(session, token, renderInstructions(provider, token));
    }

    /**
     * The "linked" signal: a provider reports the token back with the destination it found.
     *
     * @throws InvalidLinkTokenException if the token is unknown, used, expired or belongs to
     *         another provider's session
     */
    @Transactional
    public ConnectionDTO completeLink(long providerId, String token, String title,
                                      Map<String, String> destination) {
        LinkSession session = linkSessionRepository.findForUpdate(hashToken(token))
                .filter(found -> found.getProvider().getId() == providerId)
                .filter(found -> resolveStatus(found) == LinkSessionStatus.PENDING)
                .orElseThrow(InvalidLinkTokenException::new);

        return linkReportedDestination(
                connectionProviderRegistry.findProvider(providerId), session, destination, title);
    }

    /** Where one of the user's link sessions stands; the account panel polls this. */
    public LinkSessionDTO findLinkSession(long sessionId, long userId) {
        return linkSessionRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> toDTO(session, null, null))
                .orElseThrow(LinkSessionNotFoundException::new);
    }

    /**
     * A provider reporting that a destination should stop receiving notifications, e.g. the
     * bot's {@code /unlink}. A destination that is not linked is treated as done.
     */
    @Transactional
    public void unlinkDestination(long providerId, Map<String, String> destination) {
        findConnectionId(connectionProviderRegistry.findProvider(providerId), destination)
                .flatMap(connectionRepository::findById)
                .ifPresent(connectionService::deleteConnection);
    }

    /**
     * What a provider may tell a destination about itself.
     *
     * <p>The filter counts are zero until filters belong to users and bind to connections.
     */
    public ConnectorStatusDTO findConnectorStatus(long providerId, Map<String, String> destination) {
        return findConnectionId(connectionProviderRegistry.findProvider(providerId), destination)
                .flatMap(connectionRepository::findById)
                .map(connection -> new ConnectorStatusDTO(true, connection.getUser().getEmail(), 0, 0))
                .orElseGet(ConnectorStatusDTO::notLinked);
    }
}
