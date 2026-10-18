package com.saasresearcher.learning.institution.api;

import java.time.ZoneId;

public record InstitutionProfile(String name, ZoneId timezone, long revision) {
}
