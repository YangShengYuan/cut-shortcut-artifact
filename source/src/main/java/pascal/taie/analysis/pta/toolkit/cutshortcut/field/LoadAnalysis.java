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

package pascal.taie.analysis.pta.toolkit.cutshortcut.field;

import pascal.taie.World;
import pascal.taie.analysis.pta.core.solver.PropagateTypes;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;
import pascal.taie.util.collection.Sets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class LoadAnalysis {

    private final PropagateTypes propTypes;

    private final DefinedVars definedVars;

    private final CSCHelper helper;

    private final Type stringType;

    // to = base.from
    // method -> { stmt -> (baseIndex, fieldRef) }
    private final Map<JMethod, MultiMap<Stmt, Pair<Integer, FieldRef>>> loadFromParam = Maps.newHybridMap();

    // ret -> { (baseIndex, fieldRef) }
    private final Map<Var, Set<Pair<Integer, FieldRef>>> retSources = Maps.newHybridMap();

    private final Set<JMethod> ignoredMethods = Sets.newSet();

    private final boolean distinguishStrCons;


    LoadAnalysis(PropagateTypes propTypes, DefinedVars definedVars,
                 CSCHelper helper, TypeSystem typeSystem, boolean distinguishStrCons) {
        this.propTypes = propTypes;
        this.definedVars = definedVars;
        this.helper = helper;
        this.stringType = typeSystem.getType("java.lang.String");
        this.distinguishStrCons = distinguishStrCons;
    }

    // return: ret -> (baseIndex, fieldRef)
    Set<Pair<Integer, FieldRef>> getRetVarLoadSource(Var ret) {
        JMethod method = ret.getMethod();
        if (ignoredMethods.contains(method)) {
            return Set.of();
        }
        return retSources.get(ret);
    }

    void computeLoadFromParam(JMethod method) {
        AtomicInteger count = new AtomicInteger();
        IR ir = method.getIR();
        for (Stmt stmt : ir) {
            if (stmt instanceof LoadField load
                    && !load.isStatic()
                    && propTypes.isAllowed(load.getRValue())) {
                Var base = ((InstanceFieldAccess) load.getRValue()).getBase();
                // good tempTo can not be param or this.
                // good tempTo has only 1 definition, which is the load stmt
                Var tempTo = load.getLValue();
                if (ir.isThisOrParam(base)
                        && !definedVars.isDefined(base)
                        && !ir.isThisOrParam(tempTo)) {
                    Pair<Integer, FieldRef> recordpair = new Pair<>(
                            helper.getParamIndex(ir, base), load.getFieldRef());
                    if (loadFromParam.containsKey(method)) {
                        loadFromParam.get(method).put(load, recordpair);
                    } else {
                        MultiMap<Stmt, Pair<Integer, FieldRef>> tempLoadMap = Maps.newMultiMap();
                        tempLoadMap.put(load, recordpair);
                        loadFromParam.put(method, tempLoadMap);
                    }
                }
            }
        }
        if (!loadFromParam.containsKey(method)) {
            MultiMap<Stmt, Pair<Integer, FieldRef>> tempLoadMap = Maps.newMultiMap();
            loadFromParam.put(method, tempLoadMap);
        }
        // compute initial ret var load sources
        ir.getReturnVars().forEach(ret -> {
            Set<Pair<Integer, FieldRef>> retSourceSet =
                    retSources.computeIfAbsent(ret, r -> Sets.newSet());
            Set<Stmt> sources = findSources(ret, ir);
            JClass declaring = ir.getMethod().getDeclaringClass();
            // specially handling, avoid adding too much shortcut edges
            if (!distinguishStrCons) {
                if (method.getReturnType().equals(stringType)) {
                    sources = Set.of();
                }
            }
            sources.forEach(stmt -> {
                retSourceSet.addAll(loadFromParam.get(method).get(stmt));
            });
            count.addAndGet(sources.size());
        });
        if (count.get() > 5) {
            ignoredMethods.add(method);
        }
    }

    /**
     * Performs a local analysis to find all load stmts of {@code sinkVar}.
     * If finds out the value may come from non-local sources, returns empty set.
     */
    private Set<Stmt> findSources(Var sinkVar, IR ir) {
        Map<Var, Set<Stmt>> defs = definedVars.getAllDefs(ir.getMethod());
        Set<Stmt> sources = Sets.newHybridSet();
        Deque<Var> workList = new ArrayDeque<>(Set.of(sinkVar));
        Set<Var> visited = Sets.newHybridSet();
        Set<Stmt> loadStmts = loadFromParam.get(ir.getMethod()).keySet();
        if (loadStmts.isEmpty()) {
            return sources;
        }
        while (!workList.isEmpty()) {
            Var var = workList.poll();
            if (definedVars.getDefSize(var) == 0
                    && ir.isThisOrParam(var)) {
                return Set.of();
            } else {
                if (defs.get(var) != null) {
                    for (Stmt def : defs.get(var)) {
                        Var from;
                        if (def instanceof Copy copy) {
                            from = copy.getRValue();
                        } else if (def instanceof Cast cast) {
                            from = cast.getRValue().getValue();
                        } else if (loadStmts.contains(def)) {
                            sources.add(def);
                            from = null;
                        } else {
                            return Set.of();
                        }
                        if (from != null && !visited.contains(from)) {
                            workList.add(from);
                        }
                    }
                }
            }
            visited.add(var);
        }
        return sources;
    }
}
