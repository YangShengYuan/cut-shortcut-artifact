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

package pascal.taie.analysis.pta.toolkit.cutshortcut;

import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaCallEdge;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaInfo;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaInvokeUtils;
import pascal.taie.analysis.pta.plugin.reflection.ReflectiveCallEdge;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.AnalysisException;
import pascal.taie.analysis.pta.plugin.reflection.ReflectiveInvokeUtils;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.util.Set;

import static pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis.LAMBDA_DESC;
import static pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis.LAMBDA_REPLICATED_DESC;
import static pascal.taie.analysis.pta.toolkit.cutshortcut.stream.StreamHandler.STRM_OBJ_DESC;

/**
 * util class for cut-shortcut plugins
 */
public class CSCHelper {

    private final LambdaAnalysis lambdaAnalysis;

    private final CSManager csManager;

    CSCHelper(CSManager csManager, LambdaAnalysis lambdaAnalysis) {
        this.lambdaAnalysis = lambdaAnalysis;
        this.csManager = csManager;
    }

    /**
     * given a call-edge, find the pointer representing the argument
     * at the invocation site of given index
     * @param edge call edge
     * @param index argument index
     * @return the pointer of given index at the invocation site.
     */
    public CSVar getCallSiteArg(Edge<CSCallSite, CSMethod> edge, int index) {
        Invoke callSite = edge.getCallSite().getCallSite();
        Context callerContext = edge.getCallSite().getContext();
        // lambda call edge
        if (edge instanceof LambdaCallEdge lambdaCallEdge) {
            return LambdaInvokeUtils.getInvokeArg(lambdaCallEdge, index, csManager, lambdaAnalysis);
        }
        // reflective call edge
        if (edge instanceof ReflectiveCallEdge reflectiveCallEdge) {
            return ReflectiveInvokeUtils.getInvokeArg(reflectiveCallEdge, index, csManager);
        }
        // common call edges.
        if (index == InvokeUtils.BASE
                && !(callSite.getInvokeExp() instanceof InvokeInstanceExp)) {
            return null;
        }
        Var var = InvokeUtils.getVar(callSite, index);
        if (var != null) {
            return csManager.getCSVar(callerContext, var);
        } else {
            return null;
        }
    }

    /**
     * find the parameter index of a method
     * @param ir the ir of the method
     * @param var the var of the parameter
     * @return parameter index
     */
    public int getParamIndex(IR ir, Var var) {
        if (var.equals(ir.getThis())) {
            return InvokeUtils.BASE;
        }
        for (int i = 0; i < ir.getParams().size(); ++i) {
            Var param = ir.getParam(i);
            if (var.equals(param)) {
                return i;
            }
        }
        throw new AnalysisException(var + " is not a parameter");
    }

    /**
     * find the parameter of the given index
     */
    public Var getParam(JMethod method, int index) {
        IR ir = method.getIR();
        return (index == InvokeUtils.BASE) ? ir.getThis() : ir.getParam(index);
    }

    public MultiMap<Var, Stmt> getAllDefs(IR ir) {
        MultiMap<Var, Stmt> result = Maps.newMultiMap();
        ir.forEach(stmt ->
                stmt.getDef().ifPresent(def -> {
                    if (def instanceof Var defVar) {
                        result.put(defVar, stmt);
                    }
                })
        );
        return result;
    }

    public MultiMap<Var, Stmt> getAllUses(IR ir) {
        MultiMap<Var, Stmt> result = Maps.newMultiMap();
        ir.forEach(stmt -> {
            stmt.getUses().forEach(use -> {
                if (use instanceof Var) {
                    result.put((Var) use, stmt);
                }
            });
        });
        return result;
    }

    public boolean isStrm(Obj obj) {
        return (obj instanceof MockObj m) && (m.getDescriptor().equals(STRM_OBJ_DESC));
    }

    public boolean isLambdaObj(Obj obj) {
        return (obj instanceof MockObj m) &&
                ((m.getDescriptor().equals(LAMBDA_DESC) || m.getDescriptor().equals(LAMBDA_REPLICATED_DESC)));
    }

    public Set<CSVar> getPotentialLHS(Edge<CSCallSite, CSMethod> edge) {
        Set<CSVar> lhss = Sets.newSet();
        Invoke invoke = edge.getCallSite().getCallSite();
        Context invokeContext = edge.getCallSite().getContext();
        if (invoke.getResult() != null) {
            lhss.add(csManager.getCSVar(invokeContext, invoke.getResult()));
        }
        if (edge instanceof LambdaCallEdge lambdaCallEdge) {
            LambdaInfo lambdaInfo = lambdaCallEdge.getLambdaInfo();
            if (lambdaInfo.isPrecise) {
                Var ret = lambdaAnalysis.getLambdaRet(lambdaInfo);
                Context lambdaCtx = lambdaCallEdge.getLambdaContext();
                lhss.add(csManager.getCSVar(lambdaCtx, ret));
            }
        }
        return lhss;
    }

    public Set<Var> findCastVar(Var var) {
        JMethod m = var.getMethod();
        Set<Var> result = Sets.newSet();
        for (Stmt stmt : m.getIR().getStmts()) {
            if (stmt instanceof Cast cast) {
                if (var.equals(cast.getRValue().getValue())) {
                    Var casted = cast.getLValue();
                    result.add(casted);
                }
            }
        }
        if (result.size() == 0) {
            result.add(var);
        }
        return result;
    }
}
