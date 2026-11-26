package impl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import generated.Splc;
import generated.SplcBaseVisitor;
/** Student-controlled implementation. The Visitor and StringBuilder are optional. */
public class IRGenerator extends SplcBaseVisitor<Void> {
    // OPTIONAL: you may remove, replace, or split this buffer.
    private final StringBuilder ir = new StringBuilder();
    protected void emit(String line) { ir.append(line).append('\n'); }
    private int varCount = 0;
    private int tempCount = 0;
    private int labelCount = 0; // 逻辑运算的标签
    /** The ONLY required interface for the fixed framework. */
    private static class SplType {
        final SplType elementType; // 数组
        final String length; // 数组
        final String structName; // 结构体
        List<SplType> structFields; // 结构体
        List<String> structFieldNames; // 对于结构体，编译器需要按照成员声明次序确定LLVM聚合类型中的字段类型和索引        
        // 例如p.y翻译成LLVM IR的时候，就需要知道y这个字段的索引%field.addr = getelementptr %struct.Point, ptr @p, i32 0, i32 1

        private SplType(SplType elementType, String length, String structName) {
            this.elementType = elementType;
            this.length = length;
            this.structName = structName;
        }

        static final SplType INT = new SplType(null, null, null);

        static SplType array(String length, SplType elementType) {
            return new SplType(elementType, length, null);
        }

        static SplType struct(String structName, List<SplType> structFields, List<String> structFieldNames){
            SplType t = new SplType(null, null, structName);
            t.structFields = structFields;
            t.structFieldNames = structFieldNames;
            return t;
        }

        String llvm() {
            if (elementType != null){
                return "[" + length + " x " + elementType.llvm() + "]";
            }
            if(structName != null){
                return "%struct." + structName;
            } // 结构体声明类型，并非结构体定义
            return "i32";
        }
    }

    private static class VariableSymbol{
        final SplType llvmType;
        final String address;
        private VariableSymbol(SplType llvmType, String address){
            this.llvmType = llvmType;
            this.address = address;
        }
    }

    // 表达式结果，记录值的类型和 LLVM 操作数，不是变量的存储地址。
    // 记录表达式结果的类型和引用它的数字或临时名字，供后续运算使用，不必为每个中间结果分配内存。
    private static class Value {
        final SplType type;
        final String operand;

        Value(SplType type, String operand){
            this.type = type;
            this.operand = operand;
        }
    }
    private static class Address {
        final SplType type;
        final String pointer;
        
        Address(SplType type, String pointer){
            this.type = type;
            this.pointer = pointer;
        }
    }
    private String freshTemp(){
        return "%t" + (tempCount++);
    }
    private String freshLable(){
        return "bb." + (labelCount++);
    }
    // 条件跳转方法 真，跳到 trueLabel 假，跳到 falseLabel
    private void emitCond(Splc.ExpressionContext ctx, String trueLabel, String falseLabel){
        // 括号
        if(ctx.LPAREN() != null && ctx.expression().size() == 1){
            emitCond(ctx.expression(0), trueLabel, falseLabel);
            return;
        }

        // 短路与，左边真执行右边
        if(ctx.AND() != null){
            String rhsLabel = freshLable();
            emitCond(ctx.expression(0), rhsLabel, falseLabel); // 如果expression(0)是真，逻辑表达式的计算就跳到rhsLabel，否则，这个逻辑表达式直接跳到falseLabel
            emit(rhsLabel + ":");
            emitCond(ctx.expression(1), trueLabel, falseLabel); // 如果expression(0)是真，跳到了rhsLabel，此时判断expression(1)是否为真，是，跳到真标签，反之，跳到假
            return;
        }

        // 短路或，左边假执行右
        if(ctx.OR() != null){
            String rhsLabel = freshLable();
            emitCond(ctx.expression(0), trueLabel, rhsLabel);
            emit(rhsLabel + ":");
            emitCond(ctx.expression(1), trueLabel, falseLabel);
            return;
        }

        // 非：交换真、假目的地
        if(ctx.NOT() != null){
            emitCond(ctx.expression(0), falseLabel, trueLabel);
            return;
        }

        // 其他整数表达式：非零为真，零为假
        Value value = emitValue(ctx);
        if(value.type != SplType.INT){
            throw new IllegalStateException("条件表达式必须是整数");
        }
        String test = freshTemp();
        emit(test + " = icmp ne i32 " + value.operand + " , 0");
        emit("br i1 " + test + ", label %" + trueLabel + ", label %" + falseLabel);
    }
    private Value emitValue(Splc.ExpressionContext ctx){
        if(ctx.Number() != null){
            return new Value(SplType.INT, ctx.Number().getText());
        }
        if((ctx.Identifier() != null && ctx.getChildCount() == 1) || ctx.LBRACK() != null || ctx.DOT() != null){
            Address address = emitAddress(ctx);
            if (address.type.elementType != null) {
                throw new UnsupportedOperationException("数组不能在这里作为整体值读取");
            }
            String tmp = freshTemp();
            emit(tmp + " = load " + address.type.llvm() + ", ptr " + address.pointer);
            return new Value(address.type, tmp);
        }
        if(ctx.expression().size() == 1){
            if(ctx.LPAREN() != null){
                return emitValue(ctx.expression(0));
            }

            // 一元+ - !
            if(ctx.PLUS() != null){
                return emitValue(ctx.expression(0));
            }
            if(ctx.MINUS() != null){
                String operand = emitValue(ctx.expression(0)).operand;
                String tmp = freshTemp();
                emit(tmp + " = sub i32 0, " + operand);
                return new Value(SplType.INT, tmp);
            }
            if(ctx.NOT() != null){
                String operand = emitValue(ctx.expression(0)).operand;
                String tmp = freshTemp();
                emit(tmp + " = icmp eq i32 " + operand + ", 0");
                String res = freshTemp();
                emit(res + " = zext i1 " + tmp + " to i32");
                return new Value(SplType.INT, res);
            }
        }

        // 赋值
        if(ctx.ASSIGN() != null){
            Address left = emitAddress(ctx.expression(0));
            if (left.type.elementType != null) {
                throw new UnsupportedOperationException("不支持数组整体赋值");
            }

            Value right = emitValue(ctx.expression(1));
            if(left.type != right.type){
                throw new IllegalStateException("赋值两边类型不一致");
            }
            emit("store " + left.type.llvm() + " " + right.operand + " , ptr " + left.pointer);
            return right;
        }

        // 逻辑
        if(ctx.AND() != null || ctx.OR() != null){
            String slot = freshTemp();
            emit(slot + " = alloca i32");
            String trueLabel = freshLable();
            String falseLabel = freshLable();
            String endLabel = freshLable();

            emitCond(ctx, trueLabel, falseLabel);
            emit(trueLabel + ":");
            emit("store i32 1, ptr " + slot);
            emit("br label %" + endLabel);

            emit(falseLabel + ":");
            emit("store i32 0, ptr " + slot);
            emit("br label %" + endLabel);

            emit(endLabel + ":");
            String result = freshTemp();
            emit(result + " = load i32, ptr " + slot);
            return new Value(SplType.INT, result);
        }
        
        if(ctx.expression().size() == 2
                && (ctx.MUL() != null || ctx.DIV() != null || ctx.MOD() != null
                    || ctx.PLUS() != null || ctx.MINUS() != null
                    || ctx.LT() != null || ctx.LE() != null
                    || ctx.GT() != null || ctx.GE() != null
                    || ctx.EQ() != null || ctx.NEQ() != null
                    || ctx.POW() != null)){
            Value leftValue = emitValue(ctx.expression(0));
            Value rightValue = emitValue(ctx.expression(1));
            if(leftValue.type != SplType.INT || rightValue.type != SplType.INT){
                throw new IllegalStateException("该运算要求整数操作数");
            }
            String left = leftValue.operand;
            String right = rightValue.operand;    
            String tmp = freshTemp();
            if(ctx.MUL() != null){
                emit(tmp + " = mul i32 " + left + " , " + right);
                return new Value(SplType.INT, tmp);
            }
            if(ctx.DIV() != null){
                emit(tmp + " = sdiv i32 " + left + " , " + right);
                return new Value(SplType.INT, tmp);
            }
            if(ctx.MOD() != null){
                emit(tmp + " = srem i32 " + left + " , " + right);
                return new Value(SplType.INT, tmp);
            }
            if(ctx.PLUS() != null){
                emit(tmp + " = add i32 " + left + " , " + right);
                return new Value(SplType.INT, tmp);
            }
            if(ctx.MINUS() != null){
                emit(tmp + " = sub i32 " + left + " , " + right);
                return new Value(SplType.INT, tmp);
            }

            // 比较
            String cmpOp = null;
            if (ctx.LT()  != null) cmpOp = "slt";
            else if (ctx.LE()  != null) cmpOp = "sle";
            else if (ctx.GT()  != null) cmpOp = "sgt";
            else if (ctx.GE()  != null) cmpOp = "sge";
            else if (ctx.EQ()  != null) cmpOp = "eq";
            else if (ctx.NEQ() != null) cmpOp = "ne";
            
            if (cmpOp != null) {
                // 比较
                emit(tmp + " = icmp " + cmpOp + " i32 " + left + ", " + right);
                // 转成 i32
                String res = freshTemp();
                emit(res + " = zext i1 " + tmp + " to i32");
                return new Value(SplType.INT, res);
            }

            // 幂值
            if(ctx.POW() != null){
                emit(tmp + " = call i32 @__spl_pow(i32 " + left + ", " + "i32 " + right + ")");
                return new Value(SplType.INT, tmp);
            }
        }

        throw new UnsupportedOperationException("未支持的表达式");
    }

    private Address emitAddress(Splc.ExpressionContext ctx){
        // 普通变量
        if(ctx.Identifier() != null && ctx.getChildCount() == 1){
            String name = ctx.Identifier().getText();
            VariableSymbol symbol = currentScope.lookup(name);
            if(symbol == null){
                throw new IllegalStateException("未找到变量：" + name);
            }
            return new Address(symbol.llvmType, symbol.address);
        }

        // 括号
        if(ctx.LPAREN() != null && ctx.expression().size() == 1){
            return emitAddress(ctx.expression(0));
        }

        // 数组下标
        if(ctx.LBRACK() != null){
            Address base = emitAddress(ctx.expression(0));
            if(base.type.elementType == null){
                throw new IllegalStateException("下标访问的对象不是数组");
            }

            Value index = emitValue(ctx.expression(1));
            if(index.type != SplType.INT){
                throw new IllegalStateException("数组下表必须是整数");
            }
            String index64 = freshTemp();
            emit(index64 + " = sext i32 " + index.operand + " to i64");
            String pointer = freshTemp();
            emit(pointer + " = getelementptr " + base.type.llvm()
                    + ", ptr " + base.pointer
                    + ", i64 0, i64 " + index64);
            return new Address(base.type.elementType, pointer);
        }

        // 结构体
        if(ctx.DOT() != null){
            Address base = emitAddress(ctx.expression(0));
            if(base.type.structName == null){
                throw new IllegalStateException("成员访问的对象不是结构体");
            }

            String name = ctx.Identifier().getText();
            int fieldIndex = base.type.structFieldNames.indexOf(name);
            if(fieldIndex < 0){
                throw new IllegalStateException("未找到结构体成员：" + name);
            }
            String tmp = freshTemp();
            emit(tmp + " = getelementptr " + base.type.llvm()
                    + ", ptr " + base.pointer
                    + ", i64 0, i32 " + fieldIndex);  
            return new Address(base.type.structFields.get(fieldIndex), tmp);
        }
        throw new UnsupportedOperationException("暂未实现左值地址计算:" + ctx.getText());
    }

    private static class FunctionSymbol {
        final SplType returnType;
        final List<SplType> parameterTypes;

        private FunctionSymbol(SplType retuSplType, List<SplType> parameterTypes){
            this.returnType = retuSplType;
            this.parameterTypes = parameterTypes;
        }
    }

    private static class Scope {
        final Scope parent;
        final Map<String, VariableSymbol> variables = new HashMap<>(); // 变量名到变量类型及相关信息的映射

        Scope(Scope parent){
            this.parent = parent;
        }

        void define(String name, VariableSymbol symbol){
            variables.put(name, symbol);
        }

        VariableSymbol lookup(String name){
            VariableSymbol symbol = variables.get(name);
            if(symbol != null){
                return symbol;
            }
            if(parent != null){
                return parent.lookup(name);
            }
            return null;
        }
    }
    private Scope currentScope;
    private final Map<String, SplType> structVariables = new HashMap<>(); // 根据结构体名找到类型对象以及字段信息
    private final Map<String, FunctionSymbol> functions = new HashMap<>(); // 函数名到函数信息的映射

    public String generate(Splc.ProgramContext tree) {
        currentScope = new Scope(null);
        structVariables.clear();
        functions.clear();
        varCount = 0;
        tempCount = 0;
        labelCount = 0;

        ir.setLength(0);
        ir.append("declare i32 @read()")
          .append("\n")
          .append("declare i32 @write(i32)")
          .append("\n")
          .append("declare i32 @__spl_pow(i32, i32)")
          .append("\n");
        visit(tree);
        return ir.toString();
    }
    @Override public Void visitProgram(Splc.ProgramContext ctx) {
        // TODO: register symbols and types; translate global declarations, then functions.
        // You can add # labeled alternatives in your own Splc.g4 to customize visitXXX.
        for(Splc.GlobalDefContext global : ctx.globalDef()){
            visit(global);
        }
        return null;
    }

    @Override 
    public Void visitStructDef(Splc.StructDefContext ctx){
        String name = ctx.Identifier().getText();
        List<SplType> list = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for(Splc.FieldDefContext field : ctx.fieldDef()){
            Splc.VariableDeclContext decl = field.variableDecl();
            list.add(resolveDeclaredType(decl));
            names.add(decl.Identifier().getText());
        }
        SplType s = SplType.struct(name, list, names);
        structVariables.put(name, s);
        StringBuilder sb = new StringBuilder();
        for(int i = 0;i < list.size();i++){
            if(i > 0) sb.append(", ");
            sb.append(list.get(i).llvm());
        }
        emit("%struct." + name + " = type { " + sb.toString() + " }");
        return null;
    }

    @Override 
    public Void visitGlobalVarDef(Splc.GlobalVarDefContext ctx){
        Splc.VariableDeclContext decl = ctx.variableDecl();

        String name = decl.Identifier().getText();
        SplType llvmType = resolveDeclaredType(decl);
        String address = "@"+name;
        VariableSymbol symbol = new VariableSymbol(llvmType, address);
        currentScope.define(name, symbol);

        emit(symbol.address + " = global " + symbol.llvmType.llvm() + " zeroinitializer");

        return null;
    }   

    @Override public Void visitFunctionDef(Splc.FunctionDefContext ctx) {
        // TODO: LLVM IR function signature, parameters, blocks and terminators.
        Scope outerScope = currentScope;
        currentScope = new Scope(outerScope);
        try{
            SplType returnType = resolveType(ctx.type());
            String name = ctx.Identifier().getText();
            List<SplType> paraList = new ArrayList<>();
            if(ctx.parameterList() != null){
                for(Splc.ParameterContext parameter : ctx.parameterList().parameter()){
                    paraList.add(resolveDeclaredType(parameter.variableDecl()));
                }
            }
            FunctionSymbol fs = new FunctionSymbol(returnType, paraList);
            functions.put(name, fs);
            
            // 参数列表
            StringBuilder sb = new StringBuilder();
            for(int i = 0;i < paraList.size();i++){
                if(i > 0) sb.append(", ");

                SplType type = paraList.get(i);
                String parameterType = type.elementType != null ? "ptr" : type.llvm();
                sb.append(parameterType).append(" %para").append(i);
            }
            emit("define " + returnType.llvm() + " @" + name + "(" + sb.toString() + ") {");

            if(ctx.parameterList() != null){ 
                for(int i = 0;i < paraList.size(); i++){
                    Splc.VariableDeclContext decl = ctx.parameterList().parameter(i).variableDecl();
                    String parameterName = decl.Identifier().getText();
                    SplType type = paraList.get(i);
                    String incoming = "%para" + i;
                    String address;

                    if(type.elementType != null){ // 数组类型，传递指针，其他都是值传递
                        address = incoming;
                    } else{
                        address = "%local." + (varCount++);
                        emit(address + " = alloca " + type.llvm());
                        emit("store " + type.llvm() + " " + incoming + " , ptr " + address);
                        // define i32 @f(i32 %para0, %struct.Point %para1, ptr %para2)
                        // %local.1 = alloca %struct.Point
                        // store %struct.Point %para1, ptr %local.1
                        // %result = load i32, ptr %local.0
                    }
                    currentScope.define(parameterName, new VariableSymbol(type, address));
                }
            }

            for(Splc.StatementContext statement : ctx.block().statement()){
                visit(statement);
            }
            emit("}");
        }finally{
            currentScope = outerScope;
        }
        
        return null;
    }

    // 作用域
    @Override public Void visitBlock(Splc.BlockContext ctx){
        // 进入新的作用域
        Scope outerScope = this.currentScope;
        currentScope = new Scope(outerScope);
        try {
            for(Splc.StatementContext statement : ctx.statement()){
                visit(statement);
            }
        } finally {
            currentScope = outerScope;
        }

        return null;
    }
    
    // 局部变量
    @Override public Void visitLocalVarDef(Splc.LocalVarDefContext ctx){
        // TODO: 待实现赋值支持
        Splc.VariableDeclContext svdc = ctx.variableDecl();
        String name = svdc.Identifier().getText();
        SplType st = resolveDeclaredType(svdc);
        String address = "%local." + (varCount++);
        VariableSymbol vs = new VariableSymbol(st, address);
        currentScope.define(name, vs);
        emit(address + " = alloca " + st.llvm());

        if(ctx.expression() != null){
            
            if (st.elementType != null) {
                throw new UnsupportedOperationException("不支持数组整体初始化");
            }

            Value value = emitValue(ctx.expression());
            if (st != value.type) {
                throw new IllegalStateException("初始化类型不一致");
            }

            emit("store " + st.llvm() + " " + value.operand + ", ptr " + address);
        }
        return null;
    }

    // 表达式
    @Override 
    public Void visitExpressionStatement(Splc.ExpressionStatementContext ctx) {
        emitValue(ctx.expression());
        return null;
    }

    private SplType resolveDeclaredType(Splc.VariableDeclContext decl){
        SplType type = resolveType(decl.type());
        for(int i = decl.arrayDimension().size() - 1;i >= 0;i--){
            String length = decl.arrayDimension(i).Number().getText();
            type = SplType.array(length, type);
        }

        return type;
    }

    // 基础类型  主要针对譬如函数声明返回的type节点
    private SplType resolveType(Splc.TypeContext tc){
        if(tc.INT() != null){
            return SplType.INT;
        }

        String name = tc.Identifier().getText();
        SplType type = structVariables.get(name);
        if(type == null){
            throw new IllegalStateException("未找到结构体类型" + name);
        }
        return type;
    }
}
