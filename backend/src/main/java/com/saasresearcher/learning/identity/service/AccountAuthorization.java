package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.StudentScope;
import com.saasresearcher.learning.identity.entity.UserAccount;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Account-administration scope: administrators manage everyone; teachers manage students they created or teach. */
@Component
public class AccountAuthorization {

    private final ObjectProvider<StudentScope> studentScopes;

    AccountAuthorization(ObjectProvider<StudentScope> studentScopes) {
        this.studentScopes = studentScopes;
    }

    void requireStaff(Actor actor) {
        if (!actor.isAdministrator() && !actor.has(Role.TEACHER)) {
            throw ApiException.accessDenied("Staff role required");
        }
    }

    void requireAdministrator(Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Administrator role required");
        }
    }

    /** Unrelated accounts are reported as missing so their existence is not exposed. */
    void requireManageable(UserAccount target, Actor actor) {
        requireStaff(actor);
        if (!canManage(target, actor)) {
            throw ApiException.notFound();
        }
    }

    boolean canManage(UserAccount target, Actor actor) {
        if (actor.isAdministrator()) {
            return true;
        }
        return actor.has(Role.TEACHER) && !target.isStaff()
                && (actor.userId().equals(target.createdBy()) || managedStudentIds(actor).contains(target.id()));
    }

    Set<UUID> managedStudentIds(Actor actor) {
        Set<UUID> ids = new HashSet<>();
        studentScopes.forEach(scope -> ids.addAll(scope.managedStudentIds(actor.userId())));
        return ids;
    }
}
