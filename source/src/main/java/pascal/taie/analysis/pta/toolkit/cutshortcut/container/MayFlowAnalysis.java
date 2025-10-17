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

package pascal.taie.analysis.pta.toolkit.cutshortcut.container;

import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.InvokeSpecial;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.ir.stmt.Throw;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.TopType;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

class MayFlowAnalysis {

    /**
     * i: r = b.k(a1,...,an), i in m, m_this ~> b
     * where i in Ct, t :< t_A, t not in T_A.
     */
    static boolean baseMayFromThis(Var base, CSCHelper helper) {
        /*
          if base is some mock var that we cannot distinguish
          if it comes from this. we should handle them conservatively
         */
        if (base.getType().equals(TopType.Top)) {
            return true;
        }
        JMethod m = base.getMethod();
        if (m.getIR().getThis() != null) {
            Var thisVar = m.getIR().getThis();
            Set<Var> sinks = MayFlowAnalysis.mayFlowTo(thisVar, helper);
            if (sinks.isEmpty()) { // this may flow out
                Set<Var> sources = MayFlowAnalysis.mayFlowFrom(base, helper);
                if (sources.isEmpty()) { // base may from outside
                    return true;
                } else {
                    return sources.contains(thisVar);
                }
            } else {
                return sinks.contains(base);
            }
        }
        return false;
    }

    static Set<Var> mayFlowFrom(Var sink, CSCHelper helper) {
        JMethod m = sink.getMethod();
        MultiMap<Var, Stmt> defs = helper.getAllDefs(m.getIR());
        Deque<Var> workList = new ArrayDeque<>(Set.of(sink));
        Set<Var> visited = Sets.newHybridSet();
        Set<Var> sources = Sets.newSet();
        while (!workList.isEmpty()) {
            Var var = workList.poll();
            if (!defs.containsKey(var)) {
                sources.add(var);
            } else {
                if (m.getIR().isThisOrParam(var)) {
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

    /**
     * @return empty set if may flow out
     * otherwise, local variables that source may flow to
     */
    static Set<Var> mayFlowTo(Var source, CSCHelper helper) {
        JMethod m = source.getMethod();
        MultiMap<Var, Stmt> uses = helper.getAllUses(m.getIR());
        Deque<Var> workList = new ArrayDeque<>(Set.of(source));
        Set<Var> visited = Sets.newHybridSet();
        Set<Var> sinks = Sets.newSet();
        sinks.add(source);
        while (!workList.isEmpty()) {
            Var v = workList.poll();
            if (uses.containsKey(v)) {
                for (Stmt stmt : uses.get(v)) {
                    Var to = null;
                    if (stmt instanceof StoreField ||
                            stmt instanceof Invoke ||
                            stmt instanceof Return ||
                            stmt instanceof Throw ||
                            stmt instanceof StoreArray) {
                        return Set.of();
                    } else if (stmt instanceof Copy copy) {
                        to = copy.getLValue();
                    } else if (stmt instanceof Cast cast) {
                        to = cast.getLValue();
                    }
                    if (to != null && !visited.contains(to)) {
                        workList.add(to);
                        sinks.add(to);
                    }
                }
            }
            visited.add(v);
        }
        return sinks;
    }
}
