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
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Location;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.model.Supplier;

@DataJpaTest
public class GRNRepositoryTest {

    @Autowired
    private GRNRepository grnRepository;

    @Autowired
    private TestEntityManager entityManager;

    private List<Long> savedGrnIds;

    @BeforeEach
    void setUp() {
        // Create and Persist Location
        Location location = entityManager.persistAndFlush(Location.builder().name("Test Location").active(true).build());
        
        // Create and Persist Supplier
        Supplier supplier = entityManager.persistAndFlush(Supplier.builder().name("Test Supplier").build());

        // Create and Persist Stock
        Stock stock = entityManager.persistAndFlush(Stock.builder()
                .productSku("SKU-001")
                .location(location)
                .quantityOnHand(BigDecimal.valueOf(100))
                .reorderLevel(BigDecimal.valueOf(10))
                .build());

        // Set up a GRN with Batches and Prices
        GRN grn1 = buildGRN("INV-001", supplier, GRNStatus.POSTED);
        GRN grn2 = buildGRN("INV-002", supplier, GRNStatus.DRAFT);

        Batch batch1 = buildBatch(grn1, stock, "BATCH-001", BatchStatus.ACTIVE);
        Batch batch2 = buildBatch(grn2, stock, "BATCH-002", BatchStatus.EXPIRED);
        Batch batch3 = buildBatch(grn2, stock, "BATCH-003", BatchStatus.ACTIVE);

        grn1.setBatches(List.of(batch1));
        grn2.setBatches(List.of(batch2, batch3));

        GRN savedGrn = entityManager.persistAndFlush(grn1);
        GRN savedGrn2 = entityManager.persistAndFlush(grn2);
        this.savedGrnIds = Arrays.asList(savedGrn.getId(), savedGrn2.getId());
    }

    @Nested
    @DisplayName("Tests for findAllByIdWithBatches")
    class FindAllByIdWithBatchesTests {
        @Test
        @DisplayName("Should successfully retrieve GRNs with their associated batches")
        void testFindAllByIdWithBatches_Success() {
            // Given & When
            List<GRN> results = grnRepository.findAllByIdWithBatches(savedGrnIds);

            // Then
            assertThat(results).hasSize(2);
            
            // Use extracting to verify IDs without caring about list order (unless specified)
            assertThat(results).extracting(GRN::getId)
                    .containsExactlyInAnyOrderElementsOf(savedGrnIds);

            // Find the specific GRN with 2 batches (INV-002) to verify its contents
            GRN grnWithTwoBatches = results.stream()
                    .filter(g -> g.getInvoiceNum().equals("INV-002"))
                    .findFirst()
                    .orElseThrow();

            assertThat(grnWithTwoBatches.getBatches()).hasSize(2);
            assertThat(grnWithTwoBatches.getBatches())
                    .extracting(Batch::getBatchNumber)
                    .containsExactlyInAnyOrder("BATCH-002", "BATCH-003");
        }

        @Test
        @DisplayName("Should return empty list when given non-existent IDs")
        void testFindAllByIdWithBatches_WhenIdsDoNotExist() {
            // Given
            List<Long> nonExistentIds = Arrays.asList(999L, 1000L);

            // When
            List<GRN> results = grnRepository.findAllByIdWithBatches(nonExistentIds);

            // Then
            assertThat(results).isNotNull();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Tests for findByIdWithBatches")
    class FindByIdWithBatchesTests {
        @Test
        @DisplayName("Should successfully retrieve a GRN with its associated batches")
        void testFindByIdWithBatches_Success() {
            // Given
            Long targetId = savedGrnIds.getFirst(); // This is INV-001

            // When
            Optional<GRN> result = grnRepository.findByIdWithBatches(targetId);

            // Then
            assertThat(result).isPresent();
            result.ifPresent(grn -> {
                assertThat(grn.getId()).isEqualTo(targetId);
                assertThat(grn.getInvoiceNum()).isEqualTo("INV-001");
                
                // Verify Relationship loading
                assertThat(grn.getBatches()).hasSize(1);
                Batch batch = grn.getBatches().getFirst();
                assertThat(batch.getBatchNumber()).isEqualTo("BATCH-001");
                assertThat(batch.getCostPrice()).isEqualByComparingTo("10.00");
                
                // Verify specific nested data to ensure the FETCH worked correctly
                assertThat(batch.getStock().getProductSku()).isEqualTo("SKU-001");
            });
        }

        @Test
        @DisplayName("Should return empty Optional when given non-existent ID")
        void testFindByIdWithBatches_WhenIdDoesNotExist() {
            // Given
            Long nonExistentId = 999L;

            // When
            Optional<GRN> result = grnRepository.findByIdWithBatches(nonExistentId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isNotPresent();
        }
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private GRN buildGRN(String invoiceNum, Supplier supplier, GRNStatus status) {
        return GRN.builder()
            .invoiceNum(invoiceNum)
            .supplier(supplier)
            .status(status)
            .subTotalAmount(BigDecimal.valueOf(100))
            .totalDiscount(BigDecimal.ZERO)
            .grandTotalAmount(BigDecimal.valueOf(100))
            .build();
    }

    private Batch buildBatch(GRN grn, Stock stock, String batchNumber, BatchStatus status) {
        return Batch.builder()
            .grn(grn)
            .stock(stock)
            .batchNumber(batchNumber)
            .costPrice(BigDecimal.valueOf(10.00))
            .quantity(BigDecimal.valueOf(100))
            .status(status)
            .expiryDate(LocalDate.now().plusMonths(6))
            .build();        
    }
}