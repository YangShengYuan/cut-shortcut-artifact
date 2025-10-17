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

package pascal.taie.analysis.pta.plugin.natives;

import pascal.taie.ir.IR;
import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.StaticFieldAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.LoadArray;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import java.util.Set;

public class ArrayCopyAnalysis {

    /**
     * if v1 and v2 both defined by only one stmt,
     * and the rhs value of these 2 definitions are the same.
     * v1 and v2 are considered as alias here.
     */
    static boolean beingAlias(Var v1, Var v2) {
        if (v1.equals(v2)) {
            return true;
        }
        JMethod container = v1.getMethod();
        MultiMap<Var, Stmt> defs = getAllDefs(container.getIR());
        Set<Stmt> defsOfV1 = defs.get(v1);
        Set<Stmt> defsOfV2 = defs.get(v2);
        if (defsOfV1.size() == 1 && defsOfV2.size() == 1) {
            return defFromSame(
                    defsOfV1.iterator().next(),
                    defsOfV2.iterator().next());
        } else {
            return false;
        }
    }

    /**
     * if two stmt are of the same type,
     * and is Copy | Cast | LoadField | LoadArray,
     * and the rhs of stmt1 and stmt2 are syntactically equivalent,
     * return true, otherwise false.
     */
    static boolean defFromSame(Stmt stmt1, Stmt stmt2) {
        if (stmt1 instanceof Copy copy1
                && stmt2 instanceof Copy copy2) {
            return copy1.getRValue().equals(copy2.getRValue());
        } else if (stmt1 instanceof Cast cast1
                && stmt2 instanceof Cast cast2) {
            return cast1.getRValue().equals(cast2.getRValue());
        } else if (stmt1 instanceof LoadField fieldLoad1
                && stmt2 instanceof LoadField fieldLoad2) {
            FieldAccess fieldAccess1 = fieldLoad1.getFieldAccess();
            FieldAccess fieldAccess2 = fieldLoad2.getFieldAccess();
            if (fieldAccess1 instanceof InstanceFieldAccess instanceFieldAccess1
                    && fieldAccess2 instanceof InstanceFieldAccess instanceFieldAccess2) {
                return instanceFieldAccess1.getBase().equals(instanceFieldAccess2.getBase())
                        && instanceFieldAccess1.getFieldRef().equals(instanceFieldAccess2.getFieldRef());
            } else if (fieldAccess1 instanceof StaticFieldAccess staticFieldAccess1
                    && fieldAccess2 instanceof StaticFieldAccess staticFieldAccess2) {
                return staticFieldAccess1.getType().equals(staticFieldAccess2.getType())
                        && staticFieldAccess1.getFieldRef().equals(staticFieldAccess2.getFieldRef());
            } else {
                return false;
            }
        } else if (stmt1 instanceof LoadArray arrayLoad1
                && stmt2 instanceof LoadArray arrayLoad2) {
            ArrayAccess arrayAccess1 = arrayLoad1.getArrayAccess();
            ArrayAccess arrayAccess2 = arrayLoad2.getArrayAccess();
            return arrayAccess1.getBase().equals(arrayAccess2.getBase());
        } else {
            return false;
        }
    }

    public static MultiMap<Var, Stmt> getAllDefs(IR ir) {
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
}
