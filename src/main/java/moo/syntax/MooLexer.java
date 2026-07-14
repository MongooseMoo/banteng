package moo.syntax;

import java.util.Locale;

/** The single lexer for stored and dynamically compiled MOO source. */
final class MooLexer {
  enum TokenKind {
    EOF,
    IDENTIFIER,
    INTEGER,
    STRING,
    OBJECT,
    ERROR,
    IF,
    ELSEIF,
    ELSE,
    ENDIF,
    WHILE,
    ENDWHILE,
    FOR,
    IN,
    ENDFOR,
    TRY,
    EXCEPT,
    FINALLY,
    ENDTRY,
    RETURN,
    ANY,
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACE,
    RIGHT_BRACE,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    COMMA,
    SEMICOLON,
    DOT,
    DOLLAR,
    BACKTICK,
    APOSTROPHE,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    CARET,
    BANG,
    EQUAL,
    EQUAL_EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    AND_AND,
    OR_OR,
    FAT_ARROW
  }

  record Token(TokenKind kind, String lexeme, int line, int column) {}

  private final String source;
  private int offset;
  private int line = 1;
  private int column = 1;

  MooLexer(String source) {
    this.source = source;
  }

  Token next() {
    skipIgnored();
    int tokenLine = line;
    int tokenColumn = column;
    if (atEnd()) {
      return new Token(TokenKind.EOF, "", tokenLine, tokenColumn);
    }

    char current = advance();
    return switch (current) {
      case '(' -> token(TokenKind.LEFT_PAREN, tokenLine, tokenColumn);
      case ')' -> token(TokenKind.RIGHT_PAREN, tokenLine, tokenColumn);
      case '{' -> token(TokenKind.LEFT_BRACE, tokenLine, tokenColumn);
      case '}' -> token(TokenKind.RIGHT_BRACE, tokenLine, tokenColumn);
      case '[' -> token(TokenKind.LEFT_BRACKET, tokenLine, tokenColumn);
      case ']' -> token(TokenKind.RIGHT_BRACKET, tokenLine, tokenColumn);
      case ',' -> token(TokenKind.COMMA, tokenLine, tokenColumn);
      case ';' -> token(TokenKind.SEMICOLON, tokenLine, tokenColumn);
      case '.' -> token(TokenKind.DOT, tokenLine, tokenColumn);
      case '$' -> token(TokenKind.DOLLAR, tokenLine, tokenColumn);
      case '`' -> token(TokenKind.BACKTICK, tokenLine, tokenColumn);
      case '\'' -> token(TokenKind.APOSTROPHE, tokenLine, tokenColumn);
      case '+' -> token(TokenKind.PLUS, tokenLine, tokenColumn);
      case '-' -> token(TokenKind.MINUS, tokenLine, tokenColumn);
      case '*' -> token(TokenKind.STAR, tokenLine, tokenColumn);
      case '/' -> token(TokenKind.SLASH, tokenLine, tokenColumn);
      case '%' -> token(TokenKind.PERCENT, tokenLine, tokenColumn);
      case '^' -> token(TokenKind.CARET, tokenLine, tokenColumn);
      case '!' -> token(match('=') ? TokenKind.NOT_EQUAL : TokenKind.BANG, tokenLine, tokenColumn);
      case '=' ->
          token(
              match('=')
                  ? TokenKind.EQUAL_EQUAL
                  : match('>') ? TokenKind.FAT_ARROW : TokenKind.EQUAL,
              tokenLine,
              tokenColumn);
      case '<' ->
          token(
              match('=') ? TokenKind.LESS_THAN_OR_EQUAL : TokenKind.LESS_THAN,
              tokenLine,
              tokenColumn);
      case '>' ->
          token(
              match('=') ? TokenKind.GREATER_THAN_OR_EQUAL : TokenKind.GREATER_THAN,
              tokenLine,
              tokenColumn);
      case '&' -> {
        require('&', tokenLine, tokenColumn);
        yield token(TokenKind.AND_AND, tokenLine, tokenColumn);
      }
      case '|' -> {
        require('|', tokenLine, tokenColumn);
        yield token(TokenKind.OR_OR, tokenLine, tokenColumn);
      }
      case '"' -> string(tokenLine, tokenColumn);
      case '#' -> object(tokenLine, tokenColumn);
      default -> {
        if (isDigit(current)) {
          yield integer(tokenLine, tokenColumn);
        }
        if (isIdentifierStart(current)) {
          yield identifier(tokenLine, tokenColumn);
        }
        throw error(tokenLine, tokenColumn, "unexpected character '" + current + "'");
      }
    };
  }

  private Token integer(int tokenLine, int tokenColumn) {
    while (!atEnd() && isDigit(peek())) {
      advance();
    }
    return token(TokenKind.INTEGER, tokenLine, tokenColumn);
  }

  private Token object(int tokenLine, int tokenColumn) {
    if (!atEnd() && peek() == '-') {
      advance();
    }
    int digitStart = offset;
    while (!atEnd() && isDigit(peek())) {
      advance();
    }
    if (digitStart == offset) {
      throw error(tokenLine, tokenColumn, "object literal requires an object number");
    }
    return token(TokenKind.OBJECT, tokenLine, tokenColumn);
  }

  private Token string(int tokenLine, int tokenColumn) {
    StringBuilder decoded = new StringBuilder();
    while (!atEnd() && peek() != '"') {
      char current = advance();
      if (current == '\\') {
        if (atEnd()) {
          throw error(tokenLine, tokenColumn, "unterminated string literal");
        }
        current = advance();
      }
      decoded.append(current);
    }
    if (atEnd()) {
      throw error(tokenLine, tokenColumn, "unterminated string literal");
    }
    advance();
    return new Token(TokenKind.STRING, decoded.toString(), tokenLine, tokenColumn);
  }

  private Token identifier(int tokenLine, int tokenColumn) {
    while (!atEnd() && isIdentifierPart(peek())) {
      advance();
    }
    String lexeme = source.substring(tokenStart(tokenLine, tokenColumn), offset);
    String normalized = lexeme.toLowerCase(Locale.ROOT);
    TokenKind kind =
        switch (normalized) {
          case "if" -> TokenKind.IF;
          case "elseif" -> TokenKind.ELSEIF;
          case "else" -> TokenKind.ELSE;
          case "endif" -> TokenKind.ENDIF;
          case "while" -> TokenKind.WHILE;
          case "endwhile" -> TokenKind.ENDWHILE;
          case "for" -> TokenKind.FOR;
          case "in" -> TokenKind.IN;
          case "endfor" -> TokenKind.ENDFOR;
          case "try" -> TokenKind.TRY;
          case "except" -> TokenKind.EXCEPT;
          case "finally" -> TokenKind.FINALLY;
          case "endtry" -> TokenKind.ENDTRY;
          case "return" -> TokenKind.RETURN;
          case "any" -> TokenKind.ANY;
          default -> lexeme.startsWith("E_") ? TokenKind.ERROR : TokenKind.IDENTIFIER;
        };
    return new Token(kind, lexeme, tokenLine, tokenColumn);
  }

  private void skipIgnored() {
    boolean skipped;
    do {
      skipped = false;
      while (!atEnd() && Character.isWhitespace(peek())) {
        advance();
        skipped = true;
      }
      if (!atEnd() && peek() == '/' && peekNext() == '/') {
        while (!atEnd() && peek() != '\n') {
          advance();
        }
        skipped = true;
      }
    } while (skipped);
  }

  private Token token(TokenKind kind, int tokenLine, int tokenColumn) {
    return new Token(
        kind, source.substring(tokenStart(tokenLine, tokenColumn), offset), tokenLine, tokenColumn);
  }

  private int tokenStart(int tokenLine, int tokenColumn) {
    if (tokenLine == line) {
      return offset - (column - tokenColumn);
    }
    throw error(tokenLine, tokenColumn, "tokens may not cross source lines");
  }

  private void require(char expected, int tokenLine, int tokenColumn) {
    if (!match(expected)) {
      throw error(tokenLine, tokenColumn, "expected '" + expected + "'");
    }
  }

  private boolean match(char expected) {
    if (atEnd() || peek() != expected) {
      return false;
    }
    advance();
    return true;
  }

  private char advance() {
    char current = source.charAt(offset++);
    if (current == '\n') {
      line++;
      column = 1;
    } else {
      column++;
    }
    return current;
  }

  private char peek() {
    return source.charAt(offset);
  }

  private char peekNext() {
    return offset + 1 < source.length() ? source.charAt(offset + 1) : '\0';
  }

  private boolean atEnd() {
    return offset >= source.length();
  }

  private static boolean isDigit(char value) {
    return value >= '0' && value <= '9';
  }

  private static boolean isIdentifierStart(char value) {
    return Character.isLetter(value) || value == '_';
  }

  private static boolean isIdentifierPart(char value) {
    return isIdentifierStart(value) || isDigit(value);
  }

  private static MooParser.ParseException error(int line, int column, String message) {
    return new MooParser.ParseException(line, column, message);
  }
}
