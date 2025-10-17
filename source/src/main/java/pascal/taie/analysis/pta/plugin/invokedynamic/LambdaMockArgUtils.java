/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.plugin.invokedynamic;

import pascal.taie.World;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.MethodHandle;
import pascal.taie.ir.exp.MethodType;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.type.TopType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import java.util.ArrayList;
import java.util.List;

import static pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis.getMethodHandle;

/**
 * provides util APIs for other plugins to invoke
 * necessary for a precise modeling of Java lambda expressions.
 */
public class LambdaMockArgUtils {
    private final LambdaAnalysis analysis;

    private final Solver solver;

    private final CSManager csManager;

    LambdaMockArgUtils(LambdaAnalysis lambdaAnalysis, Solver solver) {
        this.analysis = lambdaAnalysis;
        this.solver = solver;
        this.csManager = solver.getCSManager();
    }

    /**
     * provide a way to access mock vars to denote LambdaInfo.preciseArg of a
     * given lambda expression, For memory saving purpose, not all lambda expression hold
     * this field, hence this API should only be called by ContainerAccessHandler | StreamHandler
     * (where precise arguments in each scenario will be linked to this field on PFG).
     * @param count the count of actual args
     * @return captured precise arguments when this lambda expression created.
     */
    List<Var> getLambdaPreciseArgs(LambdaInfo lambdaInfo, int count) {
        if (!lambdaInfo.isPrecise) {
            return null;
        }
        if (lambdaInfo.getPreciseArgs() == null) {
            Invoke invoke = lambdaInfo.invoke;
            InvokeDynamic indy = (InvokeDynamic) invoke.getInvokeExp();
            MethodType methodType = (MethodType) indy.getBootstrapArgs().get(2);
            List<Type> mockArgTypes = methodType.getParamTypes();

            // generate prcArg Vars for lambdaInfo.
            List<Var> prcArgs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Var arg = new Var(invoke.getContainer(),
                        "[Lambda-Precise-Arg-" + i + "]-{" + invoke + "}", mockArgTypes.get(i), -1);
                prcArgs.add(arg);
            }
            lambdaInfo.setPreciseArgs(prcArgs);
            // generate sound counterpart for each precise arg.
            List<Var> soundCtp = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Var soundArgHub = new Var(invoke.getContainer(),
                        "[Lambda-Precise-Arg-Sound-Counterpart" + i + "]-{" + invoke + "}", TopType.Top, -1);
                soundCtp.add(soundArgHub);
            }
            lambdaInfo.setSoundCpt(soundCtp);
            if (!lambdaInfo.isSafePrecise()) {
                lambdaInfo.markUnsound(analysis);
            }
            // generate corresponding lambda mock arg, and link precise args to mock args
            List<Var> mockArgs = getLambdaMockArgs(lambdaInfo, count);
            lambdaInfo.setMockArgs(mockArgs);
            // link lambdaPrcArg to LambdaMockArg
            Context empty = solver.getContextSelector().getEmptyContext();
            for (int i = 0; i < count; i++) {
                CSVar csLPrcArg = csManager.getCSVar(empty, prcArgs.get(i));
                CSVar csLMockArg = csManager.getCSVar(empty, mockArgs.get(i));
                solver.addPFGEdge(new ShortcutEdge(csLPrcArg, csLMockArg), csLMockArg.getType());
            }
        }
        return lambdaInfo.getPreciseArgs();
    }

    List<Var> getLambdaMockArgs(LambdaInfo lambdaInfo, int count) {
        if (lambdaInfo.getMockArgs() != null) {
            return lambdaInfo.getMockArgs();
        }
        Invoke invoke = lambdaInfo.invoke;
        InvokeDynamic indy = (InvokeDynamic) lambdaInfo.invoke.getInvokeExp();
        MethodType methodType = (MethodType) indy.getBootstrapArgs().get(2);
        List<Type> mockArgTypes = methodType.getParamTypes();
        List<Var> mockArgs = new ArrayList<>();
        for (int i = 0; i < count ; i++) {
            Type argType = mockArgTypes.get(i);
            Var lambdaMockArg = new Var(invoke.getContainer(),
                    "[Lambda-Mock-Arg-" + i + "]-{" + invoke + "}", argType, -1);
            mockArgs.add(lambdaMockArg);
        }
        return mockArgs;
    }

    /**
     * provide a way to access mock var to denote {@link LambdaInfo#ret} of a
     * given lambda expression, For memory saving purpose, not all lambda expression hold
     * this field, hence this API should only be called by StreamHandler
     * (where the potential return value is necessary to ensure soundness).
     */
    Var getLambdaRet(LambdaInfo lambdaInfo) {
        if (!lambdaInfo.isPrecise) {
            return null;
        }
        if (lambdaInfo.ret == null) {
            Invoke invoke = lambdaInfo.invoke;
            lambdaInfo.ret = new Var(invoke.getContainer(),
                    "[Lambda-Ret]-{" + invoke + "}", TopType.Top, -1);
        }
        return lambdaInfo.ret;
    }
}
