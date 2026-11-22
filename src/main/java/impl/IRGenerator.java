package impl;
import generated.Splc;
import generated.SplcBaseVisitor;
/** Student-controlled implementation. The Visitor and StringBuilder are optional. */
public class IRGenerator extends SplcBaseVisitor<Void> {
    // OPTIONAL: you may remove, replace, or split this buffer.
    private final StringBuilder ir = new StringBuilder();
    protected void emit(String line) { ir.append(line).append('\n'); }
    /** The ONLY required interface for the fixed framework. */
    public String generate(Splc.ProgramContext tree) {
        ir.setLength(0);
        visit(tree);
        return ir.toString();
    }
    @Override public Void visitProgram(Splc.ProgramContext ctx) {
        // TODO: register symbols and types; translate global declarations, then functions.
        // You can add # labeled alternatives in your own Splc.g4 to customize visitXXX.
        return null;
    }
    @Override public Void visitFunctionDef(Splc.FunctionDefContext ctx) {
        // TODO: LLVM IR function signature, parameters, blocks and terminators.
        return null;
    }
}
