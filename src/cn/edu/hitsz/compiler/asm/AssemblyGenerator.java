package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {

    List<Instruction> preprocessedInstructions;
    Deque<String> riscFreeRegs;
    Map<IRVariable, Integer> varLastUse;
    Map<IRVariable, String> varRegMap;
    List<String> riscInstructions;

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        // TODO: 读入前端提供的中间代码并生成所需要的信息
        Integer insCnt = 0;
        for (var ins : originInstructions) {
            var insKind = ins.getKind();
            if (insKind.isBinary()) {
                // 两个操作数的指令
                var lhs = ins.getLHS();
                var rhs = ins.getRHS();
                IRVariable res = ins.getResult();
                if (lhs instanceof IRImmediate immLhs && rhs instanceof IRImmediate immRhs) {
                    // 操作两个立即数的情况
                    final var lhsVal = immLhs.getValue();
                    final var rhsVal = immRhs.getValue();
                    IRVariable irVar = IRVariable.temp();
                    switch (insKind) {
                        case ADD -> preprocessedInstructions.add(Instruction.createMov(irVar, IRImmediate.of(lhsVal + rhsVal)));
                        case SUB -> preprocessedInstructions.add(Instruction.createMov(irVar, IRImmediate.of(lhsVal - rhsVal)));
                        case MUL -> preprocessedInstructions.add(Instruction.createMov(irVar, IRImmediate.of(lhsVal * rhsVal)));
                    }
                    varLastUse.put(irVar, ++insCnt);
                } else if (lhs instanceof IRImmediate || rhs instanceof IRImmediate) {
                    // 操作一个立即数的情况
                    if (insKind.equals(InstructionKind.MUL)) {
                        // 一个立即数的乘法
                        var irVar = IRVariable.temp();
                        insCnt += 2;
                        if (lhs instanceof IRImmediate immLhs) {
                            preprocessedInstructions.add(Instruction.createMov(irVar, IRImmediate.of(immLhs.getValue())));
                            preprocessedInstructions.add(Instruction.createMul(res, irVar, rhs));
                            varLastUse.put((IRVariable) rhs, insCnt);
                        } else {
                            IRImmediate immRhs = (IRImmediate) rhs;
                            preprocessedInstructions.add(Instruction.createMov(irVar, IRImmediate.of(immRhs.getValue())));
                            preprocessedInstructions.add(Instruction.createMul(res, lhs, irVar));
                            varLastUse.put((IRVariable) lhs, insCnt);
                        }
                        varLastUse.put(irVar, insCnt);
                        varLastUse.put(res, insCnt);
                    }
                    else if ((lhs instanceof IRImmediate immLhs && insKind.equals(InstructionKind.SUB))) {
                        // 左立即数减法
                        var irVar = IRVariable.temp();
                        insCnt += 2;
                        preprocessedInstructions.add(Instruction.createMov(irVar, IRImmediate.of(immLhs.getValue())));
                        preprocessedInstructions.add(Instruction.createSub(res, irVar, rhs));
                        varLastUse.put((IRVariable) rhs, insCnt);
                        varLastUse.put(irVar, insCnt);
                        varLastUse.put(res, insCnt);
                    } else {
                        insCnt++;
                        switch (insKind) {
                            case ADD -> {
                                if (lhs instanceof IRImmediate immLhs) {
                                    preprocessedInstructions.add(Instruction.createAdd(res, rhs, IRImmediate.of(immLhs.getValue())));
                                    varLastUse.put((IRVariable) rhs, insCnt);
                                } else {
                                    preprocessedInstructions.add(ins);
                                    varLastUse.put((IRVariable) lhs, insCnt);
                                }
                            }
                            case SUB -> {
                                preprocessedInstructions.add(ins);
                                varLastUse.put((IRVariable) lhs, insCnt);
                            }
                        }
                        varLastUse.put(res, insCnt);
                    }
                } else {
                    // 没有立即数的情况
                    insCnt++;
                    preprocessedInstructions.add(ins);
                    varLastUse.put((IRVariable) rhs, insCnt);
                    varLastUse.put((IRVariable) lhs, insCnt);
                    varLastUse.put(res, insCnt);
                }
            } else {
                // 一个操作数的指令
                preprocessedInstructions.add(ins);
                insCnt++;
                // 这里不使用switch是为了遇到return直接break出for循环
                if (insKind.equals(InstructionKind.RET)) {
                    var ret = ins.getReturnValue();
                    if (ret.isIRVariable()) {
                        varLastUse.put((IRVariable) ret, insCnt);
                    }
                    break;
                } else if (insKind.equals(InstructionKind.MOV)) {
                    var fromVal = ins.getFrom();
                    if (fromVal.isIRVariable()) {
                        varLastUse.put((IRVariable) fromVal, insCnt);
                        varLastUse.put(ins.getResult(), insCnt);
                    }
                }
            }
        }
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // TODO: 执行寄存器分配与代码生成
        Integer insCnt = 0;
        for (var ins : preprocessedInstructions) {
            insCnt++;
            var insKind = ins.getKind();
            var riscIns = new StringBuilder();
            switch (insKind) {
                case ADD -> {
                    var lhs = ins.getLHS();
                    var rhs = ins.getRHS();
                    if (rhs.isImmediate()) {
                        riscIns.append("addi ")
                                .append(getReg(insCnt, ins.getResult()))
                                .append(", ")
                                .append(getReg(insCnt, lhs))
                                .append(", ")
                                .append(rhs);
                        riscInstructions.add(riscIns.toString());
                    } else {
                        riscIns.append("add ")
                                .append(getReg(insCnt, ins.getResult()))
                                .append(", ")
                                .append(getReg(insCnt, lhs))
                                .append(", ")
                                .append(getReg(insCnt, rhs));
                        riscInstructions.add(riscIns.toString());
                    }
                }
                case SUB -> {
                    var lhs = ins.getLHS();
                    var rhs = ins.getRHS();
                    if (rhs.isImmediate()) {
                        riscIns.append("subi ")
                                .append(getReg(insCnt, ins.getResult()))
                                .append(", ")
                                .append(getReg(insCnt, lhs))
                                .append(", ")
                                .append(rhs);
                        riscInstructions.add(riscIns.toString());
                    } else {
                        riscIns.append("sub ")
                                .append(getReg(insCnt, ins.getResult()))
                                .append(", ")
                                .append(getReg(insCnt, lhs))
                                .append(", ")
                                .append(getReg(insCnt, rhs));
                        riscInstructions.add(riscIns.toString());
                    }
                }
                case MUL -> {
                    riscIns.append("mul ")
                            .append(getReg(insCnt, ins.getResult()))
                            .append(", ")
                            .append(getReg(insCnt, ins.getRHS()))
                            .append(", ")
                            .append(getReg(insCnt, ins.getLHS()));
                    riscInstructions.add(riscIns.toString());
                }
                case MOV -> {
                    var fromVal = ins.getFrom();
                    if (fromVal.isImmediate()) {
                        riscIns.append("li ")
                                .append(getReg(insCnt, ins.getResult()))
                                .append(", ")
                                .append(fromVal);
                        riscInstructions.add(riscIns.toString());
                    } else {
                        riscIns.append("mv ")
                                .append(getReg(insCnt, ins.getResult()))
                                .append(", ")
                                .append(getReg(insCnt, fromVal));
                        riscInstructions.add(riscIns.toString());
                    }
                }
                case RET -> {
                    var retVal = ins.getReturnValue();
                    riscIns.append("mv a0")
                            .append(", ");
                    if (retVal.isImmediate()) {
                        riscIns.append(retVal);
                    } else {
                        riscIns.append(getReg(insCnt, retVal));
                    }
                    riscInstructions.add(riscIns.toString());
                }
            }
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        // TODO: 输出汇编代码到文件
        try (var bw = new BufferedWriter(new FileWriter(path))) {
            bw.write(".text\n");
            for (var ins : riscInstructions) {
                bw.write('\t' + ins + '\n');
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public AssemblyGenerator() {
        preprocessedInstructions = new ArrayList<>();
        riscFreeRegs = new ArrayDeque<>(Arrays.asList("t0", "t1", "t2", "t3", "t4", "t5", "t6"));
        varRegMap = new HashMap<>();
        varLastUse = new HashMap<>();
        riscInstructions = new ArrayList<>();
    }

    private String getReg(Integer insCnt, IRValue irVal) {
        if (irVal instanceof IRVariable irVar) {
            if (varRegMap.containsKey(irVar)) {
                return varRegMap.get(irVar);
            }
            if (riscFreeRegs.isEmpty()) {
                for (var keyVar : varRegMap.keySet()) {
                    if (varLastUse.get(keyVar) < insCnt) {
                        String reg = varRegMap.get(keyVar);
                        varRegMap.remove(keyVar);
                        varRegMap.put(irVar, reg);
                        return reg;
                    }
                }
                throw new RuntimeException("第" + insCnt + "指令的寄存器不足");
            } else {
                String reg = riscFreeRegs.poll();
                varRegMap.put(irVar, reg);
                return reg;
            }
        }
        throw new RuntimeException("不需要为立即数分配寄存器");
    }

}

