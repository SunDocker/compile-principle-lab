package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayDeque;
import java.util.Deque;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {

    /**
     * 符号表
     */
    SymbolTable table;

    /**
     * 综合属性栈，负责记录类型信息和标识符名
     */
    Deque<Object> synStk = new ArrayDeque<>();

    @Override
    public void whenAccept(Status currentStatus) {
        // TODO: 该过程在遇到 Accept 时要采取的代码动作
        // 代表开始产生式归约,不需要执行特殊动作
        // 语法制导翻译结束,所以也没有必要再去更新栈了
        // do nothing
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO: 该过程在遇到 reduce production 时要采取的代码动作
//        throw new NotImplementedException();
        // 执行除生成三地址码以外的语义动作
        // 根据产生式执行对应动作
        switch (production.index()) {
            case 4 -> { // S -> D id
                // 获取标识符名字
                // 产生式右部弹栈，并从栈中获取非终结符D的类型
                var idName = synStk.pop(); // id
                var type = synStk.pop(); // D
                // 更新符号表中的类型信息
                table.get((String) idName).setType((SourceCodeType) type);
                // 左部无需类型信息，压入空记录占位
                synStk.push(SourceCodeType.Null); // S
            }
            case 5 -> { // D -> int
                // 弹栈，获取类型信息，然后设置左部符号属性并将其压栈
                // 该过程执行前后综合属性栈没有变化，所以不需要执行更新栈的代码
                // do nothing
            }
            default -> {
                // 其他产生式均不涉及类型信息,直接根据符号数量出入栈即可
                var symbolCnt = production.body().size();
                while (symbolCnt-- != 0) {
                    synStk.pop();
                }
                synStk.push(SourceCodeType.Null);
            }
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO: 该过程在遇到 shift 时要采取的代码动作
        var tokenKind = currentToken.getKind().getIdentifier();
        if (tokenKind.equals("int")) {
            // 若是类型关键字终结符，则压入类型信息
            synStk.push(SourceCodeType.Int);
        } else if (tokenKind.equals("id")) {
            // 若是id类终结符，则检查符号表中是否存在记录
            // 若存在，则压入变量标识符
            var val = currentToken.getText();
            if (!table.has(val)) {
                // TODO: 错误处理
                throw new RuntimeException("未在符号表中登记的标识符: " + val);
            }
            synStk.push(val);
        } else {
            // 若不是类型关键字终结符，则压入空记录
            synStk.push(SourceCodeType.Null);
        }
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO: 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        this.table = table;
    }
}

