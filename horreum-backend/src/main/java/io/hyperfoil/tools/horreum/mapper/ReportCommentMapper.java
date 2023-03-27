package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.report.ReportCommentDAO;
import io.hyperfoil.tools.horreum.api.report.ReportComment;

public class ReportCommentMapper {
    public static ReportComment from(ReportCommentDAO rc) {
        ReportComment dto = new ReportComment();
        dto.id = rc.id;
        dto.comment = rc.comment;
        dto.category = rc.category;
        dto.componentId = rc.componentId;
        dto.level = rc.level;

        return dto;
    }

    public static ReportCommentDAO to(ReportComment dto) {
        ReportCommentDAO rc = new ReportCommentDAO();
        rc.id = dto.id;
        rc.comment = dto.comment;
        rc.category = dto.category;
        rc.componentId = dto.componentId;
        rc.level = dto.level;

        return rc;
    }
}
