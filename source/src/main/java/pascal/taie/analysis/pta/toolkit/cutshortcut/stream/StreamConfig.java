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
 * Configuration for Stream API
 */
record StreamConfig(@JsonProperty List<String> scope,
                    @JsonProperty List<String> creation,
                    @JsonProperty List<StreamConfiger.Creation> containerCreation,
                    @JsonProperty List<StreamConfiger.ArrayEn> arrayCreation,
                    @JsonProperty List<StreamConfiger.Entrance> entrance,
                    @JsonProperty List<String> process,
                    @JsonProperty List<String> output,
                    @JsonProperty List<String> primitiveCreation,
                    @JsonProperty List<String> map,
                    @JsonProperty List<String> flatMap,
                    @JsonProperty List<String> forEach,
                    @JsonProperty List<StreamConfiger.Unsound> unsound) {

    @JsonCreator
    StreamConfig(
            @JsonProperty("scope") List<String> scope,
            @JsonProperty("creation") List<String> creation,
            @JsonProperty("cont-cr") List<StreamConfiger.Creation> containerCreation,
            @JsonProperty("array-cr") List<StreamConfiger.ArrayEn> arrayCreation,
            @JsonProperty("entrance") List<StreamConfiger.Entrance> entrance,
            @JsonProperty("process") List<String> process,
            @JsonProperty("output") List<String> output,
            @JsonProperty("prim-cr") List<String> primitiveCreation,
            @JsonProperty("map") List<String> map,
            @JsonProperty("flatMap") List<String> flatMap,
            @JsonProperty("foreach") List<String> forEach,
            @JsonProperty("unsound") List<StreamConfiger.Unsound> unsound) {
        this.scope = scope;
        this.creation = creation;
        this.containerCreation = containerCreation;
        this.arrayCreation = arrayCreation;
        this.entrance = entrance;
        this.process = process;
        this.output = output;
        this.primitiveCreation = primitiveCreation;
        this.map = map;
        this.flatMap = flatMap;
        this.forEach = forEach;
        this.unsound = unsound;
    }

    static List<StreamConfig> parseConfigs(InputStream content) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JavaType type = mapper.getTypeFactory()
                .constructCollectionType(List.class, StreamConfig.class);
        try {
            return mapper.readValue(content, type);
        } catch (IOException e) {
            throw new ConfigException("Failed to read analysis config file", e);
        }
    }

    @Override
    public List<String> map() {
        return map;
    }

    @Override
    public List<String> scope() {
        return scope;
    }

    @Override
    public List<String> creation() {
        return creation;
    }

    @Override
    public List<StreamConfiger.Creation> containerCreation() {
        return containerCreation;
    }

    @Override
    public List<StreamConfiger.ArrayEn> arrayCreation() {
        return arrayCreation;
    }

    @Override
    public List<StreamConfiger.Entrance> entrance() {
        return entrance;
    }

    @Override
    public List<String> process() {
        return process;
    }

    @Override
    public List<String> output() {
        return output;
    }

    @Override
    public List<String> forEach() {
        return forEach;
    }

    @Override
    public List<StreamConfiger.Unsound> unsound() {
        return unsound;
    }

    @Override
    public List<String> primitiveCreation() {
        return primitiveCreation;
    }

    @Override
    public List<String> flatMap() {
        return flatMap;
    }
}
