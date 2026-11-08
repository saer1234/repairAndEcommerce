package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.MemberRole;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportAgentPresenceRepository extends JpaRepository<SupportAgentPresence, Long> {
    Optional<SupportAgentPresence> findByCompany_IdAndUser_IdAndSessionId(
        Long companyId, Long userId, String sessionId);

    @Query("""
        select count(distinct p.user.id) from SupportAgentPresence p, CompanyMember m
        where p.company.id = :companyId and p.expiresAt > :now
          and m.company.id = p.company.id and m.user.id = p.user.id
          and m.active = true and m.user.active = true and m.memberRole in :roles
        """)
    long countAvailableAgents(@Param("companyId") Long companyId, @Param("now") LocalDateTime now,
                              @Param("roles") Collection<MemberRole> roles);

    @Modifying
    @Query("delete from SupportAgentPresence p where p.company.id = :companyId and p.user.id = :userId and p.sessionId = :sessionId")
    int deleteSession(@Param("companyId") Long companyId, @Param("userId") Long userId,
                      @Param("sessionId") String sessionId);

    @Modifying
    @Query("delete from SupportAgentPresence p where p.expiresAt < :before")
    int deleteExpired(@Param("before") LocalDateTime before);
}
