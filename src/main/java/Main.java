import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -jar dataconverter-1.0.0.jar <input_file> <output_file>");
            System.exit(2);
        }

        try {
            DataConverter.convertFile(Path.of(args[0]), Path.of(args[1]));
        } catch (Exception e) {
            System.err.println("dataconv: " + e.getMessage());
            System.exit(1);
        }
    }
}
