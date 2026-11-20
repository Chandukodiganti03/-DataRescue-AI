package com.datarescue.service;

import com.datarescue.entity.Dataset;
import com.datarescue.repository.DatasetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;

    public DatasetService(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    public List<Dataset> getAllDatasets() {
        return datasetRepository.findAll();
    }

    public Dataset getDatasetById(Long id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dataset not found with id: " + id));
    }

    public Dataset saveDataset(Dataset dataset) {
        return datasetRepository.save(dataset);
    }

    public void deleteDataset(Long id) {
        datasetRepository.deleteById(id);
    }
}