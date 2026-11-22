import generated.SplLexer;
import generated.Splc;
import impl.IRGenerator;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Fixed framework entry. The only student-facing Java contract is IRGenerator.generate(ProgramContext). */
public final class Main {
    private static final int MAX_SOURCE = 1024 * 1024;
    private static final int MAX_INPUT = 512 * 1024;
    private static final int MAX_OUTPUT = 1024 * 1024;
    private static final int RUN_TIMEOUT_SECONDS = 5;

    private static final class StrictErrors extends BaseErrorListener {
        @Override public void syntaxError(Recognizer<?, ?> r, Object token,
                int line, int col, String msg, RecognitionException ex) {
            throw new ParseCancellationException(line + ":" + col + " " + msg);
        }
    }

    private static String translate(String source) {
        SplLexer lexer = new SplLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new StrictErrors());
        Splc parser = new Splc(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new StrictErrors());
        Splc.ProgramContext tree = parser.program();
        return new IRGenerator().generate(tree);
    }

    /** Readable line-separated official test file. Sentinels occupy whole lines. */
    private static final class Lines {
        private final String[] lines;
        private int pos;
        Lines(byte[] raw) {
            if (raw.length > MAX_SOURCE + 100L * (MAX_INPUT + 64L))
                throw new IllegalArgumentException("Official case is too large");
            String s = new String(raw, StandardCharsets.UTF_8);
            if (s.indexOf('\r') >= 0 || !s.endsWith("\n"))
                throw new IllegalArgumentException("Test data must use LF and end in LF");
            lines = s.split("\n", -1);
        }
        String next() {
            if (pos >= lines.length - 1)
                throw new IllegalArgumentException("Unexpected end of test data");
            return lines[pos++];
        }
        void expect(String want) {
            String got = next();
            if (!got.equals(want))
                throw new IllegalArgumentException("Expected '" + want + "', got '" + got + "'");
        }
        String until(String marker, int limit) {
            StringBuilder result = new StringBuilder();
            while (true) {
                String line = next();
                if (line.equals(marker)) {
                    if (result.toString().getBytes(StandardCharsets.UTF_8).length > limit)
                        throw new IllegalArgumentException("Section too large");
                    return result.toString();
                }
                if (isMarker(line))
                    throw new IllegalArgumentException("Unexpected delimiter: " + line);
                result.append(line).append('\n');
                if (result.length() > limit + 1)
                    throw new IllegalArgumentException("Section too large");
            }
        }
        void finish() {
            if (pos != lines.length - 1)
                throw new IllegalArgumentException("Unexpected trailing text after END");
        }
    }

    private static boolean isMarker(String s) {
        return s.matches("=== (?:SOURCE|INPUT [0-9]+|OUTPUT [0-9]+|END|ERROR [A-Z]+) ===");
    }

    private record TestCase(String source, List<byte[]> inputs) {}
    private static TestCase readCase(byte[] raw) {
        Lines in = new Lines(raw);
        String header = in.next();
        if (!header.matches("CASES [1-9][0-9]*"))
            throw new IllegalArgumentException("Expected CASES N");
        int count;
        try { count = Integer.parseInt(header.substring(6)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Bad CASES count",e); }
        if (count < 1 || count > 100) throw new IllegalArgumentException("Bad CASES count");
        in.expect("=== SOURCE ===");
        String source = in.until("=== INPUT 1 ===", MAX_SOURCE);
        List<byte[]> inputs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String next = i < count ? "=== INPUT " + (i+1) + " ===" : "=== END ===";
            inputs.add(in.until(next,MAX_INPUT).getBytes(StandardCharsets.UTF_8));
        }
        in.finish();
        return new TestCase(source,inputs);
    }

    private static String readableStdout(byte[] bytes) {
        // SPL's write(x) prints signed decimal integers followed by LF.
        if (bytes.length > 0 && bytes[bytes.length-1] != '\n')
            return "P5_ERROR_RE\n";
        String stdout = new String(bytes,StandardCharsets.UTF_8);
        if (stdout.indexOf('\r') >= 0) return "P5_ERROR_RE\n";
        for (String line : stdout.split("\n",-1)) {
            if (isMarker(line)) return "P5_ERROR_RE\n";
        }
        return stdout;
    }

    private static void printResults(List<String> output) {
        System.out.println("CASES " + output.size());
        for (int i = 0; i < output.size(); i++) {
            System.out.println("=== OUTPUT " + (i+1) + " ===");
            System.out.print(output.get(i));
        }
        System.out.println("=== END ===");
    }

    private record Proc(int code, byte[] stdout, byte[] stderr) {}

    private static Proc process(List<String> command, byte[] stdin, Path work, int seconds)
            throws Exception {
        Process p = new ProcessBuilder(command).directory(work.toFile()).start();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> stdout = pool.submit(() -> p.getInputStream().readNBytes(MAX_OUTPUT + 1));
            Future<byte[]> stderr = pool.submit(() -> p.getErrorStream().readNBytes(MAX_OUTPUT + 1));
            try (OutputStream os = p.getOutputStream()) { os.write(stdin); }
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
                p.destroyForcibly(); throw new TimeoutException("Process timed out: " + command.get(0));
            }
            byte[] out = stdout.get(2, TimeUnit.SECONDS);
            byte[] err = stderr.get(2, TimeUnit.SECONDS);
            if (out.length > MAX_OUTPUT || err.length > MAX_OUTPUT)
                throw new IOException("Process produced excessive output");
            return new Proc(p.exitValue(), out, err);
        } finally {
            p.destroyForcibly(); pool.shutdownNow();
        }
    }

    private static Path compile(String ir, Path work) throws Exception {
        Path ll = work.resolve("output.ll"), exe = work.resolve("program");
        Files.writeString(ll, ir, StandardCharsets.UTF_8);
        String opt = System.getenv().getOrDefault("P5_OPT", "opt-19");
        String clang = System.getenv().getOrDefault("P5_CLANG", "clang-19");
        // The Hydro language copies only project.jar into the execution sandbox.
        // Embed runtime/runtime.c in the JAR and extract it for clang at execution time.
        String runtime = System.getenv("P5_RUNTIME");
        if (runtime == null || runtime.isBlank()) {
            Path bundledRuntime = work.resolve("runtime.c");
            try (InputStream in = Main.class.getResourceAsStream("/runtime/runtime.c")) {
                if (in == null) throw new IOException("Missing runtime/runtime.c inside project.jar");
                Files.copy(in, bundledRuntime, StandardCopyOption.REPLACE_EXISTING);
            }
            runtime = bundledRuntime.toString();
        }
        if (!"none".equals(opt)) {
            Proc check = process(List.of(opt, "-passes=verify", "-disable-output", ll.toString()),
                                 new byte[0], work, 12);
            if (check.code() != 0)
                throw new IOException("LLVM verifier rejected IR: " + new String(check.stderr(), StandardCharsets.UTF_8));
        }
        Proc cc = process(List.of(clang, "-O0", ll.toString(), runtime, "-o", exe.toString()),
                          new byte[0], work, 12);
        if (cc.code() != 0)
            throw new IOException("Clang rejected IR: " + new String(cc.stderr(), StandardCharsets.UTF_8));
        return exe;
    }

    private static void cleanup(Path work) {
        if (work == null) return;
        try (var paths = Files.walk(work)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static void judge(TestCase tc) throws Exception {
        List<String> output = new ArrayList<>();
        String ir;
        try { ir = translate(tc.source()); }
        catch (Throwable e) {
            System.err.println("SPL-to-LLVM compilation failed: " + e);
            for (int i=0;i<tc.inputs().size();i++) output.add("P5_ERROR_CE\n");
            printResults(output);
            return;
        }
        Path work = Files.createTempDirectory("cs323-p5-");
        try {
            Path exe;
            try { exe = compile(ir, work); }
            catch (Exception e) {
                System.err.println("LLVM build failed: " + e);
                for (int i=0;i<tc.inputs().size();i++) output.add("P5_ERROR_CE\n");
                printResults(output);
                return;
            }
            for (int i=0;i<tc.inputs().size();i++) {
                try {
                    Proc out = process(List.of(exe.toString()), tc.inputs().get(i), work, RUN_TIMEOUT_SECONDS);
                    output.add(out.code() == 0 ? readableStdout(out.stdout()) : "P5_ERROR_RE\n");
                } catch (TimeoutException e) {
                    System.err.println("Run " + (i+1) + " timed out: " + e);
                    output.add("P5_ERROR_TLE\n");
                } catch (Exception e) {
                    System.err.println("Run " + (i+1) + " failed: " + e);
                    output.add("P5_ERROR_RE\n");
                }
            }
        } finally { cleanup(work); }
        printResults(output);
    }

    private static void runOne(Path source, byte[] stdin) throws Exception {
        String ir = translate(Files.readString(source));
        Path work = Files.createTempDirectory("cs323-p5-local-");
        try {
            Path exe = compile(ir, work);
            Proc out = process(List.of(exe.toString()), stdin, work, RUN_TIMEOUT_SECONDS);
            System.out.write(out.stdout());
            if (out.code() != 0) throw new IOException("Program exited with code " + out.code());
        } finally { cleanup(work); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--emit-ir")) {
            System.out.print(translate(Files.readString(Path.of(args[1]))));
        } else if (args.length == 2 && args[0].equals("--run-spl")) {
            runOne(Path.of(args[1]), System.in.readAllBytes());
        } else if (args.length == 0) {
            judge(readCase(System.in.readAllBytes()));
        } else {
            System.err.println("Usage: Main [--emit-ir file.spl | --run-spl file.spl]");
            System.exit(2);
        }
    }
}
