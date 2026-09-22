lexer grammar SplLexer;

LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;

INT: 'int';
STRUCT: 'struct';
RETURN: 'return';
IF: 'if';
ELSE: 'else';
WHILE: 'while';

ASSIGN: '=';
PLUS: '+';
MINUS: '-';
MUL: '*';
DIV: '/';
MOD: '%';
POW: '^';
LE: '<=';
LT: '<';
GE: '>=';
GT: '>';
EQ: '==';
NEQ: '!=';
AND: '&&';
OR: '||';
NOT: '!';
DOT: '.';

SEMI: ';';
COMMA: ',';
LPAREN: '(';
RPAREN: ')';
LBRACE: '{';
RBRACE: '}';
LBRACK: '[';
RBRACK: ']';

Identifier: [a-zA-Z_][a-zA-Z0-9_]*;

Number: '0' | [1-9][0-9]*;

WS: [ \t\r\n]+ -> skip;
