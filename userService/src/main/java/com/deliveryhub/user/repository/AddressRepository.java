package com.deliveryhub.user.repository;

import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.entity.GeoStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByPrimaryDescCreatedAtAscIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    List<Address> findByGeoStatusOrderByUpdatedAtAscIdAsc(GeoStatus geoStatus, Limit limit);

    Optional<Address> findFirstByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.primary = false where a.userId = :userId and a.primary = true")
    void clearPrimary(@Param("userId") Long userId);
}
