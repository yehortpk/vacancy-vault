package com.github.yehortpk.router.exception;

/**
 * Raised when a filter expression cannot be parsed.
 *
 * <p>Checked when the filter is written, because an unparseable one stored anyway would throw
 * inside the vacancy consumer and drop the record for every account. An
 * {@link IllegalArgumentException}, so it answers 400 like other malformed input.
 */
public class InvalidFilterExpressionException extends IllegalArgumentException {
    public InvalidFilterExpressionException(String expression) {
        super("The filter expression could not be parsed: " + expression);
    }
}
