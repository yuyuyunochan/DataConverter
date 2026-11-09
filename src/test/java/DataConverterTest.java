import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataConverterTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void csvToJsonPreservesStringsAndQuotedFields() throws IOException {
        JsonNode result = JSON.readTree(DataConverter.convertText(
                "name,age,note\nEva,36,\"said \"\"hi\"\", then left\"\n", "csv", "json"));
        assertEquals("36", result.get(0).get("age").asText());
        assertEquals("said \"hi\", then left", result.get(0).get("note").asText());
    }

    @Test
    void yamlAndJsonConvertBothWays() throws IOException {
        String json = DataConverter.convertText("- name: Eva\n  age: 36\n", "yml", "json");
        assertEquals(36, JSON.readTree(json).get(0).get("age").asInt());
        String yaml = DataConverter.convertText(json, "json", "yaml");
        assertTrue(yaml.contains("Eva"));
    }

    @Test
    void jsonToCsvQuotesFields() throws IOException {
        assertEquals("name,note\nEva,\"hello, world\"\n", DataConverter.convertText(
                "[{\"name\":\"Eva\",\"note\":\"hello, world\"}]", "json", "csv"));
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class,
                () -> DataConverter.convertText("a,b\n1\n", "csv", "json"));
        assertThrows(IllegalArgumentException.class,
                () -> DataConverter.convertText("[{\"nested\":{}}]", "json", "csv"));
        assertThrows(IllegalArgumentException.class,
                () -> DataConverter.convertText("[]", "json", "json"));
        assertThrows(IllegalArgumentException.class,
                () -> DataConverter.convertText("x", "xml", "json"));
    }
}
