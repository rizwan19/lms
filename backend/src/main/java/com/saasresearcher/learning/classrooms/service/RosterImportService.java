package com.saasresearcher.learning.classrooms.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.classrooms.api.ClassStatus;
import com.saasresearcher.learning.classrooms.api.RosterImportPreview.Outcome;
import com.saasresearcher.learning.classrooms.api.RosterImportPreview.Row;
import com.saasresearcher.learning.classrooms.api.RosterImportPreview;
import com.saasresearcher.learning.classrooms.entity.Classroom;
import com.saasresearcher.learning.classrooms.repository.EnrollmentRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.identity.api.AccountProvisioning;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.identity.api.ProvisionedStudent;
import com.saasresearcher.learning.identity.api.TemporaryCredential;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV roster import (spec §8): {@code username,display_name,email}, UTF-8 with optional BOM, at most 1 MiB and
 * 1,000 rows. Preview validates every row; commit revalidates under the class lock and is all-or-nothing.
 * Existing accounts are only enrolled, never modified.
 */
@Service
public class RosterImportService {

    static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 1000;
    private static final List<String> HEADER = List.of("username", "display_name", "email");

    private final ClassAuthorization authorization;
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollments;
    private final IdentityAccess identity;
    private final AccountProvisioning provisioning;
    private final AuditLog audit;

    RosterImportService(ClassAuthorization authorization, EnrollmentService enrollmentService, EnrollmentRepository enrollments,
            IdentityAccess identity, AccountProvisioning provisioning, AuditLog audit) {
        this.authorization = authorization;
        this.enrollmentService = enrollmentService;
        this.enrollments = enrollments;
        this.identity = identity;
        this.provisioning = provisioning;
        this.audit = audit;
    }

    record CsvRow(int line, String username, String displayName, String email) {
    }

    /** A submitted row; line numbers are reassigned by position, so they cannot be spoofed. */
    public record ImportRow(String username, String displayName, String email) {
    }

    public record ImportResult(int enrolled, int created, int invited, List<TemporaryCredential> credentials) {
    }

    @Transactional(readOnly = true)
    public RosterImportPreview preview(UUID classId, InputStream csv, Actor actor) throws IOException {
        Classroom classroom = requireOpenRoster(authorization.requireClass(classId, actor));
        return validate(classroom, parse(csv));
    }

    @Transactional
    public ImportResult commit(UUID classId, String digest, List<ImportRow> submitted, Actor actor) {
        Classroom classroom = requireOpenRoster(authorization.lockClass(classId, actor));
        List<CsvRow> rows = new ArrayList<>();
        for (int i = 0; i < submitted.size(); i++) {
            ImportRow row = submitted.get(i);
            rows.add(new CsvRow(i + 2, row.username(), row.displayName(), row.email()));
        }
        RosterImportPreview current = validate(classroom, rows);
        if (!current.digest().equals(digest)) {
            throw ApiException.conflict("IMPORT_CHANGED", "The rows differ from the previewed file; preview again");
        }
        if (!current.valid()) {
            Map<String, String> errors = new LinkedHashMap<>();
            current.rows().stream().filter(row -> row.outcome() == Outcome.INVALID)
                    .forEach(row -> errors.put("rows[" + row.line() + "]", String.join("; ", row.errors())));
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_INVALID", "Some rows are no longer valid; preview again", errors);
        }

        List<TemporaryCredential> credentials = new ArrayList<>();
        int created = 0;
        int invited = 0;
        for (Row row : current.rows()) {
            UUID studentId;
            if (row.outcome() == Outcome.NEW_ACCOUNT) {
                ProvisionedStudent student = provisioning.createStudent(row.username(), row.displayName(), row.email(), actor);
                studentId = student.id();
                created++;
                if (student.temporaryCredential() != null) {
                    credentials.add(student.temporaryCredential());
                } else {
                    invited++;
                }
            } else {
                studentId = identity.findByUsername(row.username()).orElseThrow().id();
            }
            enrollmentService.enroll(classroom, studentId, actor);
        }
        audit.record(AuditEntry.success(actor.userId(), "roster.imported", "class", classId,
                Map.of("enrolled", current.rows().size(), "created", created)));
        return new ImportResult(current.rows().size(), created, invited, credentials);
    }

    private static Classroom requireOpenRoster(Classroom classroom) {
        if (classroom.status() == ClassStatus.ARCHIVED) {
            throw ApiException.conflict(ClassErrors.INVALID_CLASS_STATE, "Archived classes cannot take new students");
        }
        return classroom;
    }

    private RosterImportPreview validate(Classroom classroom, List<CsvRow> input) {
        Map<String, Integer> usernames = new HashMap<>();
        Map<String, Integer> emails = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        int newAccounts = 0;
        int existing = 0;
        for (CsvRow raw : input) {
            String username = raw.username() == null ? "" : raw.username().trim().toLowerCase(Locale.ROOT);
            String displayName = raw.displayName() == null ? "" : raw.displayName().trim();
            String email = raw.email() == null || raw.email().isBlank() ? null : raw.email().trim().toLowerCase(Locale.ROOT);
            List<String> errors = new ArrayList<>(provisioning.studentProblems(username, displayName, email));
            Integer firstUse = usernames.putIfAbsent(username, raw.line());
            if (firstUse != null) {
                errors.add("Duplicate username (also on line " + firstUse + ")");
            }
            Optional<AccountSummary> account = errors.isEmpty() ? identity.findByUsername(username) : Optional.empty();
            if (account.isPresent()) {
                errors.addAll(existingAccountProblems(account.get(), email, classroom));
            } else if (email != null) {
                Integer emailUse = emails.putIfAbsent(email, raw.line());
                if (emailUse != null) {
                    errors.add("Duplicate email (also on line " + emailUse + ")");
                } else if (identity.findByEmail(email).isPresent()) {
                    errors.add("This email belongs to another account");
                }
            }
            Outcome outcome = !errors.isEmpty() ? Outcome.INVALID : account.isPresent() ? Outcome.ENROLL_EXISTING : Outcome.NEW_ACCOUNT;
            if (outcome == Outcome.NEW_ACCOUNT) {
                newAccounts++;
            } else if (outcome == Outcome.ENROLL_EXISTING) {
                existing++;
            }
            rows.add(new Row(raw.line(), username, displayName, email, outcome, errors));
        }
        int invalid = rows.size() - newAccounts - existing;
        return new RosterImportPreview(rows, newAccounts, existing, invalid, digest(rows));
    }

    private List<String> existingAccountProblems(AccountSummary account, String email, Classroom classroom) {
        if (account.isStaff()) {
            return List.of("This is a staff account; an administrator must handle it");
        }
        if (account.status() != AccountStatus.ACTIVE) {
            return List.of("This account is disabled");
        }
        if (email != null && !email.equals(account.email())) {
            return List.of("An existing account with this username has different details");
        }
        if (enrollments.findByClassIdAndStudentId(classroom.id(), account.id()).isPresent()) {
            return List.of("Already on this roster");
        }
        return List.of();
    }

    private static List<CsvRow> parse(InputStream csv) throws IOException {
        byte[] bytes = csv.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "The file is larger than 1 MiB");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("INVALID_ENCODING", "Save the file as UTF-8 CSV");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).get().parse(new StringReader(text))) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty() || !HEADER.equals(records.getFirst().toList().stream().map(cell -> cell.trim().toLowerCase(Locale.ROOT)).toList())) {
                throw ApiException.badRequest("INVALID_HEADER", "The first line must be: username,display_name,email");
            }
            if (records.size() - 1 > MAX_ROWS) {
                throw ApiException.badRequest("TOO_MANY_ROWS", "Import at most " + MAX_ROWS + " students at a time");
            }
            return records.stream().skip(1)
                    .map(record -> new CsvRow((int) record.getRecordNumber(), cell(record, 0), cell(record, 1), cell(record, 2)))
                    .toList();
        } catch (UncheckedIOException | IllegalArgumentException malformed) {
            throw ApiException.badRequest("INVALID_CSV", "The file is not valid CSV");
        }
    }

    private static String cell(CSVRecord record, int index) {
        return index < record.size() ? record.get(index) : "";
    }

    private static String digest(List<Row> rows) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (Row row : rows) {
                sha.update((row.username() + '\u001f' + row.displayName() + '\u001f' + (row.email() == null ? "" : row.email()) + '\n')
                        .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
