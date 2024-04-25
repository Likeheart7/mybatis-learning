/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Clinton Begin
 * 用于处理Statement语句的实现类，不会包含“？”占位符，parameterize是空实现
 */
public class SimpleStatementHandler extends BaseStatementHandler {

    public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public int update(Statement statement) throws SQLException {
        String sql = boundSql.getSql();
        Object parameterObject = boundSql.getParameterObject();
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        int rows;
        // 执行后，通过KeyGenerator.processAfter()查询主键并填充相应属性
        if (keyGenerator instanceof Jdbc3KeyGenerator) {
            statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
            rows = statement.getUpdateCount();
            keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
        } else if (keyGenerator instanceof SelectKeyGenerator) {
            statement.execute(sql);
            rows = statement.getUpdateCount();
            keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
        } else {
            statement.execute(sql);
            rows = statement.getUpdateCount();
        }
        return rows;
    }

    @Override
    public void batch(Statement statement) throws SQLException {
        String sql = boundSql.getSql();
        // 将sql添加进去，等到Statement.executeBatch()的时候执行
        statement.addBatch(sql);
    }

    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
        // 获取SQL语句
        String sql = boundSql.getSql();
        // 执行SQL语句
        statement.execute(sql);
        // 处理ResultSet映射，得到结果对象
        return resultSetHandler.handleResultSets(statement);
    }

    @Override
    public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
        String sql = boundSql.getSql();
        statement.execute(sql);
        return resultSetHandler.handleCursorResultSets(statement);
    }

    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        // 直接通过Connection获取Statement对象，这个对象就是其他方法的入参
        if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
            return connection.createStatement();
        } else {
            return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
        }
    }

    @Override
    public void parameterize(Statement statement) {
        // N/A
    }

}
