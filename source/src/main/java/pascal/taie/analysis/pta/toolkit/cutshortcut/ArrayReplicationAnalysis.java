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

import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

public class ArrayReplicationAnalysis {
    Solver solver;

    CSManager csManager;

    HeapModel heapModel;

    private final MultiMap<ArrayIndex, ArrayIndex> arrayIndex2Replicated;

    private final MultiMap<ArrayIndex, CSVar> arrayIndexOutScopeFroms;

    private static final Descriptor REPLICATED_ARRAY_DESC = () -> "ReplicatedArrayObj";

    ArrayReplicationAnalysis(Solver solver) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
        this.heapModel = solver.getHeapModel();
        this.arrayIndex2Replicated = Maps.newMultiMap();
        this.arrayIndexOutScopeFroms = Maps.newMultiMap();
    }

    public ArrayIndex replicateArrayIndex(ArrayIndex arrayIndex, JMethod inMethod, Context context) {
        CSObj originalCSArrayObj = arrayIndex.getArray();
        Obj originalArrayObj = originalCSArrayObj.getObject();
        // generate a replicated arrayIndex
        Obj replicatedArrayObj = heapModel.getMockObj(REPLICATED_ARRAY_DESC,
                originalArrayObj, originalArrayObj.getType(), inMethod);
        CSObj replicatedCSArrayObj = csManager.getCSObj(context, replicatedArrayObj);
        ArrayIndex replicatedArrayIndex = csManager.getArrayIndex(replicatedCSArrayObj);
        // record in map.
        arrayIndex2Replicated.put(arrayIndex, replicatedArrayIndex);
        // if original array index already has some out of scope source
        arrayIndexOutScopeFroms.get(arrayIndex).forEach(from -> {
            solver.addPFGEdge(new PointerFlowEdge(
                    FlowKind.ARRAY_STORE, from, replicatedArrayIndex), replicatedArrayIndex.getType());
        });
        return replicatedArrayIndex;
    }

    public void reflectStoring(ArrayIndex arrayIndex, CSVar from) {
        // record from for later comes replicated array index.
        arrayIndexOutScopeFroms.put(arrayIndex, from);
        // for existing replicated array index
        arrayIndex2Replicated.get(arrayIndex).forEach(replicatedArrayIndex -> {
            solver.addPFGEdge(new PointerFlowEdge(
                    FlowKind.ARRAY_STORE, from, replicatedArrayIndex), replicatedArrayIndex.getType());
        });
    }
}
