package io.hyperfoil.tools.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.IOException;

public class JsonSerializer extends StdSerializer<Json> {

    public JsonSerializer() {
        this(null);
    }

    public JsonSerializer(Class<Json> t) {
        super(t);
    }


    @Override
    public void serialize(Json value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeRaw(Json.toJsonNode(value).toString());
    }
}
