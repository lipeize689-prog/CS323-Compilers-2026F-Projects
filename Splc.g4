parser grammar Splc;
options { tokenVocab=SplLexer; }
program: globalDef* EOF;
globalDef: structDef | functionDef | globalVarDef;
structDef: STRUCT Identifier LBRACE fieldDef* RBRACE SEMI;
fieldDef: variableDecl SEMI;
globalVarDef: variableDecl SEMI;
functionDef: type Identifier LPAREN parameterList? RPAREN block;
parameterList: parameter (COMMA parameter)*;
parameter: variableDecl;
type: INT | STRUCT Identifier;
variableDecl: type Identifier arrayDimension*;
arrayDimension: LBRACK Number RBRACK;
block: LBRACE statement* RBRACE;
statement: block | localVarDef | ifStatement | whileStatement | returnStatement | expressionStatement;
localVarDef: variableDecl (ASSIGN expression)? SEMI;
ifStatement: IF LPAREN expression RPAREN block (ELSE block)?;
whileStatement: WHILE LPAREN expression RPAREN block;
returnStatement: RETURN expression SEMI;
expressionStatement: expression SEMI;
argumentList: expression (COMMA expression)*;
expression
 : Number
 | Identifier LPAREN argumentList? RPAREN
 | Identifier
 | LPAREN expression RPAREN
 | expression LBRACK expression RBRACK
 | expression DOT Identifier
 | <assoc=right> expression POW expression
 | (PLUS | MINUS | NOT) expression
 | expression (MUL | DIV | MOD) expression
 | expression (PLUS | MINUS) expression
 | expression (LT | LE | GT | GE) expression
 | expression (EQ | NEQ) expression
 | expression AND expression
 | expression OR expression
 | <assoc=right> expression ASSIGN expression
 ;
