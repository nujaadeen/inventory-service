package com.zamzamsuper.inventory_service.repository.specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.repository.BatchRepository;

@DataJpaTest
class BatchSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BatchRepository batchRepository;

    private Batch batchActive1;   // SKU-100, BATCH-001, ACTIVE,    expires +10 days, created 3 days ago
    private Batch batchActive2;   // SKU-100, BATCH-002, ACTIVE,    expires +20 days, created 1 day ago
    private Batch batchCancelled; // SKU-ABC, BATCH-XYZ, CANCELLED, expires -5 days,  created today

    @BeforeEach
    void setUp() {
        // Create a dummy location to satisfy Stock constraints
        Location location = entityManager.persist(
                Location.builder().name("Main Warehouse").build());

        Stock stock1 = entityManager.persist(
                Stock.builder().productSku("SKU-100").location(location).quantityOnHand(BigDecimal.ZERO).build());
        Stock stock2 = entityManager.persist(
                Stock.builder().productSku("SKU-ABC").location(location).quantityOnHand(BigDecimal.ZERO).build());

        Supplier supplier = entityManager.persist(
                Supplier.builder().name("Dummy Supplier").phone("0000").build());

        GRN grn = entityManager.persist(
                GRN.builder()
                        .invoiceNum("INV-DUMMY")
                        .supplier(supplier)
                        .status(GRNStatus.POSTED)
                        .subTotalAmount(BigDecimal.valueOf(100))
                        .totalDiscount(BigDecimal.ZERO)
                        .grandTotalAmount(BigDecimal.valueOf(100))
                        .build());

        batchActive1 = persistBatch("BATCH-001", stock1, grn, BatchStatus.ACTIVE,
                LocalDate.now().plusDays(10), LocalDateTime.now().minusDays(3));

        batchActive2 = persistBatch("BATCH-002", stock1, grn, BatchStatus.ACTIVE,
                LocalDate.now().plusDays(20), LocalDateTime.now().minusDays(1));

        batchCancelled = persistBatch("BATCH-XYZ", stock2, grn, BatchStatus.CANCELLED,
                LocalDate.now().minusDays(5), LocalDateTime.now());

        entityManager.flush();
    }

    @Nested
    @DisplayName("Tests for withFilters specification")
    class withFiltersTests {

        @Nested
        @DisplayName("No filters")
        class NoFilters {

            @Test
            @DisplayName("Returns all Batches ordered by createdAt descending")
            void givenNoFilters_whenQuery_thenAllReturnedNewestFirst() {
                List<Batch> result = find(null, null, null, null, null);

                assertThat(result).hasSize(3);
                // newest first
                assertThat(result.get(0).getId()).isEqualTo(batchCancelled.getId());
                assertThat(result.get(1).getId()).isEqualTo(batchActive2.getId());
                assertThat(result.get(2).getId()).isEqualTo(batchActive1.getId());
            }
        }

        @Nested
        @DisplayName("Product SKU filter")
        class ProductSkuFilter {

            @Test
            @DisplayName("Matches by exact SKU")
            void givenExactSku_whenQuery_thenOnlyMatchingBatchesReturned() {
                List<Batch> result = find("SKU-100", null, null, null, null);

                assertThat(result)
                        .hasSize(2)
                        .allMatch(b -> b.getStock().getProductSku().equals("SKU-100"));
            }

            @Test
            @DisplayName("Matches by partial SKU (substring)")
            void givenPartialSku_whenQuery_thenMatchingBatchesReturned() {
                List<Batch> result = find("SKU", null, null, null, null);

                assertThat(result).hasSize(3); // All three contain "SKU"
            }

            @Test
            @DisplayName("Match is case-insensitive")
            void givenLowercaseSku_whenQuery_thenMatchFound() {
                List<Batch> result = find("sku-100", null, null, null, null);

                assertThat(result).hasSize(2);
            }

            @Test
            @DisplayName("Returns nothing when SKU does not match")
            void givenNonMatchingSku_whenQuery_thenEmptyResult() {
                List<Batch> result = find("NOMATCH", null, null, null, null);

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Batch number filter")
        class BatchNumberFilter {

            @Test
            @DisplayName("Matches by exact batch number")
            void givenExactBatchNum_whenQuery_thenOnlyThatBatchReturned() {
                List<Batch> result = find(null, "BATCH-001", null, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(Batch::getBatchNumber)
                        .containsExactly("BATCH-001");
            }

            @Test
            @DisplayName("Matches by partial batch number (substring)")
            void givenPartialBatchNum_whenQuery_thenMatchingBatchesReturned() {
                List<Batch> result = find(null, "BATCH", null, null, null);

                assertThat(result).hasSize(3); // All three contain "BATCH"
            }

            @Test
            @DisplayName("Match is case-insensitive")
            void givenLowercaseBatchNum_whenQuery_thenMatchFound() {
                List<Batch> result = find(null, "batch-001", null, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(Batch::getBatchNumber)
                        .containsExactly("BATCH-001");
            }

            @Test
            @DisplayName("Ignores blank batch number (treated as no filter)")
            void givenBlankBatchNum_whenQuery_thenAllReturned() {
                assertThat(find(null, "",  null, null, null)).hasSize(3);
                assertThat(find(null, "  ", null, null, null)).hasSize(3);
            }
        }

        @Nested
        @DisplayName("Status filter")
        class StatusFilter {

            @Test
            @DisplayName("Returns only Batches with the given status")
            void givenStatus_whenQuery_thenOnlyMatchingStatusReturned() {
                List<Batch> result = find(null, null, BatchStatus.CANCELLED, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(Batch::getStatus)
                        .containsExactly(BatchStatus.CANCELLED);
            }

            @Test
            @DisplayName("Returns active batches")
            void givenActiveStatus_whenQuery_thenActiveBatchesReturned() {
                List<Batch> result = find(null, null, BatchStatus.ACTIVE, null, null);

                assertThat(result).hasSize(2);
            }
        }

        @Nested
        @DisplayName("Expiry Date range filter")
        class ExpiryDateRangeFilter {

            @Test
            @DisplayName("startDate includes Batches expiring on or after that day")
            void givenStartDate_whenQuery_thenOlderBatchesExcluded() {
                // startDate = today → excludes batchCancelled (expired 5 days ago)
                LocalDate today = LocalDate.now();
                List<Batch> result = find(null, null, null, today, null);

                assertThat(result)
                        .hasSize(2)
                        .extracting(Batch::getId)
                        .containsExactlyInAnyOrder(batchActive1.getId(), batchActive2.getId());
            }

            @Test
            @DisplayName("endDate includes Batches expiring up to and including that day")
            void givenEndDate_whenQuery_thenNewerBatchesExcluded() {
                // endDate = today + 15 days → only batchActive1 (+10) and batchCancelled (-5) qualify
                LocalDate maxExpiry = LocalDate.now().plusDays(15);
                List<Batch> result = find(null, null, null, null, maxExpiry);

                assertThat(result)
                        .hasSize(2)
                        .extracting(Batch::getId)
                        .containsExactlyInAnyOrder(batchCancelled.getId(), batchActive1.getId());
            }

            @Test
            @DisplayName("Closed date range returns only Batches within that window")
            void givenStartAndEndDate_whenQuery_thenOnlyBatchesInWindowReturned() {
                // window: today + 5 days to today + 15 days → only batchActive1 (+10 days) qualifies
                LocalDate start = LocalDate.now().plusDays(5);
                LocalDate end   = LocalDate.now().plusDays(15);
                List<Batch> result = find(null, null, null, start, end);

                assertThat(result)
                        .hasSize(1)
                        .extracting(Batch::getId)
                        .containsExactly(batchActive1.getId());
            }

            @Test
            @DisplayName("Returns nothing when date range excludes all Batches")
            void givenDateRangeWithNoMatch_whenQuery_thenEmptyResult() {
                LocalDate farFuture = LocalDate.now().plusYears(1);
                List<Batch> result = find(null, null, null, farFuture, null);

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Combined filters")
        class CombinedFilters {

            @Test
            @DisplayName("SKU + status narrows to exact match")
            void givenSkuAndStatus_whenQuery_thenOnlyMatchReturned() {
                List<Batch> result = find("SKU-ABC", null, BatchStatus.CANCELLED, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(Batch::getId)
                        .containsExactly(batchCancelled.getId());
            }

            @Test
            @DisplayName("Batch partial + SKU returns only intersection")
            void givenBatchPartialAndSku_whenQuery_thenIntersectionReturned() {
                // "BATCH-00" matches BATCH-001 and BATCH-002; stock1 (SKU-100) owns both
                List<Batch> result = find("SKU-100", "BATCH-00", null, null, null);

                assertThat(result).hasSize(2);
            }

            @Test
            @DisplayName("All filters combined return single precise match")
            void givenAllFilters_whenQuery_thenSinglePreciseMatchReturned() {
                List<Batch> result = find(
                        "SKU-100",
                        "BATCH-001",
                        BatchStatus.ACTIVE,
                        LocalDate.now().plusDays(5),
                        LocalDate.now().plusDays(15));

                assertThat(result)
                        .hasSize(1)
                        .extracting(Batch::getId)
                        .containsExactly(batchActive1.getId());
            }

            @Test
            @DisplayName("Returns nothing when filter combination matches no Batch")
            void givenConflictingFilters_whenQuery_thenEmptyResult() {
                // stock2 (SKU-ABC) has no ACTIVE batches
                List<Batch> result = find("SKU-ABC", null, BatchStatus.ACTIVE, null, null);

                assertThat(result).isEmpty();
            }
        }
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private List<Batch> find(
            String productSku,
            String batchNumber,
            BatchStatus status,
            LocalDate expiryStartDate,
            LocalDate expiryEndDate) {
        Specification<Batch> spec =
                BatchSpecification.withFilters(productSku, batchNumber, status, expiryStartDate, expiryEndDate);
        return batchRepository.findAll(spec);
    }

    private Batch persistBatch(String batchNumber, Stock stock, GRN grn,  BatchStatus status, LocalDate expiryDate, LocalDateTime createdAt) {
        Batch batch = Batch.builder()
                .batchNumber(batchNumber)
                .stock(stock)
                .grn(grn)
                .status(status)
                .expiryDate(expiryDate)
                .quantity(BigDecimal.valueOf(100))
                .costPrice(BigDecimal.valueOf(10))
                .build();

        // 1. Save the entity normally (JPA will set createdAt to NOW)
        batch = entityManager.persistAndFlush(batch);

        // 2. Forcefully update the date using JPQL to bypass @CreationTimestamp
        entityManager.getEntityManager()
                .createQuery("UPDATE Batch b SET b.createdAt = :date WHERE b.id = :id")
                .setParameter("date", createdAt)
                .setParameter("id", batch.getId())
                .executeUpdate();

        // 3. Refresh the entity so the test object has the correct historical date
        entityManager.refresh(batch);

        return batch;
    }
}