package com.zamzamsuper.inventory_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zamzamsuper.inventory_service.model.Batch;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    // Finds all batches belonging to a specific GRN
    List<Batch> findByGrnId(Long grnId);

    // This query fetches all prices for a specific list of Batches in one go
    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.prices WHERE b.id IN :batchIds")
    List<Batch> findAllWithPricesByIds(@Param("batchIds") List<Long> batchIds);
}