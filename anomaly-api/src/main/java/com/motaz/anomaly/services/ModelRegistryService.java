package com.motaz.anomaly.services;

import com.motaz.anomaly.model.entities.ModelRegistryEntity;
import com.motaz.anomaly.repositories.ModelRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRegistryService {

    private final ModelRegistryRepository modelRegistryRepository;

    public IsolationForest loadLatestModel() throws IOException, ClassNotFoundException {
        IsolationForest iForest = null ;
        Optional<ModelRegistryEntity> optionalModelRegistryEntity = modelRegistryRepository.findLatestIFModel();
        if (optionalModelRegistryEntity.isPresent()) {
            ModelRegistryEntity modelRegistryEntity = optionalModelRegistryEntity.get();
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(modelRegistryEntity.getModelBytes()));
            iForest  = (IsolationForest) ois.readObject();
            ois.close();
        }
        return iForest;
    }
}
