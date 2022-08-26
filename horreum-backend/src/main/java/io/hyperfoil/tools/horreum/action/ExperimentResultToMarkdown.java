package io.hyperfoil.tools.horreum.action;

import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.ExperimentService;
import io.hyperfoil.tools.horreum.entity.json.DataSet;

@ApplicationScoped
public class ExperimentResultToMarkdown implements BodyFormatter {
   @ConfigProperty(name = "horreum.url")
   String publicUrl;

   @Override
   public String name() {
      return "experimentResultToMarkdown";
   }

   @Override
   public String format(JsonNode config, Object payload) {
      if (!(payload instanceof ExperimentService.ExperimentResult)) {
         throw new IllegalArgumentException("This formatter accepts only ExperimentResults");
      }
      ExperimentService.ExperimentResult result = (ExperimentService.ExperimentResult) payload;
      StringBuilder sb = new StringBuilder();
      sb.append("## Experiment results for test ").append(result.profile.test.name)
            .append(", profile ").append(result.profile.name).append("\n\n");
      sb.append("Dataset: ").append(datasetLink(result.datasetInfo)).append("\n");
      sb.append("Baseline: ");
      sb.append(result.baseline.stream().limit(16).map(this::datasetLink).collect(Collectors.joining(", ")));
      if (result.baseline.size() > 16) {
         sb.append(", ... (total ").append(result.baseline.size()).append(" datasets)");
      }
      sb.append("\n");
      sb.append("| Variable | Experiment value | Baseline value | Result |\n");
      sb.append("| -------- | ---------------- | -------------- | ------ |\n");
      for (var entry : result.results.entrySet()) {
         sb.append("| ").append(entry.getKey().variable.name).append(" | ")
               .append(entry.getValue().experimentValue).append(" | ")
               .append(entry.getValue().baselineValue).append(" | ")
               .append(toEmoji(entry.getValue().overall)).append(" ").append(entry.getValue().result).append(" |\n");
      }
      return sb.toString();
   }

   private String datasetLink(DataSet.Info info) {
      return String.format("[%d#%d](%s/run/%d#dataset%d)", info.runId, info.ordinal + 1, publicUrl, info.runId, info.ordinal);
   }

   private String toEmoji(ExperimentService.BetterOrWorse overall) {
      switch (overall) {
         case BETTER:
            return ":green_square:";
         case WORSE:
            return ":red_square:";
         case SAME:
         default:
            return ":white_check_mark:";
      }
   }
}
