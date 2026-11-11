package com.github.yehortpk.router.exception;

/** Raised for a provider id the catalog does not have, has disabled, or nothing implements. */
public class ProviderNotAvailableException extends RuntimeException {
    public ProviderNotAvailableException(long providerId) {
        super("Provider " + providerId + " is not available");
    }
}
