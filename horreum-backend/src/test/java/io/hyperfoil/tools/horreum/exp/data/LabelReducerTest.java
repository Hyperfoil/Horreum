package io.hyperfoil.tools.horreum.exp.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabelReducerTest {

    @org.junit.jupiter.api.Test
    public void evalJavascript_identity() throws JsonProcessingException {
        LabelReducerDao reducer = new LabelReducerDao();
        reducer.function = "(v)=>v";

        JsonNode input;
        input = new ObjectMapper().readTree("{\"foo\":\"bar\"}");
        assertEquals(input,reducer.evalJavascript(input),"identity function should not change input object");
        input = new ObjectMapper().getNodeFactory().numberNode(10l);
        assertEquals(input,reducer.evalJavascript(input),"identity function should not change input number");
    }
    @org.junit.jupiter.api.Test
    public void evalJavascript_double() throws JsonProcessingException {
        LabelReducerDao reducer = new LabelReducerDao();
        reducer.function = "(v)=>v+v";

        JsonNode input;
        input = new ObjectMapper().getNodeFactory().numberNode(10);
        JsonNode expected = new ObjectMapper().getNodeFactory().numberNode(20l);
        JsonNode output = reducer.evalJavascript(input);
        assertEquals(expected,output,"result should be double the input");
        input = new ObjectMapper().getNodeFactory().textNode("foo");
        expected = new ObjectMapper().getNodeFactory().textNode("foofoo");
        output = reducer.evalJavascript(input);
        assertEquals(expected,output,"result should be double the input");
    }


}
