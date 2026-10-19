package com.saasresearcher.learning.completion.api;

import java.util.UUID;

/** Reacts to completion changes inside the reconciling transaction (e.g. queue certificate work). */
public interface CompletionListener {

    void achieved(UUID completionId);

    void invalidated(UUID completionId);
}
