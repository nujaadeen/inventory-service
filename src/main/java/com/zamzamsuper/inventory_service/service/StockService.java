package com.zamzamsuper.inventory_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zamzamsuper.inventory_service.dto.StockRequest;
import com.zamzamsuper.inventory_service.dto.StockResponse;
import com.zamzamsuper.inventory_service.mapper.StockMapper;
import com.zamzamsuper.inventory_service.model.Location;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.repository.LocationRepository;
import com.zamzamsuper.inventory_service.repository.StockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final LocationRepository locationRepository;

    public StockResponse createStock(StockRequest request) {
        Stock stock = stockMapper.toEntity(request);
        Stock savedStock = stockRepository.save(stock);
        return stockMapper.toResponse(savedStock);
    }

    public List<StockResponse> getAllStocks() {
        return stockRepository.findAll().stream()
                .map(stockMapper::toResponse)
                .collect(Collectors.toList());
    }

    public StockResponse getStockById(Long id) {
        Stock stock =
                stockRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new RuntimeException("Stock record not found with id " + id));
        return stockMapper.toResponse(stock);
    }

    public StockResponse updateStock(Long id, StockRequest request) {
        Stock existingStock =
                stockRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new RuntimeException("Stock record not found with id " + id));

        Location location =
                locationRepository
                        .findById(request.locationId())
                        .orElseThrow(() -> new RuntimeException("Location not found"));

        existingStock.setProductSku(request.productSku());
        existingStock.setLocation(location);
        existingStock.setQuantityOnHand(request.quantityOnHand());
        existingStock.setReorderLevel(request.reorderLevel());

        Stock updated = stockRepository.save(existingStock);
        return stockMapper.toResponse(updated);
    }

    public void deleteStock(Long id) {
        if (!stockRepository.existsById(id)) {
            throw new RuntimeException("Stock record not found with id " + id);
        }
        stockRepository.deleteById(id);
    }
}
