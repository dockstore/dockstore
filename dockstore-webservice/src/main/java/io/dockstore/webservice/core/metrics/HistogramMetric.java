/*
 * Copyright 2025 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.metrics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Schema(name = "HistogramMetric", description = "Describes a metric that consists of a histogram", allOf = Metric.class)
public class HistogramMetric extends Metric {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @NotNull
    @ArraySchema(arraySchema = @Schema(description = "List of frequencies, one per bin."), schema = @Schema(description = "Frequency"))
    private List<Double> frequencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @NotNull
    @ArraySchema(arraySchema = @Schema(description = "List of edge values. Bin number i includes all data values within the interval [edges[i], edges[i + 1])"), schema = @Schema(description = "Edge value"))
    private List<Double> edges;

    @Column(updatable = false)
    @CreationTimestamp
    @JsonIgnore
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @JsonIgnore
    private Timestamp dbUpdateDate;

    public HistogramMetric() {
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public List<Double> getFrequencies() {
        return frequencies;
    }

    public void setFrequencies(List<Double> frequencies) {
        this.frequencies = frequencies;
    }

    public List<Double> getEdges() {
        return edges;
    }

    public void setEdges(List<Double> edges) {
        this.edges = edges;
    }
}
