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

package pascal.taie.analysis.pta.toolkit.cutshortcut.local;

import pascal.taie.analysis.pta.core.solver.PropagateTypes;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

class LocalFlowAnalysis {

    private final TypeSystem typeSystem;

    private final PropagateTypes propTypes;

    private final CSCHelper helper;

    private final Type stringType;

    private final boolean distinguishStrCons;

    LocalFlowAnalysis(TypeSystem typeSystem, PropagateTypes propTypes,
                      CSCHelper helper, boolean distinguishStrCons) {
        this.typeSystem = typeSystem;
        this.propTypes = propTypes;
        this.helper = helper;
        this.stringType = typeSystem.getType("java.lang.String");
        this.distinguishStrCons = distinguishStrCons;
    }

    Result analyze(JMethod method) {
        if (!distinguishStrCons) {
            if (method.getReturnType().equals(stringType)) {
                return Result.EMPTY;
            }
        }
        if (!isLocalFlowPossible(method)) {
            return Result.EMPTY;
        }
        IR ir = method.getIR();
        Set<Var> cutReturns = Sets.newHybridSet();
        Set<Integer> shortcuts = Sets.newHybridSet();
        for (Var retVar : ir.getReturnVars()) {
            Set<Var> sources = findLocalSources(retVar, ir);
            // passing ir to call isParamOf(...) in findLocalSources
            if (!sources.isEmpty()
                    && sources.stream().allMatch(ir::isThisOrParam)) {
                cutReturns.add(retVar);
                sources.forEach(v -> shortcuts.add(helper.getParamIndex(ir, v)));
            }
        }
        return cutReturns.isEmpty()
                ? Result.EMPTY
                : new Result(cutReturns, shortcuts);
    }

    private boolean isLocalFlowPossible(JMethod method) {
        Type retType = method.getReturnType();
        if (propTypes.isAllowed(retType)) {
            List<Type> paramTypes = new ArrayList<>(method.getParamTypes());
            if (!method.isStatic()) { // add 'this' type
                paramTypes.add(method.getDeclaringClass().getType());
            }
            for (Type paramType : paramTypes) {
                if (typeSystem.isSubtype(retType, paramType)
                        || typeSystem.isSubtype(paramType, retType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Performs a local analysis to find all local source variables
     * of {@code sinkVar}. If it finds out that the value may come
     * from non-local assignment, then returns empty set.
     */
    private Set<Var> findLocalSources(
            Var sinkVar, IR ir) {
        MultiMap<Var, Stmt> defs = helper.getAllDefs(ir);
        Set<Var> sources = Sets.newHybridSet();
        Deque<Var> workList = new ArrayDeque<>(Set.of(sinkVar));
        Set<Var> visited = Sets.newHybridSet();
        // a set to record vars visited by the work-list algorithm,
        // to handle cycles in local-flow
        while (!workList.isEmpty()) {
            Var var = workList.poll();
            if (!defs.containsKey(var)) {
                sources.add(var);
            } else {
                if (ir.isThisOrParam(var)) {
                    // add any parameters along the way to sources,
                    // to soundly handle cases like testMultiParams1, testMultiParams2, testMultiParams3
                    sources.add(var);
                }
                for (Stmt def : defs.get(var)) {
                    Var from;
                    if (def instanceof Copy copy) {
                        from = copy.getRValue();
                    } else if (def instanceof Cast cast) {
                        from = cast.getRValue().getValue();
                    } else {
                        // value of sinkVar does not come from local flow,
                        // just return empty set
                        return Set.of();
                    }
                    if (!visited.contains(from)) {
                        //if the LHS value is not visited, add it to work-list
                        workList.add(from);
                    }
                }
            }
            visited.add(var); // add the polled var to visited set.
        }
        return sources;
    }
}
