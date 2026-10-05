package com.saksham.poker.common.api;

import com.saksham.poker.common.error.ErrorCode;

/**
 * The body of every failed API request.
 *
 * @param code what kind of error it is
 * @param message what went wrong and how to fix it, in plain words
 */
public record ApiError(ErrorCode code, String message) {
}
