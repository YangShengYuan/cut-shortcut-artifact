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

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;

import java.util.ArrayList;
import java.util.List;

public class LambdaInvokeUtils {

    public static CSVar getInvokeArg(LambdaCallEdge edge, int index, CSManager csManager, LambdaAnalysis analysis) {
        CSCallSite csCallSite = edge.getCallSite();
        Invoke invoke = csCallSite.getCallSite();
        InvokeExp invokeExp = invoke.getInvokeExp();
        CSMethod csCallee = edge.getCallee();
        List<Var> capturedArgs = edge.getCapturedArgs();
        Context callerContext = csCallSite.getContext();
        Context lambdaContext = edge.getLambdaContext();
        LambdaInfo lambdaInfo = edge.getLambdaInfo();
        List<Var> preciseArgs = null;
        if (lambdaInfo.isPrecise) {
            preciseArgs = analysis.getLambdaPreciseArgs(
                    lambdaInfo, invokeExp.getArgs().size() == 2);
        }
        JMethod callee = csCallee.getMethod();

        CSVar base = null;
        CSVar result = null;
        List<CSVar> args = new ArrayList<>();

        //record base and arg from captured args
        int shiftC = 0;
        if (callee.isConstructor()) {
            // callee is constructor, then base is result of invoke
            if (invoke.getResult() != null) {
                base = csManager.getCSVar(callerContext, invoke.getResult());
            }
        }
        if (!callee.isStatic() && !capturedArgs.isEmpty()) {
            // callee is instance method and there is at least
            // one capture argument, then it must be the receiver object
            base = csManager.getCSVar(lambdaContext, capturedArgs.get(0));
            shiftC = 1;
        }
        for (int i = shiftC; i < capturedArgs.size(); ++i) {
            CSVar csArg = csManager.getCSVar(lambdaContext, capturedArgs.get(i));
            args.add(csArg);
        }

        //record base and arg from actual args
        int shiftA = 0;
        if (capturedArgs.isEmpty()
                && !callee.isStatic() && !callee.isConstructor()) {
            // base is the first actualArg
            if (preciseArgs == null) {
                base = csManager.getCSVar(callerContext, invokeExp.getArg(0));
            } else {
                base = csManager.getCSVar(lambdaContext, preciseArgs.get(0));
            }
            shiftA = 1;
        }
        for (int i = shiftA; i < invokeExp.getArgs().size(); ++i) {
            CSVar csArg;
            if (preciseArgs == null) {
                csArg = csManager.getCSVar(callerContext, invokeExp.getArg(i));
            } else {
                csArg = csManager.getCSVar(lambdaContext, preciseArgs.get(i));
            }
            args.add(csArg);
        }

        // record result
        if (invoke.getResult() != null) {
            result = csManager.getCSVar(callerContext, invoke.getResult());
        }

        return switch (index) {
            case InvokeUtils.BASE -> base;
            case InvokeUtils.RESULT -> result;
            default -> args.get(index);
        };
    }
}
