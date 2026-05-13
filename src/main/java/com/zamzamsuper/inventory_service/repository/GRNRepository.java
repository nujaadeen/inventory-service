package com.zamzamsuper.inventory_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zamzamsuper.inventory_service.model.GRN;

public interface GRNRepository extends JpaRepository<GRN, Long>, JpaSpecificationExecutor<GRN> {
    @Query("SELECT g FROM GRN g " + "LEFT JOIN FETCH g.batches b " + "WHERE g.id = :id")
    Optional<GRN> findByIdWithBatches(@Param("id") Long id);

    @Query("SELECT DISTINCT g FROM GRN g " + "LEFT JOIN FETCH g.batches b " + "WHERE g.id IN :ids")
    List<GRN> findAllByIdWithBatches(@Param("ids") List<Long> ids);
}
