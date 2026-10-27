package com.github.yehortpk.router.services;

import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for API key generation and resolution.
 *
 * <p>The repository is a stub backed by two maps rather than a mock with per-test stubbing,
 * because every assertion here is about the round trip: generate, store, resolve. BCrypt is
 * real, since whether the stored hash actually verifies the secret is the point.
 */
class ApiKeyServiceTest {
    private static final long USER_ID = 42L;

    private UserRepository userRepository;
    private ApiKeyService apiKeyService;
    private AppUser user;
    private Map<String, AppUser> usersByPrefix;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(USER_ID);
        user.setEmail("key@example.com");
        user.setPasswordHash("irrelevant");
        user.setCreatedAt(LocalDateTime.now());
        user.setEnabled(true);

        usersByPrefix = new HashMap<>();
        userRepository = mock(UserRepository.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // save() re-indexes by the prefix the service just assigned, so a regenerated key
        // replaces the previous entry exactly as the unique index would
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            usersByPrefix.values().removeIf(indexed -> indexed.getId() == saved.getId());
            usersByPrefix.put(saved.getApiKeyPrefix(), saved);
            return saved;
        });
        when(userRepository.findByApiKeyPrefix(any())).thenAnswer(invocation ->
                Optional.ofNullable(usersByPrefix.get(invocation.getArgument(0, String.class))));

        apiKeyService = new ApiKeyService(userRepository, new BCryptPasswordEncoder());
    }

    @Test
    void GenerateApiKey_NewKey_ShouldResolveBackToItsOwner() {
        String apiKey = apiKeyService.generateApiKey(USER_ID);

        assertThat(apiKeyService.findUserByApiKey(apiKey)).contains(user);
    }

    @Test
    void GenerateApiKey_NewKey_ShouldStoreOnlyTheHashOfTheSecret() {
        String apiKey = apiKeyService.generateApiKey(USER_ID);

        // The clear-text key must not be recoverable from the row; only the prefix is plain
        assertThat(user.getApiKeyHash()).isNotNull().doesNotContain(user.getApiKeyPrefix());
        assertThat(apiKey).contains(user.getApiKeyPrefix());
        assertThat(apiKey).doesNotContain(user.getApiKeyHash());
    }

    @Test
    void GenerateApiKey_NewKey_ShouldBePrefixedForRecognisability() {
        assertThat(apiKeyService.generateApiKey(USER_ID)).startsWith("vv_");
    }

    @Test
    void GenerateApiKey_Twice_ShouldInvalidatePreviousKey() {
        String firstKey = apiKeyService.generateApiKey(USER_ID);
        String secondKey = apiKeyService.generateApiKey(USER_ID);

        assertThat(firstKey).isNotEqualTo(secondKey);
        assertThat(apiKeyService.findUserByApiKey(firstKey)).isEmpty();
        assertThat(apiKeyService.findUserByApiKey(secondKey)).contains(user);
    }

    @Test
    void GenerateApiKey_Twice_ShouldNotRepeatTheSecret() {
        assertThat(apiKeyService.generateApiKey(USER_ID))
                .isNotEqualTo(apiKeyService.generateApiKey(USER_ID));
    }

    @Test
    void FindUserByApiKey_SecretContainingTheSeparator_ShouldStillResolve() {
        // Base64url's alphabet includes '_', which is also the separator, so a generated
        // secret regularly contains one. Splitting without a limit shredded such a key into
        // four parts and rejected it, which made roughly half of all issued keys unusable.
        for (int attempt = 0; attempt < 50; attempt++) {
            String apiKey = apiKeyService.generateApiKey(USER_ID);
            assertThat(apiKeyService.findUserByApiKey(apiKey))
                    .withFailMessage("key %s did not resolve", apiKey)
                    .isPresent();
        }
    }

    @Test
    void GenerateApiKey_AnyKey_ShouldKeepTheSeparatorOutOfThePrefix() {
        // The prefix is recovered by splitting on the separator, so one inside it would
        // shift the boundary between prefix and secret
        for (int attempt = 0; attempt < 50; attempt++) {
            apiKeyService.generateApiKey(USER_ID);
            assertThat(user.getApiKeyPrefix()).doesNotContain("_");
        }
    }

    @Test
    void FindUserByApiKey_WrongKey_ShouldReturnEmpty() {
        apiKeyService.generateApiKey(USER_ID);

        assertThat(apiKeyService.findUserByApiKey("vv_nosuchprefix_nosuchsecret")).isEmpty();
    }

    @Test
    void FindUserByApiKey_RightPrefixWrongSecret_ShouldReturnEmpty() {
        // The prefix is only a lookup shortcut; it must not be sufficient on its own
        apiKeyService.generateApiKey(USER_ID);

        assertThat(apiKeyService.findUserByApiKey("vv_" + user.getApiKeyPrefix() + "_wrong-secret"))
                .isEmpty();
    }

    @Test
    void FindUserByApiKey_MalformedKey_ShouldReturnEmpty() {
        apiKeyService.generateApiKey(USER_ID);

        assertThat(apiKeyService.findUserByApiKey("not-a-key")).isEmpty();
        assertThat(apiKeyService.findUserByApiKey("vv__")).isEmpty();
        assertThat(apiKeyService.findUserByApiKey("")).isEmpty();
        assertThat(apiKeyService.findUserByApiKey(null)).isEmpty();
    }

    @Test
    void FindUserByApiKey_AccountWithNoKeyGenerated_ShouldReturnEmpty() {
        // A null hash must never be treated as "matches anything"
        user.setApiKeyPrefix("orphan");
        user.setApiKeyHash(null);
        usersByPrefix.put("orphan", user);

        assertThat(apiKeyService.findUserByApiKey("vv_orphan_whatever")).isEmpty();
    }

    @Test
    void FindUserByApiKey_DisabledAccount_ShouldReturnEmpty() {
        String apiKey = apiKeyService.generateApiKey(USER_ID);
        user.setEnabled(false);

        assertThat(apiKeyService.findUserByApiKey(apiKey)).isEmpty();
    }

    @Test
    void GenerateApiKey_UnknownUser_ShouldThrow() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> apiKeyService.generateApiKey(999L))
                .isInstanceOf(IllegalStateException.class);
    }
}
