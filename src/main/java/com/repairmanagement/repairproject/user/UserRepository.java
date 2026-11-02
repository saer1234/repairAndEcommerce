package com.repairmanagement.repairproject.user;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.repairmanagement.repairproject.company.CompanyMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("""
        SELECT u FROM User u WHERE u.active = true AND u.platformRole = :role
        AND NOT EXISTS (SELECT m.id FROM CompanyMember m WHERE m.user = u AND m.active = true)
        AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
             OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%')))
        ORDER BY u.fullName, u.id
        """)
    List<User> findEligibleRepairClients(@Param("role") PlatformRole role,
                                          @Param("term") String term, Pageable pageable);
}
