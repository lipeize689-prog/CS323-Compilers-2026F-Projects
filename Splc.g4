grammar Splc;

@parser::members {
    private framework.project2.ErrorReporter errorReporter;

    public void setErrorReporter(framework.project2.ErrorReporter reporter) {
        this.errorReporter = reporter;
    }

    /**
     * Report a grammar-specific missing-token error.
     *
     * line is already the 0-indexed line required by Project 2.
     */
    protected void reportMissing(int tokenType, int line) {
        if (errorReporter != null) {
            errorReporter.reportMissing(tokenType, line);
        }
    }

    /**
     * Return the 0-indexed line of the parser's current lookahead token.
     */
    protected int currentLine() {
        return Math.max(0, getCurrentToken().getLine() - 1);
    }
}


/* ==================== Program ==================== */

program
    : globalDef* EOF
    ;

globalDef
    : structDef
    | functionDef
    | globalVarDef
    ;


/* ==================== Type ==================== */

type
    : INT
    | STRUCT Identifier
    ;


/* ==================== Struct ==================== */

structDef
    : STRUCT Identifier
      LBRACE fieldDef+
      RBRACE SEMI
    ;

fieldDef
    : variableDecl SEMI
    ;


/* ==================== Variable ==================== */

variableDecl
    : type Identifier arrayDimension*
    ;

arrayDimension
    : LBRACK Number RBRACK
    ;

globalVarDef
    : variableDecl SEMI
    ;

localVarDef
    : variableDecl (ASSIGN expression)? SEMI
    ;


/* ==================== Function ==================== */

functionDef
    : type Identifier
      LPAREN parameterList? RPAREN
      block
    ;

/*
 * TODO (Error Production):
 *
 * Project 2 contains one grammar-specific recovery case in parameterList
 * that cannot be diagnosed as the required Missing COMMA reliably by the
 * generic recovery strategy alone.  Extend this rule according to the spec.
 */
parameterList
    : parameter (COMMA parameter)*
    | parameter parameter
      { reportMissing(SplcParser.COMMA, currentLine()); }
    ;
parameter
    : variableDecl
    ;


/* ==================== Statement ==================== */

block
    : LBRACE statement* RBRACE
    ;

statement
    : block
    | localVarDef
    | ifStatement
    | whileStatement
    | returnStatement
    | expressionStatement
    ;

ifStatement
    : IF LPAREN expression RPAREN
      block
      (ELSE block)?
    ;

whileStatement
    : WHILE LPAREN expression RPAREN
      block
    ;

returnStatement
    : RETURN expression SEMI
    ;

expressionStatement
    : expression SEMI
    ;


/* ==================== Expression ==================== */

/*
 * TODO:
 * Complete the expression rule according to the Project 2 specification.
 *
 * The temporary Number-only rule keeps the starter grammar compilable.
 */
expression
    // 函数调用、数组访问、成员访问
    : Identifier LPAREN argumentList? RPAREN
    | expression LBRACK expression RBRACK
    | expression DOT Identifier

    // POW 右结合
    | <assoc=right> expression POW expression

    // 一元 PLUS MINUS NOT
    | PLUS expression
    | MINUS expression
    | NOT expression

    // MUL DIV MOD
    | expression MUL expression
    | expression DIV expression
    | expression MOD expression

    // PLUS MINUS
    | expression PLUS expression
    | expression MINUS expression

    // LT LE GT GE
    | expression LT expression
    | expression LE expression
    | expression GT expression
    | expression GE expression

    // EQ NEQ
    | expression EQ expression
    | expression NEQ expression

    // AND
    | expression AND expression

    // OR
    | expression OR expression

    // ASSIGN 右结合
    | <assoc=right> expression ASSIGN expression

    // 基本形式
    | Number
    | Identifier
    | LPAREN expression RPAREN
    ;
argumentList
    : expression (COMMA expression)*
    ;


/* ==================== Lexer ==================== */

INT : 'int';
STRUCT : 'struct';
IF : 'if';
ELSE : 'else';
WHILE : 'while';
RETURN : 'return';

LE : '<=';
GE : '>=';
EQ : '==';
NEQ : '!=';
AND : '&&';
OR : '||';
ASSIGN : '=';
PLUS : '+';
MINUS : '-';
MUL : '*';
DIV : '/';
MOD : '%';
POW : '^';
LT : '<';
GT : '>';
NOT : '!';

LPAREN : '(';
RPAREN : ')';
LBRACE : '{';
RBRACE : '}';
LBRACK : '[';
RBRACK : ']';
SEMI : ';';
COMMA : ',';
DOT : '.';

Number
    : '0'
    | [1-9] [0-9]*
    ;

Identifier
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
