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

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.language.type.Type;
import pascal.taie.util.Hashes;
import pascal.taie.util.collection.Sets;

import java.util.Set;

/**
 * represent the host obj of a container in cut-shortcut pta
 */
public class Host {

    /**
     * the container obj the host represent.
     */
    private final Obj obj;

    /**
     * the mockObj containing this host as allocation object.
     */
    private MockObj mockObj;

    /**
     * the retrieve kind of this host
     */
    private final RetrieveKind kind;

    /**
     * if this host may have sources from custom containers
     * if an invocation to an exit has base containing
     * a tainted host, the return var of this invoke should not be cut.
     */
    private boolean isTainted = false;

    /**
     * a sourceHub is a (mock) var with predecessor of all host sources
     * and with successor of all host targets.
     */
    private CSVar sourceHub;

    /**
     * sources of one host are also sources of its successors
     * (however, for target, this is not the case)
     */
    private final Set<Host> successors = Sets.newSet();
    private final Set<Host> predecessors = Sets.newSet();

    private final Set<CSVar> sources = Sets.newSet();

    private final Set<CSVar> targets = Sets.newSet();

    Host(Obj obj, RetrieveKind kind) {
        this.obj = obj;
        this.kind = kind;
    }

    public String toString() {
        return "<<Host>>:" + "[" + kind + "]-" + obj.toString();
    }

    Type getType() {
        return obj.getType();
    }

    public RetrieveKind getKind() {
        return kind;
    }

    Obj getObj() {
        return obj;
    }

    void addSuccessor(Host host) {
        this.successors.add(host);
    }

    Set<Host> getSuccessors() {
        return successors;
    }

    void addPredecessor(Host h) {
        this.predecessors.add(h);
    }

    Set<Host> getPredecessors() {
        return this.predecessors;
    }

    void setTaint() {
        this.isTainted = true;
    }

    public boolean isTainted() {
        return this.isTainted;
    }

    Set<CSVar> getSources() {
        return sources;
    }

    Set<CSVar> getTargets() {
        return targets;
    }

    boolean addSource(CSVar source) {
        return this.sources.add(source);
    }

    boolean addTarget(CSVar target) {
        return this.targets.add(target);
    }

    void setSourceHub(CSVar sourceHub) {
        this.sourceHub = sourceHub;
    }

    public CSVar getSourceHub() {
        return sourceHub;
    }

    void setMockObj(MockObj mockObj) {
        this.mockObj = mockObj;
    }

    MockObj getMockObj() {
        return mockObj;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Host that = (Host) o;
        return obj.equals(that.obj)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Hashes.hash(obj, kind);
    }

}
