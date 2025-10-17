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

import pascal.taie.ir.IR;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.IndexMap;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Computes and caches the variables defined in each method.
 * Provide util methods for store and load handler.
 */
class DefinedVars {

    private final Map<JMethod, Map<Var, Set<Stmt>>> definedVars = Maps.newMap();

    boolean isDefined(Var var) {
        return definedVars.computeIfAbsent(var.getMethod(),
                        DefinedVars::computeDefinedVars)
                .containsKey(var);
    }

    int getDefSize(Var var) {
        Map<Var, Set<Stmt>> def = definedVars.computeIfAbsent(var.getMethod(),
                DefinedVars::computeDefinedVars);
        if (def.containsKey(var)) {
            return def.get(var).size();
        } else {
            return 0;
        }
    }

    Map<Var, Set<Stmt>> getAllDefs(JMethod method) {
        return definedVars.computeIfAbsent(method, DefinedVars::computeDefinedVars);
    }

    private static Map<Var, Set<Stmt>> computeDefinedVars(JMethod method) {
        IR ir = method.getIR();
        Map<Var, Set<Stmt>> def = new IndexMap<>(ir.getVarIndexer(),
                ir.getStmts().size());
        for (Stmt stmt : ir) {
            stmt.getDef().ifPresent(lValue -> {
                if (lValue instanceof Var v) {
                    if (def.containsKey(v)) {
                        def.get(v).add(stmt);
                    } else {
                        Set<Stmt> stmtSet = Sets.newHybridSet();
                        stmtSet.add(stmt);
                        def.put(v, stmtSet);
                    }
                }
            });
        }
        return def.isEmpty() ? Map.of() : def;
    }
}
