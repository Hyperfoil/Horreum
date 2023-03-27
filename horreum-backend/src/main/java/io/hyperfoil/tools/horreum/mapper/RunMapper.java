package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.api.data.Run;

import java.util.stream.Collectors;

public class RunMapper {

    public static Run from(RunDAO run) {
        Run dto = new Run();
        dto.id = run.id;
        dto.start = run.start;
        dto.stop = run.stop;
        dto.description = run.description;
        dto.testid = run.testid;
        dto.data = run.data;
        dto.metadata = run.metadata;
        dto.trashed = run.trashed;
        if(run.validationErrors != null)
            dto.validationErrors = run.validationErrors.stream().map(ValidationErrorMapper::fromValidationError).collect(Collectors.toList());
        if(run.datasets != null)
            dto.datasets = run.datasets.stream().map(DataSetMapper::from).collect(Collectors.toList());
        dto.owner = run.owner;
        dto.access = run.access;

        return dto;
    }

    public static RunDAO to(Run dto) {
       RunDAO run = new RunDAO();
       run.id = dto.id;
       run.start = dto.start;
       run.stop = dto.stop;
       run.description = dto.description;
       run.testid = dto.testid;
       run.data = dto.data;
       run.metadata = dto.metadata;
       run.trashed = dto.trashed;
       run.validationErrors = dto.validationErrors.stream().map(ValidationErrorMapper::toValidationError).collect(Collectors.toList());
       if(dto.datasets != null)
           run.datasets = dto.datasets.stream().map( dsDTO -> DataSetMapper.to(dsDTO, run)).collect(Collectors.toList());

       return run;
    }


}
