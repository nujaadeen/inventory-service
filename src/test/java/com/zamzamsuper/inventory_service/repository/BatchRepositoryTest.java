package com.zamzamsuper.inventory_service.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.enums.PriceType;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Location;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.model.Supplier;

@DataJpaTest
public class BatchRepositoryTest {

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private TestEntityManager entityManager;

    private List<Long> savedBatchIds;

    @BeforeEach
    void setUp() {
        // 1. Create and Persist Required Parent Entities
        Location location = entityManager.persistAndFlush(Location.builder().name("Test Location").active(true).build());
        Supplier supplier = entityManager.persistAndFlush(Supplier.builder().name("Test Supplier").phone("0000").build());

        Stock stock = entityManager.persistAndFlush(Stock.builder()
                .productSku("SKU-001")
                .location(location)
                .quantityOnHand(BigDecimal.valueOf(100))
                .reorderLevel(BigDecimal.valueOf(10))
                .build());

        GRN grn = entityManager.persistAndFlush(GRN.builder()
                .invoiceNum("INV-001")
                .supplier(supplier)
                .status(GRNStatus.POSTED)
                .subTotalAmount(BigDecimal.valueOf(200))
                .totalDiscount(BigDecimal.ZERO)
                .grandTotalAmount(BigDecimal.valueOf(200))
                .build());

        // 2. Set up Batches
        Batch batch1 = buildBatch(grn, stock, "BATCH-001");
        Batch batch2 = buildBatch(grn, stock, "BATCH-002");

        // 3. Set up Prices and link to Batches to test the JOIN FETCH
        ProductPrice price1 = buildPrice(batch1, PriceType.RETAIL, "15.00");
        ProductPrice price2 = buildPrice(batch2, PriceType.RETAIL, "20.00");
        ProductPrice price3 = buildPrice(batch2, PriceType.WHOLESALE, "18.00");

        batch1.setPrices(List.of(price1));
        batch2.setPrices(List.of(price2, price3));

        // 4. Persist and store IDs
        Batch savedBatch1 = entityManager.persistAndFlush(batch1);
        Batch savedBatch2 = entityManager.persistAndFlush(batch2);

        this.savedBatchIds = Arrays.asList(savedBatch1.getId(), savedBatch2.getId());

        // Clear the persistence context so the tests actually fetch from DB, not L1 cache
        entityManager.clear();
    }

    @Nested
    @DisplayName("Tests for findAllByIdWithPrices")
    class FindAllByIdWithPricesTests {

        @Test
        @DisplayName("Should successfully retrieve Batches with their associated prices")
        void testFindAllByIdWithPrices_Success() {
            // Given & When
            List<Batch> results = batchRepository.findAllByIdWithPrices(savedBatchIds);

            // Then
            assertThat(results).hasSize(2);

            assertThat(results).extracting(Batch::getId)
                    .containsExactlyInAnyOrderElementsOf(savedBatchIds);

            // Find the specific Batch with 2 prices (BATCH-002) to verify its contents
            Batch batchWithTwoPrices = results.stream()
                    .filter(b -> b.getBatchNumber().equals("BATCH-002"))
                    .findFirst()
                    .orElseThrow();

            assertThat(batchWithTwoPrices.getPrices()).hasSize(2);
            assertThat(batchWithTwoPrices.getPrices())
                    .extracting(ProductPrice::getPriceType)
                    .containsExactlyInAnyOrder(PriceType.RETAIL, PriceType.WHOLESALE);
        }

        @Test
        @DisplayName("Should return empty list when given non-existent IDs")
        void testFindAllByIdWithPrices_WhenIdsDoNotExist() {
            // Given
            List<Long> nonExistentIds = Arrays.asList(999L, 1000L);

            // When
            List<Batch> results = batchRepository.findAllByIdWithPrices(nonExistentIds);

            // Then
            assertThat(results).isNotNull();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Tests for findByIdWithPrices")
    class FindByIdWithPricesTests {

        @Test
        @DisplayName("Should successfully retrieve a Batch with its associated prices")
        void testFindByIdWithPrices_Success() {
            // Given
            Long targetId = savedBatchIds.getFirst(); // This is BATCH-001

            // When
            Optional<Batch> result = batchRepository.findByIdWithPrices(targetId);

            // Then
            assertThat(result).isPresent();
            result.ifPresent(batch -> {
                assertThat(batch.getId()).isEqualTo(targetId);
                assertThat(batch.getBatchNumber()).isEqualTo("BATCH-001");

                // Verify Relationship loading
                assertThat(batch.getPrices()).hasSize(1);
                ProductPrice price = batch.getPrices().getFirst();
                assertThat(price.getPriceType()).isEqualTo(PriceType.RETAIL);
                assertThat(price.getPrice()).isEqualByComparingTo("15.00");

                // Note: If GRN/Stock are Lazy, calling them here might trigger extra queries,
                // but since they are ManyToOne they might be Eager by default depending on your model.
            });
        }

        @Test
        @DisplayName("Should return empty Optional when given non-existent ID")
        void testFindByIdWithPrices_WhenIdDoesNotExist() {
            // Given
            Long nonExistentId = 999L;

            // When
            Optional<Batch> result = batchRepository.findByIdWithPrices(nonExistentId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isNotPresent();
        }
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private Batch buildBatch(GRN grn, Stock stock, String batchNumber) {
        return Batch.builder()
                .grn(grn)
                .stock(stock)
                .batchNumber(batchNumber)
                .costPrice(BigDecimal.valueOf(10.00))
                .quantity(BigDecimal.valueOf(100))
                .status(BatchStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusMonths(6))
                .build();
    }

    private ProductPrice buildPrice(Batch batch, PriceType type, String priceAmount) {
        return ProductPrice.builder()
                .batch(batch)
                .priceType(type)
                .price(new BigDecimal(priceAmount))
                .minQuantity(1)
                .active(true)
                .build();
    }
}