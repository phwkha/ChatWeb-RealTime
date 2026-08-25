package com.web.backend.repository;

import com.web.backend.controller.response.AddressResponse;
import com.web.backend.model.postgres.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    Optional<AddressEntity> findByIdAndUser_Username(Long id, String username);

    @Query("""
        SELECT new com.web.backend.controller.response.AddressResponse(
            a.id, a.houseNumber, a.street, a.ward, a.district, a.city, a.country
        )
        FROM AddressEntity a
        WHERE a.user.username = :username
        """)
    List<AddressResponse> findAddressResponsesByUsername(@Param("username") String username);

    @Query("""
        SELECT new com.web.backend.controller.response.AddressResponse(
            a.id, a.houseNumber, a.street, a.ward, a.district, a.city, a.country
        )
        FROM AddressEntity a
        WHERE a.id = :addressId AND a.user.username = :username
        """)
    Optional<AddressResponse> findAddressResponseByIdAndUsername(@Param("addressId") Long addressId, @Param("username") String username);
}
