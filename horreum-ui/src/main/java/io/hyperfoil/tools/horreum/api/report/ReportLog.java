package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.PersistentLog;

public class ReportLog extends PersistentLog {
    private int reportId;

    public ReportLog() {
        super(0, (String)null);
    }

    public ReportLog(int reportId, int level, String message) {
        super(level, message);
        this.reportId = reportId;
    }

    @JsonProperty(required = true, value = "reportId")
    public int getReportId() {
        return this.reportId;
    }

}
