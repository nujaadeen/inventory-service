package com.zamzamsuper.inventory_service.repository.specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Supplier;
import com.zamzamsuper.inventory_service.repository.GRNRepository;


@DataJpaTest
class GRNSpecificationTest {

    @Autowired 
    private TestEntityManager entityManager;

    @Autowired 
    private GRNRepository grnRepository;

    private Supplier supplier1;
    private Supplier supplier2;

    private GRN grnPosted1;   // supplier1, INV-001, POSTED,  3 days ago
    private GRN grnPosted2;    // supplier1, INV-002, POSTED,   1 day ago
    private GRN grnCancelled;// supplier2, INV-ABC, CANCELLED, today

    @BeforeEach
    void setUp() {
        supplier1 = entityManager.persist(
                Supplier.builder().name("Supplier One").phone("111").build());
        supplier2 = entityManager.persist(
                Supplier.builder().name("Supplier Two").phone("222").build());

        grnPosted1 = persistGRN("INV-001", supplier1, GRNStatus.POSTED,
                LocalDateTime.now().minusDays(3));
        grnPosted2 = persistGRN("INV-002", supplier1, GRNStatus.POSTED,
                LocalDateTime.now().minusDays(1));
        grnCancelled = persistGRN("INV-ABC", supplier2, GRNStatus.CANCELLED,
                LocalDateTime.now());

        entityManager.flush();
    }

    @Nested
    @DisplayName("Tests for withFilters specification")
    class withFiltersTests {

        @Nested
        @DisplayName("No filters")
        class NoFilters {

            @Test
            @DisplayName("Returns all GRNs ordered by createdAt descending")
            void givenNoFilters_whenQuery_thenAllReturnedNewestFirst() {
                List<GRN> result = find(null, null, null, null, null);

                assertThat(result).hasSize(3);
                // newest first
                assertThat(result.get(0).getId()).isEqualTo(grnCancelled.getId());
                assertThat(result.get(1).getId()).isEqualTo(grnPosted2.getId());
                assertThat(result.get(2).getId()).isEqualTo(grnPosted1.getId());
            }
        }

        @Nested
        @DisplayName("Invoice number filter")
        class InvoiceNumFilter {

            @Test
            @DisplayName("Matches by exact invoice number")
            void givenExactInvoiceNum_whenQuery_thenOnlyThatGRNReturned() {
                List<GRN> result = find("INV-001", null, null, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getInvoiceNum)
                        .containsExactly("INV-001");
            }

            @Test
            @DisplayName("Matches by partial invoice number (substring)")
            void givenPartialInvoiceNum_whenQuery_thenMatchingGRNsReturned() {
                List<GRN> result = find("INV", null, null, null, null);

                assertThat(result).hasSize(3); // all three contain "INV"
            }

            @Test
            @DisplayName("Match is case-insensitive")
            void givenLowercaseInvoiceNum_whenQuery_thenMatchFound() {
                List<GRN> result = find("inv-001", null, null, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getInvoiceNum)
                        .containsExactly("INV-001");
            }

            @Test
            @DisplayName("Returns nothing when invoice number does not match")
            void givenNonMatchingInvoiceNum_whenQuery_thenEmptyResult() {
                List<GRN> result = find("NOMATCH", null, null, null, null);

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("Ignores blank invoice number (treated as no filter)")
            void givenBlankInvoiceNum_whenQuery_thenAllReturned() {
                assertThat(find("",  null, null, null, null)).hasSize(3);
                assertThat(find("  ", null, null, null, null)).hasSize(3);
            }
        }

        @Nested
        @DisplayName("Supplier ID filter")
        class SupplierIdFilter {

            @Test
            @DisplayName("Returns only GRNs belonging to the given supplier")
            void givenSupplierId_whenQuery_thenOnlyThatSuppliersGRNsReturned() {
                List<GRN> result = find(null, supplier1.getId(), null, null, null);

                assertThat(result)
                        .hasSize(2)
                        .allMatch(g -> g.getSupplier().getId().equals(supplier1.getId()));
            }

            @Test
            @DisplayName("Returns nothing when supplier has no GRNs")
            void givenUnknownSupplierId_whenQuery_thenEmptyResult() {
                List<GRN> result = find(null, 999L, null, null, null);

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Status filter")
        class StatusFilter {

            @Test
            @DisplayName("Returns only GRNs with the given status")
            void givenStatus_whenQuery_thenOnlyMatchingStatusReturned() {
                List<GRN> result = find(null, null, GRNStatus.CANCELLED, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getStatus)
                        .containsExactly(GRNStatus.CANCELLED);
            }

            @Test
            @DisplayName("Returns nothing when no GRN has the given status")
            void givenStatusWithNoMatch_whenQuery_thenEmptyResult() {
                // No GRN seeded with PENDING — adjust if your enum differs
                List<GRN> result = find(null, null, GRNStatus.DRAFT, null, null);

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Date range filter")
        class DateRangeFilter {

            @Test
            @DisplayName("startDate includes GRNs created on or after that day")
            void givenStartDate_whenQuery_thenOlderGRNsExcluded() {
                // startDate = yesterday → excludes grnPosted (3 days ago)
                LocalDate yesterday = LocalDate.now().minusDays(1);
                List<GRN> result = find(null, null, null, yesterday, null);

                assertThat(result)
                        .hasSize(2)
                        .extracting(GRN::getId)
                        .containsExactlyInAnyOrder(grnPosted2.getId(), grnCancelled.getId());
            }

            @Test
            @DisplayName("endDate includes GRNs created up to and including end of that day")
            void givenEndDate_whenQuery_thenNewerGRNsExcluded() {
                // endDate = 2 days ago → only grnPosted1 (3 days ago) qualifies
                LocalDate twoDaysAgo = LocalDate.now().minusDays(2);
                List<GRN> result = find(null, null, null, null, twoDaysAgo);

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getId)
                        .containsExactly(grnPosted1.getId());
            }

            @Test
            @DisplayName("Closed date range returns only GRNs within that window")
            void givenStartAndEndDate_whenQuery_thenOnlyGRNsInWindowReturned() {
                // window: 4 days ago → 2 days ago → only grnPosted1 (3 days ago) qualifies
                LocalDate start = LocalDate.now().minusDays(4);
                LocalDate end   = LocalDate.now().minusDays(2);
                List<GRN> result = find(null, null, null, start, end);

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getId)
                        .containsExactly(grnPosted1.getId());
            }

            @Test
            @DisplayName("Returns nothing when date range excludes all GRNs")
            void givenDateRangeWithNoMatch_whenQuery_thenEmptyResult() {
                LocalDate farFuture = LocalDate.now().plusYears(1);
                List<GRN> result = find(null, null, null, farFuture, null);

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Combined filters")
        class CombinedFilters {

            @Test
            @DisplayName("Supplier + status narrows to exact match")
            void givenSupplierAndStatus_whenQuery_thenOnlyMatchReturned() {
                List<GRN> result = find(null, supplier2.getId(), GRNStatus.CANCELLED, null, null);

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getId)
                        .containsExactly(grnCancelled.getId());
            }

            @Test
            @DisplayName("Invoice partial + supplier returns only intersection")
            void givenInvoicePartialAndSupplier_whenQuery_thenIntersectionReturned() {
                // "INV-0" matches INV-001 and INV-002; supplier1 owns both → both returned
                List<GRN> result = find("INV-0", supplier1.getId(), null, null, null);

                assertThat(result).hasSize(2);
            }

            @Test
            @DisplayName("All filters combined return single precise match")
            void givenAllFilters_whenQuery_thenSinglePreciseMatchReturned() {
                List<GRN> result = find(
                        "INV-001",
                        supplier1.getId(),
                        GRNStatus.POSTED,
                        LocalDate.now().minusDays(4),
                        LocalDate.now().minusDays(2));

                assertThat(result)
                        .hasSize(1)
                        .extracting(GRN::getId)
                        .containsExactly(grnPosted1.getId());
            }

            @Test
            @DisplayName("Returns nothing when filter combination matches no GRN")
            void givenConflictingFilters_whenQuery_thenEmptyResult() {
                // supplier2 has no POSTED GRNs
                List<GRN> result = find(null, supplier2.getId(), GRNStatus.POSTED, null, null);

                assertThat(result).isEmpty();
            }
        }
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private List<GRN> find(
            String invoiceNum,
            Long supplierId,
            GRNStatus status,
            LocalDate startDate,
            LocalDate endDate) {
        Specification<GRN> spec =
                GRNSpecification.withFilters(invoiceNum, supplierId, status, startDate, endDate);
        return grnRepository.findAll(spec);
    }

    private GRN persistGRN(String invoiceNum, Supplier supplier, GRNStatus status, LocalDateTime createdAt) {
        GRN grn = GRN.builder()
                .invoiceNum(invoiceNum)
                .supplier(supplier)
                .status(status)
                .subTotalAmount(BigDecimal.valueOf(100))
                .totalDiscount(BigDecimal.ZERO)
                .grandTotalAmount(BigDecimal.valueOf(100))
                .build();

        // 1. Save the entity normally (JPA will set createdAt to NOW)
        grn = entityManager.persistAndFlush(grn);

        // 2. Forcefully update the date using JPQL to bypass @CreationTimestamp
        entityManager.getEntityManager()
                .createQuery("UPDATE GRN g SET g.createdAt = :date WHERE g.id = :id")
                .setParameter("date", createdAt)
                .setParameter("id", grn.getId())
                .executeUpdate();

        // 3. Refresh the entity so the test object has the correct historical date
        entityManager.refresh(grn);
        
        return grn;
    }
}