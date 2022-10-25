package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {

    /**
     * 符号表
     */
    SymbolTable table;

    /**
     * 综合属性栈，只负责记录IR值信息
     */
    Deque<IRValue> synStk = new ArrayDeque<>();

    /**
     * 三地址码列表j
     */
    List<Instruction> instructions = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO
        if (currentToken.getKind().getIdentifier().equals("IntConst")) {
            // 若是IntConst类终结符，则压入其字面值
            var val = Integer.parseInt(currentToken.getText());
            synStk.push(IRImmediate.of(val));
        } else {
            // 若不是IntConst类终结符，则压入空记录
            synStk.push(SourceCodeType.Null);
        }
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO
//        throw new NotImplementedException();
        switch (production.index()) {

            case 6 -> { // S -> id = E
                // 获取标识符名字
                var idName = production.head().getTermName();
                // 判断符号表中是否有此标识符
                if (table.has(idName)) {

                } else {
                    // TODO: 错误处理
                    throw new RuntimeException("未在符号表中登记的标识符");
                }

            }
        }
    }


    @Override
    public void whenAccept(Status currentStatus) {
        // TODO
//        throw new NotImplementedException();
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO
//        throw new NotImplementedException();
        this.table = table;
    }

    public List<Instruction> getIR() {
        // TODO
        throw new NotImplementedException();
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}

