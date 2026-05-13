package com.zamzamsuper.inventory_service.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zamzamsuper.inventory_service.model.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {
    // Selects the Stock record where product_sku and location_id match
    Optional<Stock> findByProductSkuAndLocationId(String productSku, Long locationId);

    @Modifying
    @Query("UPDATE Stock s SET s.quantityOnHand = s.quantityOnHand + :qty WHERE s.id = :id")
    void incrementQuantity(@Param("id") Long id, @Param("qty") BigDecimal qty);

    @Modifying
    @Query(
            "UPDATE Stock s SET s.quantityOnHand = s.quantityOnHand - :qty WHERE s.id = :id AND"
                    + " s.quantityOnHand >= :qty")
    int decrementQuantity(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
