package io.hyperfoil.tools.horreum.api.data;

import java.util.Collection;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Schema(type = SchemaType.OBJECT, description = "Represents a Test. Tests are typically equivalent to a particular benchmark")
public class Test extends ProtectedType {
    @JsonProperty(required = true)
    @Schema(description = "Unique Test id", example = "101")
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Test name", example = "my-comprehensive-benchmark")
    public String name;
    @Schema(description = "Name of folder that the test is stored in. Folders allow tests to be organised in the UI", example = "My Team Folder")
    public String folder;
    @Schema(description = "Description of the test", example = "Comprehensive benchmark to tests the limits of any system it is run against")
    public String description;

    @NotNull
    @Schema(description = "backend ID for backing datastore")
    public Integer datastoreId;

    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "List of label names that are used for determining metric to use as the time series", example = "[ \"timestamp\" ]")
    public JsonNode timelineLabels;
    @Schema(description = "Label function to modify timeline labels to a produce a value used for ordering datapoints", example = "timestamp => timestamp")
    public String timelineFunction;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "Array of Label names that are used to create a fingerprint ", example = "[ \"build_tag\" ]")
    public JsonNode fingerprintLabels;
    @Schema(description = "Filter function to filter out datasets that are comparable for the purpose of change detection", example = "value => value === \"true\"")
    public String fingerprintFilter;
    @Schema(description = "URL to external service that can be called to compare runs.  This is typically an external reporting/visulization service", example = "(ids, token) => 'http://repoting.example.com/report/specj?q=' + ids.join('&q=') + \"&token=\"+token")
    public String compareUrl;

    @Schema(description = "Array for transformers defined for the Test")
    public Collection<Transformer> transformers;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Are notifications enabled for the test", example = "true")
    public Boolean notificationsEnabled;

    public Test() {
        this.access = Access.PUBLIC;
    }

    public Test(Test t) {
        id = t.id;
        name = t.name;
        folder = t.folder;
        description = t.description;
        timelineLabels = t.timelineLabels;
        timelineFunction = t.timelineFunction;
        fingerprintLabels = t.fingerprintLabels;
        fingerprintFilter = t.fingerprintFilter;
        compareUrl = t.compareUrl;
        transformers = t.transformers;
        notificationsEnabled = t.notificationsEnabled;
        access = t.access;
        owner = t.owner;
    }

    @Override
    public String toString() {
        return "Test{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", folder='" + folder + '\'' +
                ", description='" + description + '\'' +
                ", owner='" + owner + '\'' +
                ", access=" + access + '\'' +
                ", datastoreId= " + datastoreId + '\'' +
                ", timelineLabels=" + timelineLabels + '\'' +
                ", timelineFunction='" + timelineFunction + '\'' +
                ", fingerprintLabels=" + fingerprintLabels + '\'' +
                ", fingerprintFilter='" + fingerprintFilter + '\'' +
                ", compareUrl='" + compareUrl + '\'' +
                ", transformers=" + transformers +
                ", notificationsEnabled=" + notificationsEnabled +
                '}';
    }

    public void clearIds() {
        id = null;
    }
}
