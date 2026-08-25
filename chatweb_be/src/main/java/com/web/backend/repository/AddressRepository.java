package com.web.backend.repository;

import com.web.backend.model.postgres.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    Optional<AddressEntity> findByIdAndUser_Username(Long id, String username);

    List<AddressEntity> findAllByUser_Username(String username);
}
