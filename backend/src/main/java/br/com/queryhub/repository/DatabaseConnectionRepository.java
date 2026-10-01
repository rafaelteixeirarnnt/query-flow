package br.com.queryhub.repository;

import br.com.queryhub.domain.connection.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, Long> {}
