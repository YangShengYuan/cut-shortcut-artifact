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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import pascal.taie.config.ConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Configuration for a Container Class who implements java.util.Map or java.util.Dictionary
 */
record MapConfig(@JsonProperty String className,
                 @JsonProperty List<String> keyExit,
                 @JsonProperty List<String> valueExit,
                 @JsonProperty List<String> entryExit,
                 @JsonProperty List<ContainerConfiger.ConfigEntrance> keyEntrance,
                 @JsonProperty List<ContainerConfiger.ConfigEntrance> valueEntrance,
                 @JsonProperty List<ContainerConfiger.ConfigBatchEntrance> batchEntrance,
                 @JsonProperty List<ContainerConfiger.ConfigArrayEntrance> arrayEntrance,
                 @JsonProperty List<ContainerConfiger.ConfigBatchArrayEn> batchArrayEn,

                 @JsonProperty List<ContainerConfiger.ConfigTransfer> transfer,
                 @JsonProperty List<ContainerConfiger.InnerSource> innerK,
                 @JsonProperty List<ContainerConfiger.InnerSource> innerV,
                 @JsonProperty List<String> replaceAll,
                 @JsonProperty List<String> unsound,
                 @JsonProperty List<String> view) {

    @JsonCreator
    MapConfig(
            @JsonProperty("class") String className,
            @JsonProperty("K-exit") List<String> keyExit,
            @JsonProperty("V-exit") List<String> valueExit,
            @JsonProperty("E-exit") List<String> entryExit,
            @JsonProperty("K-entrance") List<ContainerConfiger.ConfigEntrance> keyEntrance,
            @JsonProperty("V-entrance") List<ContainerConfiger.ConfigEntrance> valueEntrance,
            @JsonProperty("batch-en") List<ContainerConfiger.ConfigBatchEntrance> batchEntrance,
            @JsonProperty("array-en") List<ContainerConfiger.ConfigArrayEntrance> arrayEntrance,
            @JsonProperty("batch-aren") List<ContainerConfiger.ConfigBatchArrayEn> batchArrayEn,
            @JsonProperty("transfer") List<ContainerConfiger.ConfigTransfer> transfer,
            @JsonProperty("inner-K") List<ContainerConfiger.InnerSource> innerK,
            @JsonProperty("inner-V") List<ContainerConfiger.InnerSource> innerV,
            @JsonProperty("repl-all") List<String> replaceAll,
            @JsonProperty("unsound") List<String> unsound,
            @JsonProperty("view") List<String> view) {
        this.className = className;
        this.valueExit = valueExit;
        this.keyExit = keyExit;
        this.entryExit = entryExit;
        this.valueEntrance = valueEntrance;
        this.keyEntrance = keyEntrance;
        this.batchEntrance = batchEntrance;
        this.arrayEntrance = arrayEntrance;
        this.batchArrayEn = batchArrayEn;
        this.transfer = transfer;
        this.innerK = innerK;
        this.innerV = innerV;
        this.replaceAll = replaceAll;
        this.unsound = unsound;
        this.view = view;
    }

    static List<MapConfig> parseConfigs(InputStream content) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JavaType type = mapper.getTypeFactory()
                .constructCollectionType(List.class, MapConfig.class);
        try {
            return mapper.readValue(content, type);
        } catch (IOException e) {
            throw new ConfigException("Failed to read analysis config file", e);
        }
    }

    @Override
    public String className() {
        return className;
    }

    @Override
    public List<String> valueExit() {
        return valueExit;
    }

    @Override
    public List<String> keyExit() {
        return keyExit;
    }

    @Override
    public List<ContainerConfiger.ConfigEntrance> valueEntrance() {
        return valueEntrance;
    }

    @Override
    public List<ContainerConfiger.ConfigEntrance> keyEntrance() {
        return keyEntrance;
    }

    public List<ContainerConfiger.ConfigBatchEntrance> batchEntrance() {
        return batchEntrance;
    }

    @Override
    public List<ContainerConfiger.ConfigArrayEntrance> arrayEntrance() {
        return arrayEntrance;
    }

    @Override
    public List<ContainerConfiger.ConfigBatchArrayEn> batchArrayEn() {
        return batchArrayEn;
    }

    @Override
    public List<ContainerConfiger.ConfigTransfer> transfer() {
        return transfer;
    }

    @Override
    public List<String> entryExit() {
        return entryExit;
    }

    @Override
    public List<String> unsound() {
        return unsound;
    }

    @Override
    public List<String> view() {
        return view;
    }

    @Override
    public List<ContainerConfiger.InnerSource> innerK() {
        return innerK;
    }

    @Override
    public List<ContainerConfiger.InnerSource> innerV() {
        return innerV;
    }

    @Override
    public List<String> replaceAll() {
        return replaceAll;
    }
}

