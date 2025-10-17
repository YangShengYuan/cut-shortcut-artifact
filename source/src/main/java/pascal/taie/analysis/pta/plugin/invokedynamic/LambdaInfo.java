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

import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.util.Hashes;

import java.util.List;

public class LambdaInfo {
    /**
     * represent the invokedynamic call-site for resolving a lambda expression
     * e.g., function = invokedynamic <...>
     * invoke.getInvokeExp().getArgs gives the captured argument when lambdaObj created.
     */
    final Invoke invoke;

    /**
     * captured precise arguments by StreamHandler & ContainerAccessHandler
     */
    private List<Var> preciseArgs;

    /**
     * sound counterparts for each precise arg.
     * captured from mixed arg.
     */
    private List<Var> prcArgSoundCtp;

    /**
     * Lambda Mock args.
     * if this lambdaInfo is precise handled by csc-s
     * mock args comes from its preciseArgs
     * otherwise, it comes from actual args.
     * initialized when (1) lambdaInfo is precise and precise arg is initialized
     *                  (2) lambdaInfo is not precise and lambda call edge is about to add
     */
    private List<Var> mockArgs;

    /**
     * catch the (potentially exit) ret value of this lambda expression's target method,
     * which will be resolved after lambdaCallEdge created.
     * for sound and precise static modeling of Stream.map | flatMap | reduce |...
     * semantics by passing this mock var back to StreamHandler
     */
    Var ret;

    /**
     * represent if preciseArgs is sound.
     */
    private boolean safePrecise = true;

    /**
     * represent if this LambdaObj is a mirrored obj used in precise scenarios,
     * e.g., used by container.forEach(lambda) | stream.map(lambda).
     */
    public final boolean isPrecise;

    LambdaInfo(Invoke invoke,  Boolean isPrecise) {
        this.invoke = invoke;
        this.ret = null;
        this.preciseArgs = null;
        this.prcArgSoundCtp = null;
        this.isPrecise = isPrecise;
    }

    public boolean getSoundness() {
        return safePrecise;
    }

    public void markUnsound(LambdaAnalysis analysis) {
        this.safePrecise = false;
        if (this.preciseArgs != null) {
            for (int i = 0; i < preciseArgs.size(); i++) {
                Var preciseArg = preciseArgs.get(i);
                Var soundCtp = prcArgSoundCtp.get(i);
                analysis.makePreciseArgSound(preciseArg, soundCtp);
            }
        }
    }

    List<Var> getPreciseArgs() {
        return preciseArgs;
    }

    void setPreciseArgs(List<Var> prcArgs) {
        this.preciseArgs = prcArgs;
    }

    Var getSoundCpt(int i) {
        return prcArgSoundCtp.get(i);
    }

    void setSoundCpt(List<Var> soundCpt) {
        this.prcArgSoundCtp = soundCpt;
    }

    List<Var> getMockArgs() {
        return this.mockArgs;
    }

    public Var getMockArg(int i) {
        return mockArgs.get(i);
    }

    void setMockArgs(List<Var> mockArgs) {
        this.mockArgs = mockArgs;
    }

    boolean isSafePrecise() {
        return safePrecise;
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
        LambdaInfo that = (LambdaInfo) o;
        return invoke.equals(that.invoke) && isPrecise == that.isPrecise;
    }

    @Override
    public int hashCode() {
        return Hashes.hash(invoke, isPrecise);
    }
}
