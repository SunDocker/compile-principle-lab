package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private final StringBuilder codeWords;
    private final List<String> keyWords;

    List<Token> tokens;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.codeWords = new StringBuilder();
        this.keyWords = Arrays.asList("int", "return");
        this.tokens = new ArrayList<>();
    }


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // TODO: 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
        try (BufferedReader inputCode = new BufferedReader(new FileReader(path))) {
            String codeLine;
            while (Objects.nonNull(codeLine = inputCode.readLine())) {
                codeWords.append(codeLine);
            }
            codeWords.append('$');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // TODO: 自动机实现的词法分析过程
//        throw new NotImplementedException();
        var curState = State.Initial;
        var readChars = new StringBuilder();

        for (int idx = 0; idx < codeWords.length(); idx++) {
            var character = codeWords.charAt(idx);
            var halt = false;

            curState = switch (curState) {
                case Initial -> switch (character) {
                    case '*' -> State.Star;
                    case '=' -> State.Equal;
                    case '(' -> State.LeftBracket;
                    case ')' -> State.RightBracket;
                    case '+' -> State.Plus;
                    case '-' -> State.Minus;
                    case '/' -> State.Division;
                    case ';' -> State.Semicolon;
                    case ',' -> State.Comma;
                    default -> {
                        if (Character.isLetter(character)) yield State.ID;
                        else if (Character.isDigit(character)) yield State.IntConst;
                        else if (isBlank(character)) yield curState;
                        else throw new LexicalAnalyzeException("不合法的符号");
                    }
                };
                case ID -> {
                    if (Character.isLetter(character) || Character.isDigit(character)) yield curState;
                    else {
                        var identifierText = readChars.toString().strip();
                        if (keyWords.contains(identifierText)) {
                            tokens.add(Token.simple(identifierText));
                        } else {
                            tokens.add(Token.normal("id", identifierText));
                            if (!symbolTable.has(identifierText)) {
                                symbolTable.add(identifierText);
                            }
                        }
                        halt = true;
                        yield State.Initial;
                    }
                }
                case IntConst -> {
                    if (Character.isDigit(character)) yield curState;
                    else {
                        tokens.add(Token.normal("IntConst", readChars.toString().strip()));
                        halt = true;
                        yield State.Initial;
                    }
                }
                case Star, Equal, LeftBracket
                        , RightBracket, Plus
                        , Minus, Division
                        , Semicolon, Comma -> {
                    var symbol = readChars.toString().strip();
                    if (";".equals(symbol)) tokens.add(Token.simple("Semicolon"));
                    else tokens.add(Token.simple(symbol));
                    halt = true;
                    yield State.Initial;
                }
            };
            if (halt) {
                readChars.delete(0, readChars.length());
                if (character == '$') tokens.add(Token.eof());
                else idx--;
            } else {
                readChars.append(character);
            }
        }


    }

    private boolean isBlank(char character) {
        // \n已经在读取文件时忽略掉（暂不处理\n与\r\n的差异问题）
        return Arrays.asList(' ', '\t', '\r').contains(character);
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // TODO: 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
//        throw new NotImplementedException();
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
                path,
                StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }

    /**
     * 自动机的状态
     */
    enum State {
        Initial,
        ID,
        IntConst,
        Star,
        Equal,
        LeftBracket,
        RightBracket,
        Plus,
        Minus,
        Division,
        Semicolon,
        Comma
    }

    /**
     * 词法分析阶段的异常
     */
    static class LexicalAnalyzeException extends RuntimeException {
        public LexicalAnalyzeException() {
            super();
        }

        public LexicalAnalyzeException(String message) {
            super(message);
        }
    }


}
