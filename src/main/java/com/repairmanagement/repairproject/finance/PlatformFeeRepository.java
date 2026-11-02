package com.repairmanagement.repairproject.finance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PlatformFeeRepository extends JpaRepository<PlatformFee,Long>{
    List<PlatformFee> findByCompany_Id(Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from PlatformFee f where f.id=:feeId")
    Optional<PlatformFee> lockForUpdate(@Param("feeId") Long feeId);
}
