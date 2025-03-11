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

    /**
     * Reset the references for all entities associated to the test by
     * setting the testId to the current one and resetting all ids to null
     * so that new entities will be created
     */
    public void resetRefs() {
        //need to make sure the correct variables are used by experiments
        if (variables != null && !variables.isEmpty()) {
            for (Variable variable : variables) {
                variable.testId = id;
                variable.id = null;
                variable.changeDetection.forEach(cd -> cd.id = null);
            }
        }

        if (experiments != null && !experiments.isEmpty()) {
            for (ExperimentProfile experiment : experiments) {
                experiment.testId = id;
                experiment.id = null;
            }
        }
        if (actions != null && !actions.isEmpty()) {
            for (Action action : actions) {
                action.testId = id;
                action.id = null;
            }
        }
        if (subscriptions != null) {
            subscriptions.testId = id;
            subscriptions.id = null;
        }
        if (missingDataRules != null && !missingDataRules.isEmpty()) {
            for (MissingDataRule rule : missingDataRules) {
                rule.testId = id;
                rule.id = null;
            }
        }
    }

    public void updateExperimentsVariableId(String variableName, int newVarId) {
        if (experiments != null && !experiments.isEmpty()) {
            for (ExperimentProfile experiment : experiments) {
                if (experiment.comparisons != null && !experiment.comparisons.isEmpty()) {
                    experiment.comparisons.forEach(c -> {
                        if (c.variableName.equals(variableName))
                            c.variableId = newVarId;
                    });
                }
            }
        }
    }
}
