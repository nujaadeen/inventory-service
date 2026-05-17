package com.zamzamsuper.inventory_service.repository.specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.model.Batch;

import jakarta.persistence.criteria.Predicate;

public class BatchSpecification {

    public static Specification<Batch> withFilters(
            String productSku,
            String batchNumber,
            BatchStatus status,
            LocalDate expiryStartDate,
            LocalDate expiryEndDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Filter by Product SKU
            if (StringUtils.hasText(productSku)) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("stock").get("productSku")),
                                "%" + productSku.toLowerCase() + "%"));
            }

            // 2. Filter by Batch Number
            if (StringUtils.hasText(batchNumber)) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("batchNumber")),
                                "%" + batchNumber.toLowerCase() + "%"));
            }

            // 3. Filter by Status
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 4. Filter by Expiry Date Range
            if (expiryStartDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("expiryDate"), expiryStartDate));
            }

            if (expiryEndDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("expiryDate"), expiryEndDate));
            }

            // Use the query to order results if not specified by Pageable
            if (query != null) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}