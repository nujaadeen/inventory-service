package com.zamzamsuper.inventory_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.ProductPriceResponse;
import com.zamzamsuper.inventory_service.mapper.ProductPriceMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.repository.BatchRepository;
import com.zamzamsuper.inventory_service.repository.ProductPriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductPriceService {

    private final ProductPriceMapper priceMapper;
    private final ProductPriceRepository productPriceRepository;
    private final BatchRepository batchRepository;

    public ProductPriceResponse createPrice(ProductPriceRequest request) {
        ProductPrice productPrice = priceMapper.toEntity(request);
        ProductPrice savedProductPrice = productPriceRepository.save(productPrice);
        return priceMapper.toResponse(savedProductPrice);
    }

    public List<ProductPriceResponse> getAllPrices() {
        return productPriceRepository.findAll()
                .stream()
                .map(priceMapper::toResponse)
                .collect(Collectors.toList());
    }

    public ProductPriceResponse getPriceById(Long id) {
        ProductPrice price = productPriceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Price not found with id " + id));
        return priceMapper.toResponse(price);
    }

    public ProductPriceResponse updatePrice(Long id, ProductPriceRequest request) {
        ProductPrice existingPrice = productPriceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Price not found with id " + id));

        Batch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        existingPrice.setBatch(batch);
        existingPrice.setPriceType(request.priceType());
        existingPrice.setActive(request.active());
        existingPrice.setMinQuantity(request.minQuantity());
        existingPrice.setPrice(request.price());
        existingPrice.setMinPrice(request.minPrice());

        ProductPrice updated = productPriceRepository.save(existingPrice);
        return priceMapper.toResponse(updated);
    }

    public void deletePrice(Long id) {
        if (!productPriceRepository.existsById(id)) {
            throw new RuntimeException("Price not found with id " + id);
        }
        productPriceRepository.deleteById(id);
    }
}