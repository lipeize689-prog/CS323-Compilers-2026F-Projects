import framework.project2.ConstExprResult;
import generated.SplcBaseVisitor;
import generated.SplcParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstExprVisitor extends SplcBaseVisitor<Integer> {

    private final List<ConstExprResult> results = new ArrayList<>();

    public final List<ConstExprResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    protected final void addResult(String expression, int value) {
        results.add(new ConstExprResult(expression, value));
    }

    @Override
    public Integer visitLocalVarDef(SplcParser.LocalVarDefContext ctx) {
        if(ctx.ASSIGN() == null || ctx.expression() == null){
            return null;
        }
        SplcParser.ExpressionContext expr = ctx.expression();
        if(!isConstExpr(expr)){
            return null;
        }
        Integer value = evaluateConstExpr(expr);
        if(value == null) return null;
        addResult(expr.getText(), value);

        return null;
    }

    /**
     * Evaluate an expression that has already been accepted by isConstExpr.
     *
     * This deliberately is not a visitExpression override.  The visitor is
     * also used to walk return statements and ordinary expression statements;
     * evaluating those expressions would attempt to unbox null for variables
     * in expressions such as {@code return x + 1;}.
     */
    private Integer evaluateConstExpr(SplcParser.ExpressionContext ctx){
        if(ctx.Number() != null){
            return Integer.parseInt(ctx.Number().getText());
        }

        if(ctx.LPAREN() != null && ctx.Identifier() == null){
            return evaluateConstExpr(ctx.expression(0));
        }
        if(ctx.PLUS() != null && ctx.expression().size() == 1){
            return evaluateConstExpr(ctx.expression(0));
        }
        if(ctx.MINUS() != null && ctx.expression().size() == 1){
            return -evaluateConstExpr(ctx.expression(0));
        }
        if(ctx.PLUS() != null && ctx.expression().size() == 2){
            return evaluateConstExpr(ctx.expression(0)) + evaluateConstExpr(ctx.expression(1));
        }
        if(ctx.MINUS() != null && ctx.expression().size() == 2){
            return evaluateConstExpr(ctx.expression(0)) - evaluateConstExpr(ctx.expression(1));
        }
        if(ctx.MUL() != null && ctx.expression().size() == 2){
            return evaluateConstExpr(ctx.expression(0)) * evaluateConstExpr(ctx.expression(1));
        }
        if(ctx.DIV() != null && ctx.expression().size() == 2){
            return evaluateConstExpr(ctx.expression(0)) / evaluateConstExpr(ctx.expression(1));
        }
        if(ctx.MOD() != null && ctx.expression().size() == 2){
            return evaluateConstExpr(ctx.expression(0)) % evaluateConstExpr(ctx.expression(1));
        }
        return null;
    }

    public boolean isConstExpr(SplcParser.ExpressionContext context){
        if(context.Number() != null){
            return true;
        }
        if(context.LPAREN() != null && context.Identifier() == null){
            return isConstExpr(context.expression(0));
        }
        if(context.PLUS() != null && context.expression().size() == 1){
            return isConstExpr(context.expression(0));
        }
        if(context.MINUS() != null && context.expression().size() == 1){
            return isConstExpr(context.expression(0));
        }
        if(context.PLUS() != null && context.expression().size() == 2){
            return isConstExpr(context.expression(0)) && isConstExpr(context.expression(1));
        }
        if(context.MINUS() != null && context.expression().size() == 2){
            return isConstExpr(context.expression(0)) && isConstExpr(context.expression(1));
        }
        if(context.MUL() != null && context.expression().size() == 2){
            return isConstExpr(context.expression(0)) && isConstExpr(context.expression(1));
        }
        if(context.DIV() != null && context.expression().size() == 2){
            return isConstExpr(context.expression(0)) && isConstExpr(context.expression(1));
        }
        if(context.MOD() != null && context.expression().size() == 2){
            return isConstExpr(context.expression(0)) && isConstExpr(context.expression(1));
        }
        return false;
    }

    
    /*
     * TODO:
     *
     * 1. Find the complete expression on the right-hand side of each local
     *    variable definition.
     * 2. Determine whether that complete expression is a constexpr according
     *    to the Project 2 specification.
     * 3. Evaluate it if it is a constexpr.
     * 4. Store the result with addResult(...).
     *
     * Do not add constexpr subexpressions of a non-constexpr whole expression.
     */
}
