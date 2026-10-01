package br.com.queryhub.repository;

import br.com.queryhub.domain.audit.QueryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryAuditRepository extends JpaRepository<QueryAudit, Long> {}
