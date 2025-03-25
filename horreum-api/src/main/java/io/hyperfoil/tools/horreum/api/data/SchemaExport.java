package io.hyperfoil.tools.horreum.api.data;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;

@org.eclipse.microprofile.openapi.annotations.media.Schema(type = SchemaType.OBJECT, description = "Represents a Schema with all associated data used for export/import operations.")
public class SchemaExport extends Schema {

    @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Array of Labels associated with schema")
    public List<Label> labels;
    @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Array of Transformers associated with schema")
    public List<Transformer> transformers;

    public SchemaExport() {
    }

    public SchemaExport(Schema s) {
        super(s);
    }

}
