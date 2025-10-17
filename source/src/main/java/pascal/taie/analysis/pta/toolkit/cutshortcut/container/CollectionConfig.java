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
 * Configuration for a Container who implements java.util.Collection
 */
record CollectionConfig(@JsonProperty String className,
                        @JsonProperty List<String> exit,
                        @JsonProperty List<ContainerConfiger.ConfigEntrance> entrance,
                        @JsonProperty List<ContainerConfiger.ConfigBatchEntrance> batchEntrance,
                        @JsonProperty List<ContainerConfiger.BatchExit> batchExit,
                        @JsonProperty List<ContainerConfiger.ConfigArrayEntrance> arrayEntrance,
                        @JsonProperty List<String> transfer,
                        @JsonProperty List<String> replaceAll,
                        @JsonProperty List<String> unsound,
                        @JsonProperty List<String> view) {

    @JsonCreator
    CollectionConfig(
            @JsonProperty("class") String className,
            @JsonProperty("exit") List<String> exit,
            @JsonProperty("entrance") List<ContainerConfiger.ConfigEntrance> entrance,
            @JsonProperty("batch-en") List<ContainerConfiger.ConfigBatchEntrance> batchEntrance,
            @JsonProperty("batch-ex") List<ContainerConfiger.BatchExit> batchExit,
            @JsonProperty("array-en") List<ContainerConfiger.ConfigArrayEntrance> arrayEntrance,
            @JsonProperty("transfer") List<String> transfer,
            @JsonProperty("repl-all") List<String> replaceAll,
            @JsonProperty("unsound") List<String> unsound,
            @JsonProperty("view") List<String> view) {
        this.className = className;
        this.exit = exit;
        this.entrance = entrance;
        this.batchEntrance = batchEntrance;
        this.batchExit = batchExit;
        this.arrayEntrance = arrayEntrance;
        this.transfer = transfer;
        this.replaceAll = replaceAll;
        this.unsound = unsound;
        this.view = view;
    }

    static List<CollectionConfig> parseConfigs(InputStream content) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JavaType type = mapper.getTypeFactory()
                .constructCollectionType(List.class, CollectionConfig.class);
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
    public List<String> unsound() {
        return unsound;
    }

    @Override
    public List<String> exit() {
        return exit;
    }

    @Override
    public List<ContainerConfiger.ConfigEntrance> entrance() {
        return entrance;
    }

    @Override
    public List<ContainerConfiger.ConfigBatchEntrance> batchEntrance() {
        return batchEntrance;
    }

    @Override
    public List<ContainerConfiger.BatchExit> batchExit() {
        return batchExit;
    }

    @Override
    public List<ContainerConfiger.ConfigArrayEntrance> arrayEntrance() {
        return arrayEntrance;
    }

    @Override
    public List<String> transfer() {
        return transfer;
    }

    @Override
    public List<String> view() {
        return view;
    }

    @Override
    public List<String> replaceAll() {
        return replaceAll;
    }
}
