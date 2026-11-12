package com.repairmanagement.repairproject.company;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {
    @EntityGraph(attributePaths = "company")
    List<CompanyMember> findByUser_IdAndActiveTrue(Long userId);

    Optional<CompanyMember> findByCompany_IdAndUser_IdAndActiveTrue(Long companyId, Long userId);

    boolean existsByCompany_IdAndUser_Id(Long companyId, Long userId);

    Optional<CompanyMember> findByCompany_IdAndUser_Id(Long companyId, Long userId);

    long countByCompany_IdAndActiveTrueAndMemberRole(Long companyId, MemberRole role);

    boolean existsByUser_IdAndActiveTrue(Long userId);

    @EntityGraph(attributePaths = "user")
    List<CompanyMember> findByCompany_IdAndActiveTrue(Long companyId);
}
