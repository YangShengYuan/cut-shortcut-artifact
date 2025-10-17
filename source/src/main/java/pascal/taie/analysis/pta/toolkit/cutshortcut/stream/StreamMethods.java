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

import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.Modifier;
import pascal.taie.util.collection.Sets;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class StreamMethods {

    /**
     * package containing stream related class.
     */
    private static final String STREAM_PACKAGE = "java.util.stream";
    private static final String OPTIONAL = "java.util.Optional";
    private static final String SPLITERATORS = "java.util.Spliterators";
    private static final String SPLITERATOR = "java.util.Spliterator";
    private static final String FUNCTION = "java.util.function";
    private static final String INT_STREAM = "java.util.stream.IntStream";
    private static final String LONG_STREAM = "java.util.stream.LongStream";
    private static final String DOUBLE_STREAM = "java.util.stream.DoubleStream";
    private final JClass intStream;
    private final JClass longStream;
    private final JClass doubleStream;

    private final ClassHierarchy hierarchy;

    public StreamMethods(ClassHierarchy hierarchy) {
        this.hierarchy = hierarchy;
        this.intStream = hierarchy.getClass(INT_STREAM);
        this.longStream = hierarchy.getClass(LONG_STREAM);
        this.doubleStream = hierarchy.getClass(DOUBLE_STREAM);
    }

    /**
     * return methods of any class in stream package
     */
    public Set<JMethod> get() {
        Set<JMethod> results = Sets.newSet();

        Set<JClass> streamClasses
                = hierarchy.allClasses()
                .filter(jClass -> jClass.getName().startsWith(STREAM_PACKAGE)
                        || jClass.getName().startsWith(OPTIONAL)
                        || jClass.getName().startsWith(SPLITERATORS)
                        || jClass.getName().startsWith(SPLITERATOR))
                .collect(Collectors.toSet());
        Set<JMethod> streamMethods = streamClasses
                .stream()
                .map(JClass::getDeclaredMethods)
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableSet());

        Set<JClass> functionalClass
                = hierarchy.allClasses()
                .filter(c -> c.getName().startsWith(FUNCTION))
                .map(hierarchy::getAllSubclassesOf)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        Set<JMethod> functionalMethods = functionalClass
                .stream()
                .map(JClass::getDeclaredMethods)
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableSet());

        results.addAll(streamMethods);
        results.addAll(functionalMethods);
        return results;
    }
}
