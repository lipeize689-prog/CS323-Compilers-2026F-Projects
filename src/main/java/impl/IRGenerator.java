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
    private Value emitValue(Splc.ExpressionContext ctx){
        // TODO
        if(ctx.Number() != null){
            return new Value(SplType.INT, ctx.Number().getText());
        }
        if(ctx.expression().size() == 2)
        throw new UnsupportedOperationException("未支持的表达式");
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
            // TODO
            if (st != SplType.INT) {
                throw new UnsupportedOperationException("暂未支持非整数局部初始化");
            }

            Value value = emitValue(ctx.expression());
            if (value.type != SplType.INT) {
                throw new UnsupportedOperationException("暂未支持非整数初始化值");
            }

            emit("store i32 " + value.operand + ", ptr " + address);
        }
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
