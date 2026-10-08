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
        Integer value = visit(expr);
        if(value == null) return null;
        addResult(expr.getText(), value);

        return null;
    }

    @Override
    public Integer visitExpression(SplcParser.ExpressionContext ctx){
        if(ctx.Number() != null){
            return Integer.parseInt(ctx.Number().getText());
        }

        if(ctx.LPAREN() != null && ctx.Identifier() == null){
            return visit(ctx.expression(0));
        }
        if(ctx.PLUS() != null && ctx.expression().size() == 1){
            return visit(ctx.expression(0));
        }
        if(ctx.MINUS() != null && ctx.expression().size() == 1){
            return -visit(ctx.expression(0));
        }
        if(ctx.PLUS() != null && ctx.expression().size() == 2){
            return visit(ctx.expression(0)) + visit(ctx.expression(1));
        }
        if(ctx.MINUS() != null && ctx.expression().size() == 2){
            return visit(ctx.expression(0)) - visit(ctx.expression(1));
        }
        if(ctx.MUL() != null && ctx.expression().size() == 2){
            return visit(ctx.expression(0)) * visit(ctx.expression(1));
        }
        if(ctx.DIV() != null && ctx.expression().size() == 2){
            return visit(ctx.expression(0)) / visit(ctx.expression(1));
        }
        if(ctx.MOD() != null && ctx.expression().size() == 2){
            return visit(ctx.expression(0)) % visit(ctx.expression(1));
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
