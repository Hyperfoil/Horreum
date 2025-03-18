package io.hyperfoil.tools.horreum.api.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.SchemaExport;
import io.hyperfoil.tools.horreum.api.data.Transformer;

@Path("api/schema")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON })
@Tag(name = "Schema", description = "Manage schemas")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface SchemaService {
    @GET
    @Path("{id}")
    @APIResponse(responseCode = "404", description = "No Schema with the given id was found", content = @Content(mediaType = "application/json"))
    @APIResponseSchema(value = Schema.class, responseCode = "200", responseDescription = "Returns Schema if a matching id is found")
    @Operation(description = "Retrieve Schema by ID")
    @Parameters(value = {
            @Parameter(name = "id", description = "Schema ID to retrieve", example = "101")
    })
    Schema getSchema(@PathParam("id") int id);

    @GET
    @Path("idByUri/{uri}")
    @Operation(description = "Retrieve Schema ID by uri")
    @Parameters(value = {
            @Parameter(name = "uri", description = "Schema uri", example = "uri:my-schema:0.1"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", content = {
                    @Content(schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(type = SchemaType.INTEGER, implementation = Integer.class), example = "101")
            })
    })
    int idByUri(@PathParam("uri") String uri);

    @POST
    @Operation(description = "Save a new Schema")
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", description = "New schema created successfully", content = @Content(schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(type = SchemaType.INTEGER, implementation = Integer.class), example = "103"))
    Integer add(Schema schema);

    @PUT
    @Operation(description = "Update an existing Schema")
    @APIResponse(responseCode = "200", description = "Schema updated successfully", content = @Content(schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(type = SchemaType.INTEGER, implementation = Integer.class), example = "103"))
    Integer update(Schema schema);

    @GET
    @Operation(description = "Retrieve a paginated list of Schemas with available count")
    @Parameters(value = {
            @Parameter(name = "limit", description = "limit the number of results", example = "20"),
            @Parameter(name = "page", description = "filter by page number of a paginated list of Schemas", example = "2"),
            @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
            @Parameter(name = "direction", description = "Sort direction", example = "Ascending"),
            @Parameter(name = "roles", description = "__my, __all or a comma delimited  list of roles", example = "__my"),
    })
    SchemaQueryResult list(@QueryParam("roles") String roles,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction,
            @QueryParam("name") String name);

    @GET
    @Path("descriptors")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Retrieve a list of Schema Descriptors")
    @Parameters(value = {
            @Parameter(name = "id", description = "Limit to a single Schema by ID", example = "102"),
    })
    List<SchemaDescriptor> descriptors(@QueryParam("id") List<Integer> ids);

    @POST
    @Path("{id}/updateAccess")
    // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
    @Operation(description = "Update the Access configuration for a Schema")
    @Parameters(value = {
            @Parameter(name = "id", description = "Schema ID to update Access", example = "101"),
            @Parameter(name = "owner", required = true, description = "Name of the new owner", example = "perf-team"),
            @Parameter(name = "access", required = true, description = "New Access level", example = "0")
    })
    void updateAccess(@PathParam("id") int id,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access);

    @DELETE
    @Path("{id}")
    @Operation(description = "Delete a Schema by id")
    @Parameters(value = {
            @Parameter(name = "id", description = "Schema ID to delete", example = "101"),
    })
    void delete(@PathParam("id") int id);

    @GET
    @Path("findUsages")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Find all usages of a Schema by label name")
    @Parameters(value = {
            @Parameter(name = "label", required = true, description = "Name of label to search for", example = "Throughput"),
    })
    List<LabelLocation> findUsages(@QueryParam("label") String label);

    @GET
    @Path("{schemaId}/transformers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List all Transformers defined for a Schema")
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
    })
    List<Transformer> listTransformers(@PathParam("schemaId") int schemaId);

    @POST
    @Path("{schemaId}/transformers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Save new or update existing Transformer definition")
    @ResponseStatus(201)
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "New transformer created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = Integer.class)))
    })
    int addTransformer(@PathParam("schemaId") int schemaId, @RequestBody(required = true) Transformer transformer);

    @PUT
    @Path("{schemaId}/transformers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Save new or update existing Transformer definition")
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Transformer updated successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = Integer.class)))
    })
    int updateTransformer(@PathParam("schemaId") int schemaId, @RequestBody(required = true) Transformer transformer);

    @DELETE
    @Path("{schemaId}/transformers/{transformerId}")
    @Operation(description = "Delete a Transformer defined for a Schema")
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
            @Parameter(name = "transformerId", description = "Transformer ID", example = "202"),
    })
    void deleteTransformer(@PathParam("schemaId") int schemaId, @PathParam("transformerId") int transformerId);

    @GET
    @Path("{schemaId}/labels")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Retrieve list of Labels for a Schema by Schema ID")
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
    })
    List<Label> labels(@PathParam("schemaId") int schemaId);

    @POST
    @Path("{schemaId}/labels")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Save new or update existing Label for a Schema (Label id only required when updating existing one)")
    @ResponseStatus(201)
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "New schema created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = Integer.class)))
    })
    Integer addLabel(@PathParam("schemaId") int schemaId, @RequestBody(required = true) Label label);

    @PUT
    @Path("{schemaId}/labels")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Update existing Label for a Schema (Label id only required when updating existing one)")
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Schema updated successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = Integer.class)))
    })
    Integer updateLabel(@PathParam("schemaId") int schemaId, @RequestBody(required = true) Label label);

    @DELETE
    @Path("{schemaId}/labels/{labelId}")
    @Operation(description = "Delete existing Label from a Schema")
    @Parameters(value = {
            @Parameter(name = "schemaId", description = "Schema ID", example = "101"),
            @Parameter(name = "labelId", description = "Label ID", example = "202"),
    })
    void deleteLabel(@PathParam("schemaId") int schemaId, @PathParam("labelId") int labelId);

    @GET
    @Path("allLabels")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Retrieve list of Labels for ny name. Allows users to retrieve all Label Definitions that have the same name")
    @Parameters(value = {
            @Parameter(name = "name", description = "Label name", example = "buildID"),
    })
    Collection<LabelInfo> allLabels(@QueryParam("name") String name);

    @GET
    @Path("allTransformers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Retrieve all transformers")
    List<TransformerInfo> allTransformers();

    @GET
    @Path("{id}/export")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Export a Schema")
    @Parameters(value = {
            @Parameter(name = "id", description = "Schema ID", example = "101"),
    })
    @APIResponseSchema(value = SchemaExport.class, responseDescription = "A JSON representation of the SchemaExport object", responseCode = "200")
    SchemaExport exportSchema(@PathParam("id") int id);

    @POST
    @Path("import")
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", description = "New Schema created successfully using previously exported one", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = Integer.class)))
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = SchemaExport.class)))
    @Operation(description = "Import a previously exported Schema as a new Schema")
    Integer importSchema(SchemaExport config);

    @PUT
    @Path("import")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", description = "Schema updated successfully using previously exported one", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = Integer.class)))
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = SchemaExport.class)))
    @Operation(description = "Update an existing Schema using its previously exported version")
    Integer updateSchema(SchemaExport config);

    class SchemaQueryResult {
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Array of Schemas")
        public List<Schema> schemas;
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Count of available Schemas. This is a count of Schemas that the current user has access to", example = "64")
        public long count;

        // required for automatic parsing on testing side
        public SchemaQueryResult() {
        }

        public SchemaQueryResult(List<Schema> schemas, long count) {
            this.schemas = schemas;
            this.count = count;
        }
    }

    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelLocation", type = SchemaType.OBJECT)
    abstract class LabelLocation {
        @org.eclipse.microprofile.openapi.annotations.media.Schema(type = SchemaType.STRING, implementation = String.class, description = "Location of Label usage", example = "VIEW")
        public LabelFoundLocation type;
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Unique ID for location that references Schema", example = "101")
        public int testId;
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Test name that references Schema", example = "My Benchmark")
        public String testName;

        public LabelLocation() {
        }

        public LabelLocation(LabelFoundLocation type, int testId, String testName) {
            this.type = type;
            this.testId = testId;
            this.testName = testName;
        }
    }

    enum LabelFoundLocation {
        FINGERPRINT,
        MISSINGDATA_RULE,
        VARIABLE,
        VIEW,
        REPORT

    }

    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelInFingerprint", type = SchemaType.OBJECT, allOf = {
            LabelLocation.class })
    class LabelInFingerprint extends LabelLocation {
        public LabelInFingerprint(int testId, String testName) {
            super(LabelFoundLocation.FINGERPRINT, testId, testName);
        }
    }

    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelInRule", type = SchemaType.OBJECT, allOf = {
            LabelLocation.class })
    class LabelInRule extends LabelLocation {
        public int ruleId;
        public String ruleName;

        public LabelInRule(int testId, String testName, int ruleId, String ruleName) {
            super(LabelFoundLocation.MISSINGDATA_RULE, testId, testName);
            this.ruleId = ruleId;
            this.ruleName = ruleName;
        }
    }

    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelInVariable", type = SchemaType.OBJECT, allOf = {
            LabelLocation.class })
    class LabelInVariable extends LabelLocation {
        public int variableId;
        public String variableName;

        public LabelInVariable(int testId, String testName, int variableId, String variableName) {
            super(LabelFoundLocation.VARIABLE, testId, testName);
            this.variableId = variableId;
            this.variableName = variableName;
        }
    }

    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelInView", type = SchemaType.OBJECT, allOf = {
            LabelLocation.class })
    class LabelInView extends LabelLocation {
        public int viewId;
        public String viewName;
        public int componentId;
        public String header;

        public LabelInView(int testId, String testName, int viewId, String viewName, int componentId, String header) {
            super(LabelFoundLocation.VIEW, testId, testName);
            this.viewId = viewId;
            this.componentId = componentId;
            this.viewName = viewName;
            this.header = header;
        }
    }

    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelInReport", type = SchemaType.OBJECT, allOf = {
            LabelLocation.class })
    class LabelInReport extends LabelLocation {
        public int configId;
        public String title;
        public String where; // component, filter, category, series, label
        public String name; // only set for component

        public LabelInReport(int testId, String testName, int configId, String title, String where, String name) {
            super(LabelFoundLocation.REPORT, testId, testName);
            this.configId = configId;
            this.title = title;
            this.where = where;
            this.name = name;
        }
    }

    class TransformerInfo {
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Schema ID", example = "101")
        public int schemaId;
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Schema uri", example = "uri:my-schema:0.1")
        public String schemaUri;
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Schema name", example = "my-benchmark-schema")
        public String schemaName;
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Transformer ID", example = "201")
        public int transformerId;
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Transformer name", example = "my-dataset-transformer")
        public String transformerName;
    }

    class SchemaDescriptor {
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Schema unique ID", example = "1")
        public int id;
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Schema name", example = "my-benchmark-schema")
        public String name;
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Schema name", example = "uri:my-schmea:0.1")
        public String uri;

        public SchemaDescriptor() {
        }

        public SchemaDescriptor(int id, String name, String uri) {
            this.id = id;
            this.name = name;
            this.uri = uri;
        }
    }

    // this roughly matches run_schemas table

    class SchemaUsage extends SchemaDescriptor {
        // 0 is data, 1 is metadata. DataSets always use 0
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Source of schema usage, 0 is data, 1 is metadata. DataSets always use 0", example = "1")
        public int source;

        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Location of Schema Usage, 0 for Run, 1 for Dataset", example = "1")
        public int type;

        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Ordinal position of schema usage in Run/Dataset", example = "1")
        public String key;

        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Does schema have a JSON validation schema defined?", example = "false")
        public boolean hasJsonSchema;
    }

    class LabelInfo {
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Label name", example = "buildID")
        public String name;
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Is label a metrics label?", example = "true")
        public boolean metrics;
        @JsonProperty(required = true)
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Is label a filtering label?", example = "false")
        public boolean filtering;
        @NotNull
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "List of schemas where label is referenced")
        public List<SchemaDescriptor> schemas = new ArrayList<>();

        public LabelInfo() {
        }

        public LabelInfo(String name) {
            this.name = name;
        }
    }
}
