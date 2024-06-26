/**
 * Copyright 2009-2016 the original author or authors.
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
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Clinton Begin
 * 所有Statement处理相关的类的父接口
 */
public interface StatementHandler {

    /**
     * 获取Statement对象
     *
     * @param connection         连接
     * @param transactionTimeout 事务超时时间
     */
    Statement prepare(Connection connection, Integer transactionTimeout)
            throws SQLException;

    /**
     * 为SQL语句绑定实参
     *
     * @param statement statement对象
     */
    void parameterize(Statement statement)
            throws SQLException;

    /**
     * 批量执行
     */
    void batch(Statement statement)
            throws SQLException;

    /**
     * 单条更新
     */
    int update(Statement statement)
            throws SQLException;

    /**
     * 单条查询
     */
    <E> List<E> query(Statement statement, ResultHandler resultHandler)
            throws SQLException;

    /**
     * 游标查询，用于超大结果集
     */
    <E> Cursor<E> queryCursor(Statement statement)
            throws SQLException;

    BoundSql getBoundSql();

    ParameterHandler getParameterHandler();

}
