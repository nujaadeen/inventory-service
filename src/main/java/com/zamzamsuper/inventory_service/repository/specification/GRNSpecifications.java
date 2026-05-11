package com.zamzamsuper.inventory_service.repository.specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.model.GRN;

import jakarta.persistence.criteria.Predicate;

public class GRNSpecifications {

    public static Specification<GRN> withFilters(
            String invoiceNum,
            Long supplierId,
            GRNStatus status,
            LocalDate startDate,
            LocalDate endDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Filter by Invoice Number (Case-insensitive partial match)
            if (StringUtils.hasText(invoiceNum)) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("invoiceNum")),
                                "%" + invoiceNum.toLowerCase() + "%"));
            }

            // 2. Filter by Supplier ID
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }

            // 3. Filter by Status
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 4. Filter by Date Range (Created At)
            if (startDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }

            if (endDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            }

            // Use the query to order results if not specified by Pageable
            query.orderBy(cb.desc(root.get("createdAt")));

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
