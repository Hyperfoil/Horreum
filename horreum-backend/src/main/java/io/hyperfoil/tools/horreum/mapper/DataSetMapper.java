package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.hyperfoil.tools.horreum.api.data.DataSet;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;

import java.util.stream.Collectors;

public class DataSetMapper {

    public static DataSet from(DataSetDAO ds) {

        DataSet dto = new DataSet();
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

        if(ds.validationErrors != null)
            dto.validationErrors = ds.validationErrors.stream().map(ValidationErrorMapper::fromValidationError).collect(Collectors.toList());

        return dto;
    }

    public static DataSetDAO to(DataSet dto, RunDAO run) {
        DataSetDAO ds = new DataSetDAO(run, dto.ordinal, dto.description, dto.data);
        ds.id = dto.id;
        return ds;
    }

    public static DataSet.Info fromInfo(DataSetDAO.Info info) {
        return new DataSet.Info(info.id, info.runId, info.ordinal, info.testId);
    }

    public static DataSetDAO.Info toInfo(DataSet.Info info) {
        return new DataSetDAO.Info(info.id, info.runId, info.ordinal, info.testId);
    }
}
