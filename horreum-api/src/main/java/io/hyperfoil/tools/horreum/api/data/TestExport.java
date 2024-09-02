package io.hyperfoil.tools.horreum.api.data;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.hyperfoil.tools.horreum.api.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.alerting.Watch;
import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;

@Schema(type = SchemaType.OBJECT, allOf = Test.class, description = "Represents a Test with all associated data used for export/import operations.")
public class TestExport extends Test {

    @Schema(description = "Array of Variables associated with test")
    public List<Variable> variables;
    @Schema(description = "Array of MissingDataRules associated with test")
    public List<MissingDataRule> missingDataRules;
    @Schema(description = "Array of ExperimentProfiles associated with test")
    public List<ExperimentProfile> experiments;
    @Schema(description = "Array of Actions associated with test")
    public List<Action> actions;
    @Schema(description = "Watcher object associated with test")
    public Watch subscriptions;

    @Schema(description = "Datastore associated with test")
    public Datastore datastore;

    public TestExport() {
        super();
    }

    public TestExport(Test t) {
        super(t);
    }

    //need to propagate the changes to newTest into the existing properties
    public void update(Test newTest) {
        id = newTest.id;
    }

    public void updateRefs() {
        //need to make sure the correct variables are used by experiments
        if (variables != null && !variables.isEmpty())
            variables.forEach(variable -> variable.testId = id);

        if (experiments != null && !experiments.isEmpty()) {
            for (ExperimentProfile experiment : experiments) {
                experiment.testId = id;
            }
        }
        if (actions != null && !actions.isEmpty()) {
            for (Action action : actions) {
                action.testId = id;
            }
        }
        if (subscriptions != null) {
            subscriptions.testId = id;
        }
        if (missingDataRules != null && !missingDataRules.isEmpty()) {
            for (MissingDataRule rule : missingDataRules) {
                rule.testId = id;
            }
        }
    }

    public void updateExperimentsVariableId(int oldVarId, int newVarId) {
        if (experiments != null && !experiments.isEmpty()) {
            for (ExperimentProfile experiment : experiments) {
                if (experiment.comparisons != null && !experiment.comparisons.isEmpty()) {
                    experiment.comparisons.forEach(c -> {
                        if (c.variableId == oldVarId)
                            c.variableId = newVarId;
                    });
                }
            }
        }
    }
}
