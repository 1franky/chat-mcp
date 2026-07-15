package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.ModelDescriptor;
import java.util.List;

public interface ModelCatalogPort {

    List<ModelDescriptor> listModels();
}
