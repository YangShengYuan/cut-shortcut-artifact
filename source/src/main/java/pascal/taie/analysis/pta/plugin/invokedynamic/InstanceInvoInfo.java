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

package pascal.taie.analysis.pta.plugin.invokedynamic;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.util.Hashes;

import java.util.List;

class InstanceInvoInfo {

    private final CSCallSite csCallSite;

    private final Context lambdaContext;

    private final Obj lambdaObj;

    InstanceInvoInfo(CSCallSite csCallSite,
                     Context lambdaContext,
                     Obj lambdaObj) {
        this.csCallSite = csCallSite;
        this.lambdaContext = lambdaContext;
        this.lambdaObj = lambdaObj;
    }

    CSCallSite getCSCallSite() {
        return csCallSite;
    }

    InvokeDynamic getLambdaIndy() {
        LambdaInfo info = (LambdaInfo) lambdaObj.getAllocation();
        return (InvokeDynamic) info.invoke.getInvokeExp();
    }

    Context getLambdaContext() {
        return lambdaContext;
    }

    Obj getLambdaObj() {
        return lambdaObj;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstanceInvoInfo that = (InstanceInvoInfo) o;
        return csCallSite.equals(that.csCallSite) &&
                lambdaObj.equals(that.lambdaObj) &&
                lambdaContext.equals(that.lambdaContext);
    }

    @Override
    public int hashCode() {
        return Hashes.hash(csCallSite, lambdaObj, lambdaContext);
    }
}
