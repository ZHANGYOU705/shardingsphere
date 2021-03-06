/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.metadata.schema.refresher.type;

import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.schema.builder.SchemaBuilderMaterials;
import org.apache.shardingsphere.infra.metadata.schema.builder.TableMetaDataBuilder;
import org.apache.shardingsphere.infra.metadata.schema.builder.loader.TableMetaDataLoader;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;
import org.apache.shardingsphere.infra.metadata.schema.refresher.SchemaRefresher;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.infra.rule.single.SingleTableRule;
import org.apache.shardingsphere.infra.rule.type.DataNodeContainedRule;
import org.apache.shardingsphere.infra.rule.type.TableContainedRule;
import org.apache.shardingsphere.sql.parser.sql.common.statement.ddl.CreateTableStatement;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * ShardingSphere schema refresher for create table statement.
 */
public final class CreateTableStatementSchemaRefresher implements SchemaRefresher<CreateTableStatement> {
    
    @Override
    public void refresh(final ShardingSphereSchema schema, final Collection<String> routeDataSourceNames, 
                        final CreateTableStatement sqlStatement, final SchemaBuilderMaterials materials) throws SQLException {
        String tableName = sqlStatement.getTable().getTableName().getIdentifier().getValue();
        TableMetaData tableMetaData;
        if (containsInTableContainedRule(tableName, materials)) {
            tableMetaData = TableMetaDataBuilder.build(tableName, materials).orElse(new TableMetaData());
        } else {
            tableMetaData = loadTableMetaData(tableName, routeDataSourceNames, materials);
        }
        schema.put(tableName, tableMetaData);
        if (isSingleTable(tableName, materials)) {
            materials.getRules().stream().filter(each -> each instanceof SingleTableRule).map(each 
                -> (SingleTableRule) each).findFirst().ifPresent(rule -> rule.addSingleTableDataNode(tableName, routeDataSourceNames.iterator().next()));
        }
    }
    
    private boolean isSingleTable(final String tableName, final SchemaBuilderMaterials materials) {
        return materials.getRules().stream().noneMatch(each -> each instanceof DataNodeContainedRule && ((DataNodeContainedRule) each).getAllTables().contains(tableName));
    }
    
    private boolean containsInTableContainedRule(final String tableName, final SchemaBuilderMaterials materials) {
        for (ShardingSphereRule each : materials.getRules()) {
            if (each instanceof TableContainedRule && ((TableContainedRule) each).getTables().contains(tableName)) {
                return true;
            }
        }
        return false;
    }
    
    private TableMetaData loadTableMetaData(final String tableName, final Collection<String> routeDataSourceNames, 
                                            final SchemaBuilderMaterials materials) throws SQLException {
        for (String routeDataSourceName : routeDataSourceNames) {
            DataSource dataSource = materials.getDataSourceMap().get(routeDataSourceName);
            Optional<TableMetaData> tableMetaDataOptional = Objects.isNull(dataSource) ? Optional.empty()
                    : TableMetaDataLoader.load(dataSource, tableName, materials.getDatabaseType());
            if (!tableMetaDataOptional.isPresent()) {
                continue;
            }
            return tableMetaDataOptional.get();
        }
        return new TableMetaData();
    }
}
