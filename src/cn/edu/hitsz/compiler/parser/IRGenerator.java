package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
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
     * 三地址码列表
     */
    List<Instruction> instructions = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO
        var tokenKind = currentToken.getKind().getIdentifier();
        if (tokenKind.equals("IntConst")) {
            // 若是IntConst类终结符，则压入其字面值
            var val = Integer.parseInt(currentToken.getText());
            synStk.push(IRImmediate.of(val));
        } else if (tokenKind.equals("id")) {
            // 若是id类终结符，则检查符号表中是否存在记录
            // 若存在，则压入变量标识符
            var val = currentToken.getText();
            if (!table.has(val)) {
                // TODO: 错误处理
                throw new RuntimeException("未在符号表中登记的标识符: " + val);
            }
            synStk.push(IRVariable.named(val));
        } else {
            // 若不是上述两类终结符，则压入空记录
            synStk.push(new IRNullValue());
        }
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO
        switch (production.index()) {
            case 6 -> { // S -> id = E
                // 弹栈并获得E.val
                var eVal = synStk.pop(); // E
                synStk.pop(); // =
                var idVal = synStk.pop(); // id
                // gencode(id.val = E.val);
                instructions.add(Instruction.createMov((IRVariable) idVal, eVal));
                // S无需val属性，压入空记录占位
                synStk.push(new IRNullValue()); // S
            }
            case 7 -> { // S -> return E
                var val = synStk.pop(); // E
                synStk.pop(); // return
                // gencode(return E.val);
                instructions.add(Instruction.createRet(val));
                // S无需val属性，压入空记录占位
                synStk.push(new IRNullValue()); // S
            }
            case 8 -> { // E1 -> E2 + A
                var aVal = synStk.pop(); // A
                synStk.pop(); // +
                var e2Val = synStk.pop(); // E2
                // E1.val = newtemp();
                var e1Val = IRVariable.temp();
                synStk.push(e1Val); // E1
                // gencode(E1.val = E2.val + A.val);
                instructions.add(Instruction.createAdd(e1Val, e2Val, aVal));
            }
            case 9 -> { // E1 -> E2 – A
                var aVal = synStk.pop(); // A
                synStk.pop(); // -
                var e2Val = synStk.pop(); // E2
                // E1.val = newtemp();
                var e1Val = IRVariable.temp();
                synStk.push(e1Val); // E1
                // gencode(E1.val = E2.val - A.val);
                instructions.add(Instruction.createSub(e1Val, e2Val, aVal));
            }
            case 10 -> { // E -> A
                // 弹栈，获取值信息，再将同样的值入栈。操作前后栈不变。
                // E.val = A.val;
                // do nothing
            }
            case 11 -> { // A1 -> A2 * B
                var bVal = synStk.pop(); // B
                synStk.pop(); // *
                var a2Val = synStk.pop(); // A2
                // A1.val = newtemp();
                var a1Val = IRVariable.temp();
                synStk.push(a1Val);
                // gencode(A1.val = A2.val * B.val);
                instructions.add(Instruction.createMul(a1Val, a2Val, bVal));
            }
            case 12 -> { // A -> B
                // 弹栈，获取值信息，再将同样的值入栈。操作前后栈不变。
                // A.val = B.val
                // do nothing
            }
            case 13 -> { // B -> ( E )
                // 弹栈，获取值信息，再将同样的值入栈。
                synStk.pop(); // (
                var eVal = synStk.pop(); // E
                synStk.pop(); // )
                // B.val = E.val;
                synStk.push(eVal); // B
            }
            case 14 -> { // B -> id
                // 弹栈，获取值信息，再将同样的值入栈。操作前后栈不变。
                // B.val = lookup(id.name);
                // do nothing
            }
            case 15 -> { // B -> IntConst
                // 弹栈，获取值信息，再将同样的值入栈。操作前后栈不变。
                // B.val = IntConst.lexval;
                // do nothing
            }
            default -> {
                // 其他产生式均不涉及类型信息,直接根据符号数量出入栈即可
                var symbolCnt = production.body().size();
                while (symbolCnt-- != 0) {
                    synStk.pop();
                }
                synStk.push(new IRNullValue());
            }
        }
    }


    @Override
    public void whenAccept(Status currentStatus) {
        // TODO
        // 代表开始产生式归约,不需要执行特殊动作
        // 语法制导翻译结束,所以也没有必要再去更新栈了
        // do nothing
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO
//        throw new NotImplementedException();
        this.table = table;
    }

    public List<Instruction> getIR() {
        // TODO
        return instructions;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }

    /**
     * 综合属性值的空记录,用于在栈中占位
     */
    static class IRNullValue implements IRValue {
    }

}

