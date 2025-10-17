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

package pascal.taie.analysis.pta.plugin.reflection;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;

public class ReflectiveInvokeUtils {

    private static final String classNewInstance = "java.lang.Object newInstance()";
    private static final String constructorNewInstance = "java.lang.Object newInstance(java.lang.Object[])";

    /**
     * given a reflectiveCallEdge, find the arg var at the invocation site of given index
     * @param edge call edge
     * @param index argument index
     */
    public static CSVar getInvokeArg(ReflectiveCallEdge edge, int index, CSManager csManager) {
        Context callerContext = edge.getCallSite().getContext();
        Invoke invoke = edge.getCallSite().getCallSite();
        String sig = invoke.getInvokeExp().getMethodRef().getSubsignature().toString();

        // is classNewInstance
        if (sig.equals(classNewInstance)) {
            if (index == InvokeUtils.BASE) {
                if (invoke.getResult() != null) {
                    Var result = invoke.getResult();
                    return csManager.getCSVar(callerContext, result);
                }
            }
            return null;
        } else if (sig.equals(constructorNewInstance)) { // is constructorNewInstance
            switch (index) {
                case InvokeUtils.BASE -> {
                    if (invoke.getResult() != null) {
                        Var result = invoke.getResult();
                        return csManager.getCSVar(callerContext, result);
                    } else {
                        return null;
                    }
                }
                case InvokeUtils.RESULT -> {
                    return null;
                }
                default -> {
                    Var argHub = edge.getArgHub();
                    return csManager.getCSVar(callerContext, argHub);
                }
            }
        } else {  // common reflective call edge
            return switch (index) {
                case InvokeUtils.BASE -> csManager.getCSVar(callerContext,
                        InvokeUtils.getVar(invoke, 0));
                case InvokeUtils.RESULT -> csManager.getCSVar(callerContext,
                        InvokeUtils.getVar(invoke, InvokeUtils.RESULT));
                default -> csManager.getCSVar(callerContext, edge.getArgHub());
            };
        }
    }
}
