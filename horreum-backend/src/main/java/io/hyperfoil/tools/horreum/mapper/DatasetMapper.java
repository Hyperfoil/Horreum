package io.hyperfoil.tools.horreum.mapper;

import java.util.stream.Collectors;

import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;

public class DatasetMapper {

    public static Dataset from(DatasetDAO ds) {

        Dataset dto = new Dataset();
        dto.id = ds.id;
        dto.runId = ds.getRunId();
        dto.start = ds.start;
        dto.stop = ds.stop;
        dto.testid = ds.testid;
        dto.owner = ds.owner;
        dto.access = ds.access;
        dto.ordinal = ds.ordinal;
        dto.description = ds.description;
        dto.data = ds.data;

        if (ds.validationErrors != null)
            dto.validationErrors = ds.validationErrors.stream().map(ValidationErrorMapper::fromValidationError)
                    .collect(Collectors.toList());

        return dto;
    }

    public static DatasetDAO to(Dataset dto, RunDAO run) {
        DatasetDAO ds;
        if (run != null) {
            ds = new DatasetDAO(run, dto.ordinal, dto.description, dto.data);
        } else {
            ds = new DatasetDAO();
        }
        ds.id = dto.id;
        return ds;
    }

    public static Dataset.Info fromInfo(DatasetDAO.Info info) {
        return new Dataset.Info(info.id, info.runId, info.ordinal, info.testId);
    }

    public static DatasetDAO.Info toInfo(Dataset.Info info) {
        return new DatasetDAO.Info(info.id, info.runId, info.ordinal, info.testId);
    }
}
