package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.report.TableReportConfigDAO;
import io.hyperfoil.tools.horreum.entity.report.TableReportDAO;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.api.report.TableReport;

import java.util.stream.Collectors;

public class TableReportMapper {
    public static TableReport from(TableReportDAO tr) {
       TableReport dto = new TableReport();
       dto.id = tr.id;
       dto.config = fromTableReportConfig(tr.config);
       dto.created = tr.created;
       if (tr.comments != null)
           dto.comments = tr.comments.stream().map(ReportCommentMapper::from).collect(Collectors.toList());
       if(tr.data != null)
           dto.data = tr.data.stream().map(TableReportMapper::fromData).collect(Collectors.toList());
       if(tr.logs != null)
           dto.logs = tr.logs.stream().map(ReportLogMapper::from).collect(Collectors.toList());

       return dto;
    }

    public static TableReportConfig fromTableReportConfig(TableReportConfigDAO trc) {
        TableReportConfig dto = new TableReportConfig();
        dto.id = trc.id;
        dto.title = trc.title;
        dto.test = TestMapper.from(trc.test);
        dto.filterLabels = trc.filterLabels;
        dto.filterFunction = trc.filterFunction;
        dto.categoryLabels = trc.categoryLabels;
        dto.filterFunction = trc.categoryFunction;
        dto.categoryFormatter = trc.categoryFormatter;
        dto.seriesLabels = trc.seriesLabels;
        dto.seriesFunction = trc.seriesFunction;
        dto.seriesFormatter = trc.scaleFormatter;
        dto.scaleLabels = trc.scaleLabels;
        dto.scaleFunction = trc.scaleFunction;
        dto.scaleFormatter = trc.scaleFormatter;
        dto.scaleDescription = trc.scaleDescription;
        if (trc.components != null)
            dto.components = trc.components.stream().filter(r -> r.report != null).map(ReportComponentMapper::from).collect(Collectors.toList());

        return dto;
    }

    public static TableReport.DataDTO fromData(TableReportDAO.Data trd) {
        TableReport.DataDTO dto = new TableReport.DataDTO();
        dto.datasetId = trd.datasetId;
        dto.runId = trd.runId;
        dto.ordinal = trd.ordinal;
        dto.category = trd.category;
        dto.series = trd.series;
        dto.scale = trd.scale;
        dto.values = trd.values;

        return dto;
    }

    public static TableReportConfigDAO toTableReportConfig(TableReportConfig dto) {
        TableReportConfigDAO trc = new TableReportConfigDAO();
        trc.id = dto.id;
        trc.title = dto.title;
        trc.test = TestMapper.to(dto.test);
        trc.filterLabels = dto.filterLabels;
        trc.filterFunction = dto.filterFunction;
        trc.categoryLabels = dto.categoryLabels;
        trc.categoryFunction = dto.categoryFunction;
        trc.categoryFormatter = dto.categoryFormatter;
        trc.seriesLabels = dto.seriesLabels;
        trc.seriesFunction = dto.seriesFunction;
        trc.seriesFormatter = dto.scaleFormatter;
        trc.scaleLabels = dto.scaleLabels;
        trc.scaleFunction = dto.scaleFunction;
        trc.scaleFormatter = dto.scaleFormatter;
        trc.scaleDescription = dto.scaleDescription;
        if (dto.components != null)
            trc.components = dto.components.stream().map(ReportComponentMapper::to).collect(Collectors.toList());

        return trc;
    }
}
