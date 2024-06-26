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
package org.apache.ibatis.datasource.pooled;

import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 * 对Connection的封装，本类重写了连接的关闭逻辑，让数据库连接在调用close时不会真正被关闭
 */
class PooledConnection implements InvocationHandler {

    private static final String CLOSE = "close";
    private static final Class<?>[] IFACES = new Class<?>[]{Connection.class};

    private final int hashCode; // 连接的hash值
    private final PooledDataSource dataSource;  //连接所属的数据源
    private final Connection realConnection;  // 真正的连接
    private final Connection proxyConnection; // 连接的代理
    private long checkoutTimestamp; // 从连接池中取出的时间
    private long createdTimestamp;  // 创建时间
    private long lastUsedTimestamp; // 最后一次使用的时间戳
    private int connectionTypeCode; // 标志所在连接池的链接类型编码
    private boolean valid;  // 连接是否可用

    /**
     * Constructor for SimplePooledConnection that uses the Connection and PooledDataSource passed in.
     *
     * @param connection - the connection that is to be presented as a pooled connection
     * @param dataSource - the dataSource that the connection is from
     */
    public PooledConnection(Connection connection, PooledDataSource dataSource) {
        this.hashCode = connection.hashCode();
        this.realConnection = connection;
        this.dataSource = dataSource;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastUsedTimestamp = System.currentTimeMillis();
        this.valid = true;
        // 参数依次是：被代理对象的类加载器，被代理对象的接口，包含代理对象的类（就是自身）。
        this.proxyConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), IFACES, this);
    }

    /**
     * Invalidates the connection.
     */
    public void invalidate() {
        valid = false;
    }

    /**
     * Method to see if the connection is usable.
     * 判断连接是否可用的方法，方式是判断valid字段、查看封装的connection是否为null，ping数据库成功
     *
     * @return True if the connection is usable
     */
    public boolean isValid() {
        return valid && realConnection != null && dataSource.pingConnection(this);
    }

    /**
     * Getter for the *real* connection that this wraps.
     *
     * @return The connection
     */
    public Connection getRealConnection() {
        return realConnection;
    }

    /**
     * Getter for the proxy for the connection.
     *
     * @return The proxy
     */
    public Connection getProxyConnection() {
        return proxyConnection;
    }

    /**
     * Gets the hashcode of the real connection (or 0 if it is null).
     *
     * @return The hashcode of the real connection (or 0 if it is null)
     */
    public int getRealHashCode() {
        return realConnection == null ? 0 : realConnection.hashCode();
    }

    /**
     * Getter for the connection type (based on url + user + password).
     *
     * @return The connection type
     */
    public int getConnectionTypeCode() {
        return connectionTypeCode;
    }

    /**
     * Setter for the connection type.
     *
     * @param connectionTypeCode - the connection type
     */
    public void setConnectionTypeCode(int connectionTypeCode) {
        this.connectionTypeCode = connectionTypeCode;
    }

    /**
     * Getter for the time that the connection was created.
     *
     * @return The creation timestamp
     */
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Setter for the time that the connection was created.
     *
     * @param createdTimestamp - the timestamp
     */
    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    /**
     * Getter for the time that the connection was last used.
     *
     * @return - the timestamp
     */
    public long getLastUsedTimestamp() {
        return lastUsedTimestamp;
    }

    /**
     * Setter for the time that the connection was last used.
     *
     * @param lastUsedTimestamp - the timestamp
     */
    public void setLastUsedTimestamp(long lastUsedTimestamp) {
        this.lastUsedTimestamp = lastUsedTimestamp;
    }

    /**
     * Getter for the time since this connection was last used.
     *
     * @return - the time since the last use
     */
    public long getTimeElapsedSinceLastUse() {
        return System.currentTimeMillis() - lastUsedTimestamp;
    }

    /**
     * Getter for the age of the connection.
     *
     * @return the age
     */
    public long getAge() {
        return System.currentTimeMillis() - createdTimestamp;
    }

    /**
     * Getter for the timestamp that this connection was checked out.
     *
     * @return the timestamp
     */
    public long getCheckoutTimestamp() {
        return checkoutTimestamp;
    }

    /**
     * Setter for the timestamp that this connection was checked out.
     *
     * @param timestamp the timestamp
     */
    public void setCheckoutTimestamp(long timestamp) {
        this.checkoutTimestamp = timestamp;
    }

    /**
     * Getter for the time that this connection has been checked out.
     * 获取这个连接被取出使用了多长时间
     *
     * @return the time
     */
    public long getCheckoutTime() {
        return System.currentTimeMillis() - checkoutTimestamp;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Allows comparing this connection to another.
     *
     * @param obj - the other connection to test for equality
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PooledConnection) {
            return realConnection.hashCode() == ((PooledConnection) obj).realConnection.hashCode();
        } else if (obj instanceof Connection) {
            return hashCode == obj.hashCode();
        } else {
            return false;
        }
    }

    /**
     * Required for InvocationHandler implementation.
     * 这里只是对close方法进行了拦截，将关闭连接操作改为放回连接池
     *
     * @param proxy  - not used
     * @param method - the method to be executed
     * @param args   - the parameters to be passed to the method
     * @see java.lang.reflect.InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (CLOSE.equals(methodName)) {
            // 如果调用的是close方法，不将连接关闭，而是将他放回连接池
            dataSource.pushConnection(this);
            return null;
        }
        try {
            // 不是Object里面定义的方法或close方法，检测连接是否可用，实际就是判断valid的值
            if (!Object.class.equals(method.getDeclaringClass())) {
                // issue #579 toString() should never fail
                // throw an SQLException instead of a Runtime
                checkConnection();
            }
            return method.invoke(realConnection, args);
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }

    }

    private void checkConnection() throws SQLException {
        if (!valid) {
            throw new SQLException("Error accessing PooledConnection. Connection is invalid.");
        }
    }

}
