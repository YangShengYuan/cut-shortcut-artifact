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

package pascal.taie.analysis.pta.toolkit.cutshortcut.stream;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.util.collection.Sets;

import java.util.Set;

/**
 * represent one pipeline of a series stream operations in cut-shortcut pta
 */
public class Strm {

    /**
     * (statically) use stmt invoking an input stage as the identifier
     * for a (dynamic) stream instance (over-approximation)
     */
    private final Stmt allocation;

    /**
     * the mockObj containing this label as allocation object.
     */
    private MockObj mockObj;

    /**
     * if this label may have sources cannot be soundly handled,
     * we mark it as unsound, i.e., invocations to outputAPI whose base
     * is marked with unsound stream label should not be cut.
     */
    private boolean isConserv = false;

    /**
     * a sourceHub is a (mock) var with predecessors of all label sources
     * and with successors being all label targets.
     */
    private CSVar sourceHub;

    /**
     * sources of one label are also sources of its successors.
     * (connect the source hub to all successors' source hub)
     */
    private final Set<Strm> successors = Sets.newSet();

    private final Set<Strm> predecessors = Sets.newSet();

    private final Set<CSVar> sources = Sets.newSet();

    private final Set<CSVar> targets = Sets.newSet();

    Strm(Stmt alloc) {
        this.allocation = alloc;
    }

    public String toString() {
        return "<<Pipeline-Strm>>:" + allocation.toString();
    }

    Stmt getAlloc() {
        return allocation;
    }

    void setUnsound() {
        this.isConserv = true;
    }

    public boolean isConserv() {
        return this.isConserv;
    }

    Set<CSVar> getSources() {
        return this.sources;
    }

    Set<CSVar> targets() {
        return this.targets;
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

    void addSuccessor(Strm strm) {
        this.successors.add(strm);
    }

    Set<Strm> getSuccessors() {
        return this.successors;
    }

    void addPredecessor(Strm strm) {
        this.predecessors.add(strm);
    }

    Set<Strm> getPredecessors() {
        return this.predecessors;
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
        Strm that = (Strm) o;
        return allocation.equals(that.allocation);
    }

    @Override
    public int hashCode() {
        return allocation.hashCode();
    }
}
