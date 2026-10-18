package com.saasresearcher.learning.institution.api;

import java.time.ZoneId;
import java.util.UUID;

public record InstitutionProfile(String name, ZoneId timezone, UUID logoAssetId, long revision) {
}
