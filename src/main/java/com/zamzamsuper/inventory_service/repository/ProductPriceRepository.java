package com.zamzamsuper.inventory_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zamzamsuper.inventory_service.model.ProductPrice;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {
    // Deletes all prices linked to a specific batch
    void deleteByBatchId(Long batchId);
}
