package com.github.yehortpk.router.services;

import com.github.yehortpk.router.models.user.AccountDTO;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * This service provides methods for work with the authenticated account's own record
 */
@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserRepository userRepository;

    public AppUser findById(long userId) {
        return userRepository.findById(userId).orElseThrow(
                () -> new IllegalStateException("Authenticated account " + userId + " no longer exists"));
    }

    public AccountDTO findAccount(long userId) {
        AppUser user = findById(userId);
        return new AccountDTO(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getApiKeyPrefix(),
                user.getApiKeyHash() != null);
    }
}
