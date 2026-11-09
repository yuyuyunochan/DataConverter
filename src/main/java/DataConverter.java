import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DataConverter {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();
    private static final Set<String> FORMATS = Set.of("csv", "json", "yaml");

    private DataConverter() {
    }

    public static String convertText(String input, String inputExtension, String outputExtension) throws IOException {
        String source = normalizeExtension(inputExtension);
        String target = normalizeExtension(outputExtension);
        if (source.equals(target)) {
            throw new IllegalArgumentException("Input and output formats must differ");
        }

        JsonNode data = switch (source) {
            case "csv" -> parseCsv(input);
            case "json" -> JSON.readTree(input);
            default -> YAML.readTree(input);
        };
        if (data == null) {
            if (source.equals("json")) {
                throw new IOException("Empty JSON input");
            }
            data = NullNode.getInstance();
        }

        return switch (target) {
            case "csv" -> toCsv(data);
            case "json" -> JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n";
            default -> YAML.writeValueAsString(data);
        };
    }

    public static void convertFile(Path input, Path output) throws IOException {
        String converted = convertText(
                Files.readString(input, StandardCharsets.UTF_8),
                extension(input), extension(output));
        Files.writeString(output, converted, StandardCharsets.UTF_8);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("File has no extension: " + path);
        }
        return name.substring(dot + 1);
    }

    private static String normalizeExtension(String extension) {
        String format = extension.toLowerCase(Locale.ROOT);
        if (format.startsWith(".")) {
            format = format.substring(1);
        }
        if (format.equals("yml")) {
            format = "yaml";
        }
        if (!FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unsupported file extension: " + format);
        }
        return format;
    }

    private static ArrayNode parseCsv(String input) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Malformed CSV: unterminated quoted field");
        }
        if (field.length() > 0 || !row.isEmpty() || fieldStarted) {
            row.add(field.toString());
            rows.add(row);
        }

        ArrayNode result = JSON.createArrayNode();
        if (rows.isEmpty()) {
            return result;
        }
        List<String> headers = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            List<String> values = rows.get(i);
            if (values.size() != headers.size()) {
                throw new IllegalArgumentException("CSV row has a different number of fields than the header");
            }
            ObjectNode record = JSON.createObjectNode();
            for (int j = 0; j < headers.size(); j++) {
                record.put(headers.get(j), values.get(j));
            }
            result.add(record);
        }
        return result;
    }

    private static String toCsv(JsonNode data) {
        if (!data.isArray()) {
            throw new IllegalArgumentException("Expected an array/sequence of records");
        }
        if (data.isEmpty()) {
            return "";
        }
        JsonNode first = data.get(0);
        if (!first.isObject()) {
            throw new IllegalArgumentException("Each record must be an object/map");
        }
        List<String> headers = new ArrayList<>();
        first.fieldNames().forEachRemaining(headers::add);
        StringBuilder output = new StringBuilder();
        appendCsvRow(output, headers);

        for (JsonNode record : data) {
            if (!record.isObject()) {
                throw new IllegalArgumentException("Each record must be an object/map");
            }
            if (record.size() != headers.size()) {
                throw new IllegalArgumentException("Records must have identical keys");
            }
            List<String> values = new ArrayList<>(headers.size());
            for (String header : headers) {
                JsonNode value = record.get(header);
                if (value == null) {
                    throw new IllegalArgumentException("Records must have identical keys");
                }
                if (value.isContainerNode()) {
                    throw new IllegalArgumentException("Nested values cannot be exported to CSV");
                }
                values.add(value.isTextual() ? value.textValue() : value.toString());
            }
            appendCsvRow(output, values);
        }
        return output.toString();
    }

    private static void appendCsvRow(StringBuilder output, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                output.append(',');
            }
            String value = values.get(i);
            boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
            if (quote) {
                output.append('"');
            }
            output.append(value.replace("\"", "\"\""));
            if (quote) {
                output.append('"');
            }
        }
        if (!values.isEmpty()) {
            output.append('\n');
        }
    }
}
