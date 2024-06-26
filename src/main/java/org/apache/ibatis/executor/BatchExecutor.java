/**
 * Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.executor;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Jeff Butler
 * 支持批量执行的执行器
 */
public class BatchExecutor extends BaseExecutor {

    public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

    private final List<Statement> statementList = new ArrayList<>();
    private final List<BatchResult> batchResultList = new ArrayList<>();
    private String currentSql;
    private MappedStatement currentStatement;

    public BatchExecutor(Configuration configuration, Transaction transaction) {
        super(configuration, transaction);
    }

    @Override
    public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
        final Configuration configuration = ms.getConfiguration();
        // 创建Statement对象
        final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
        final BoundSql boundSql = handler.getBoundSql();
        // 获取此次追加的SQL模板
        final String sql = boundSql.getSql();
        final Statement stmt;
        // 比较此次追加的SQL模板和最近一次追加的SQL模板，一级两个MappedStatement对象
        // 如果两者相同，获取StatementList集合中的最后一个Statement对象
        if (sql.equals(currentSql) && ms.equals(currentStatement)) {
            int last = statementList.size() - 1;
            stmt = statementList.get(last);
            applyTransactionTimeout(stmt);
            handler.parameterize(stmt);// fix Issues 322
            // 查找该Statement对象对应的BatchResult对象，并记录用户传入的实参
            BatchResult batchResult = batchResultList.get(last);
            batchResult.addParameterObject(parameterObject);
        } else { // 如果两者不同，创建新的Statement对象
            Connection connection = getConnection(ms.getStatementLog());
            // 创建新的Statement对象
            stmt = handler.prepare(connection, transaction.getTimeout());
            handler.parameterize(stmt);    // fix Issues 322
            // 更新currentSql和currentStatement
            currentSql = sql;
            currentStatement = ms;
            // 将新建的Statement对象添加到statementList中
            statementList.add(stmt);
            // 为新的Statement对象添加新的BatchResult
            batchResultList.add(new BatchResult(ms, sql, parameterObject));
        }
        handler.batch(stmt);
        return BATCH_UPDATE_RETURN_VALUE;
    }

    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        Statement stmt = null;
        try {
            flushStatements();
            Configuration configuration = ms.getConfiguration();
            StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
            Connection connection = getConnection(ms.getStatementLog());
            stmt = handler.prepare(connection, transaction.getTimeout());
            handler.parameterize(stmt);
            return handler.query(stmt, resultHandler);
        } finally {
            closeStatement(stmt);
        }
    }

    @Override
    protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
        flushStatements();
        Configuration configuration = ms.getConfiguration();
        StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
        Connection connection = getConnection(ms.getStatementLog());
        Statement stmt = handler.prepare(connection, transaction.getTimeout());
        handler.parameterize(stmt);
        Cursor<E> cursor = handler.queryCursor(stmt);
        stmt.closeOnCompletion();
        return cursor;
    }

    @Override
    public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
        try {
            // 用于储存批处理的结果
            List<BatchResult> results = new ArrayList<>();
            // 如果指定了要回滚事务，则直接返回空集合，忽略statementList集合中记录的SQL语句
            if (isRollback) {
                return Collections.emptyList();
            }
            // 遍历statementList集合
            for (int i = 0, n = statementList.size(); i < n; i++) {
                Statement stmt = statementList.get(i);
                applyTransactionTimeout(stmt);
                BatchResult batchResult = batchResultList.get(i);
                try {
                    // 调用statement.executeBatch()执行其中记录的sql语句，并使用返回的int数组更新BatchResult的updateCounts属性
                    // 其中每个元素都表示一条SQL语句影响的记录条数
                    batchResult.setUpdateCounts(stmt.executeBatch());
                    MappedStatement ms = batchResult.getMappedStatement();
                    List<Object> parameterObjects = batchResult.getParameterObjects();
                    // 获取keyGenerator
                    KeyGenerator keyGenerator = ms.getKeyGenerator();
                    // 获取数据库生成的主键，记录到实参中对应的组件
                    if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
                        Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
                        jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
                    } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //issue #141
                        for (Object parameter : parameterObjects) {
                            keyGenerator.processAfter(this, ms, stmt, parameter);
                        }
                    }
                    // Close statement to close cursor #1109
                    closeStatement(stmt);
                } catch (BatchUpdateException e) {
                    StringBuilder message = new StringBuilder();
                    message.append(batchResult.getMappedStatement().getId())
                            .append(" (batch index #")
                            .append(i + 1)
                            .append(")")
                            .append(" failed.");
                    if (i > 0) {
                        message.append(" ")
                                .append(i)
                                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
                    }
                    throw new BatchExecutorException(message.toString(), e, results, batchResult);
                }
                results.add(batchResult);
            }
            return results;
        } finally {
            for (Statement stmt : statementList) {
                closeStatement(stmt);
            }
            currentSql = null;
            statementList.clear();
            batchResultList.clear();
        }
    }

}
