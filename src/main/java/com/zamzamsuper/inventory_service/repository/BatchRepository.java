package com.zamzamsuper.inventory_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zamzamsuper.inventory_service.model.Batch;

public interface BatchRepository extends JpaRepository<Batch, Long>, JpaSpecificationExecutor<Batch> {
    @Query("SELECT b FROM Batch b " + "LEFT JOIN FETCH b.prices p " + "WHERE b.id = :id")
    Optional<Batch> findByIdWithPrices(@Param("id") Long id);

    @Query("SELECT DISTINCT b FROM Batch b " + "LEFT JOIN FETCH b.prices " + "WHERE b.id IN :batchIds")
    List<Batch> findAllByIdWithPrices(@Param("batchIds") List<Long> batchIds);
}
