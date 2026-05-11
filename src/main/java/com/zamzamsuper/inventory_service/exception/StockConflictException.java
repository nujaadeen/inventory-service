package com.zamzamsuper.inventory_service.exception;

public class StockConflictException extends RuntimeException {
    public StockConflictException(String sku) {
        super(
                "Stock conflict: Cannot revert quantity for SKU "
                        + sku
                        + ". Inventory has already been utilized.");
    }
}
