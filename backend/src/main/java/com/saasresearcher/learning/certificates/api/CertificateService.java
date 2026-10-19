package com.saasresearcher.learning.certificates.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface CertificateService {

    /** Creates the certificate for a completion once (with its snapshot) and queues rendering; returns its ID. */
    UUID issue(UUID completionId);

    void revoke(UUID certificateId, String reason, Actor actor);

    /** Administrator-only; requires the completion to be achieved again and re-renders from the original snapshot. */
    void reinstate(UUID certificateId, String reason, Actor actor);

    PublicCertificateStatus verify(String token);
}
