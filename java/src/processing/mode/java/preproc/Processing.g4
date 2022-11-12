/**
 *  Based on Java 1.7 grammar for ANTLR 4, see Java.g4
 *
 *  - changes main entry point to reflect sketch types 'static' | 'active'
 *  - adds support for type converter functions like "int()"
 *  - adds pseudo primitive type "color"
 *  - adds HTML hex notation with hash symbol: #ff5522
 *     - allow color to appear as part of qualified names (like in imports)
 */

grammar Processing;

@lexer::members {
  public static final int WHITESPACE = 1;
  public static final int COMMENTS = 2;
}

// import Java grammar
import JavaParser;

// main entry point, select sketch type
processingSketch
    : staticProcessingSketch
    | javaProcessingSketch
    | activeProcessingSketch
//    | warnMixedModes
    ;

// java mode, is a compilation unit
javaProcessingSketch
    : packageDeclaration? importDeclaration* typeDeclaration+ EOF
    ;

// No method declarations, just statements
staticProcessingSketch
    : (importDeclaration | blockStatement | typeDeclaration)* EOF
    ;

// active mode, has function definitions
activeProcessingSketch
    : (importDeclaration | classBodyDeclaration)* EOF
    ;

// User incorrectly mixing modes. Included to allow for kind error message.
warnMixedModes
    : (importDeclaration | classBodyDeclaration | blockStatement)* blockStatement classBodyDeclaration (importDeclaration | classBodyDeclaration | blockStatement)*
    | (importDeclaration | classBodyDeclaration | blockStatement)* classBodyDeclaration blockStatement (importDeclaration | classBodyDeclaration | blockStatement)*
    ;

variableDeclaratorId
    : warnTypeAsVariableName
    | IDENTIFIER ('[' ']')*
    ;

// bug #93
// https://github.com/processing/processing/issues/93
// prevent from types being used as variable names
warnTypeAsVariableName
    : primitiveType ('[' ']')* {
        notifyErrorListeners("Type names are not allowed as variable names: "+$primitiveType.text);
      }
    ;

// catch special API function calls that we are interested in
methodCall
    : functionWithPrimitiveTypeName
    | IDENTIFIER '(' expressionList? ')'
    | THIS '(' expressionList? ')'
    | SUPER '(' expressionList? ')'
    ;

// these are primitive type names plus "()"
// "color" is a special Processing primitive (== int)
functionWithPrimitiveTypeName
    : (  'boolean'
      |  'byte'
      |  'char'
      |  'float'
      |  'int'
      |  'color'
      ) '(' expressionList? ')'
    ;

// adding support for "color" primitive
primitiveType
    : BOOLEAN
    | CHAR
    | BYTE
    | SHORT
    | INT
    | LONG
    | FLOAT
    | DOUBLE
    | colorPrimitiveType
    ;

colorPrimitiveType
    : 'color'
    ;

qualifiedName
    : (IDENTIFIER | colorPrimitiveType) ('.' (IDENTIFIER | colorPrimitiveType))*
    ;

// added HexColorLiteral
literal
    : integerLiteral
    | floatLiteral
    | CHAR_LITERAL
    | stringLiteral
    | BOOL_LITERAL
    | NULL_LITERAL
    | hexColorLiteral
    ;

// As parser rule so this produces a separate listener
// for us to alter its value.
hexColorLiteral
    : HexColorLiteral
    ;

// add color literal notations for
// #ff5522
HexColorLiteral
    : '#' (HexDigit HexDigit)? HexDigit HexDigit HexDigit HexDigit HexDigit HexDigit
    ;


// hide but do not remove whitespace and comments

WS  : [ \t\r\n\u000C]+ -> channel(1)
    ;

COMMENT
    : '/*' .*? '*/' -> channel(2)
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> channel(2)
    ;

CHAR_LITERAL
    : '\'' (~['\\\r\n] | EscapeSequence)* '\''  // A bit nasty but let JDT tackle invalid chars
		;
