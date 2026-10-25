package com.saasresearcher.learning.workspace.repository;

import com.saasresearcher.learning.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Short> {
}
