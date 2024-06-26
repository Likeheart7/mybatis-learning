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
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 用于创建Log对象的工厂，里面包含了多种第三方日志框架的适配器
 */
public final class LogFactory {

    /**
     * Marker to be used by logging implementations that support markers.
     */
    public static final String MARKER = "MYBATIS";

    private static Constructor<? extends Log> logConstructor;

    // 该静态代码块尝试创建每种类型的第三方日志，来确定项目具体使用的日志框架
    static {
        tryImplementation(LogFactory::useSlf4jLogging);
        tryImplementation(LogFactory::useCommonsLogging);
        tryImplementation(LogFactory::useLog4J2Logging);
        tryImplementation(LogFactory::useLog4JLogging);
        tryImplementation(LogFactory::useJdkLogging);
        // 不打印日志作为兜底方案
        tryImplementation(LogFactory::useNoLogging);
    }

    private LogFactory() {
        // disable construction
    }

    public static Log getLog(Class<?> clazz) {
        return getLog(clazz.getName());
    }

    public static Log getLog(String logger) {
        try {
            return logConstructor.newInstance(logger);
        } catch (Throwable t) {
            throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
        }
    }

    public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
        setImplementation(clazz);
    }

    public static synchronized void useSlf4jLogging() {
        setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
    }

    public static synchronized void useCommonsLogging() {
        setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
    }

    public static synchronized void useLog4JLogging() {
        setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
    }

    public static synchronized void useLog4J2Logging() {
        setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
    }

    public static synchronized void useJdkLogging() {
        setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
    }

    public static synchronized void useStdOutLogging() {
        setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
    }

    public static synchronized void useNoLogging() {
        setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
    }

    private static void tryImplementation(Runnable runnable) {
        // 如果logConstructor不为空，表示已经确定引用的日志框架，直接跳过方法
        if (logConstructor == null) {
            try {
                runnable.run(); // 这里调用的是run方法不是start方法
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    private static void setImplementation(Class<? extends Log> implClass) {
        try {
            // 获取这个类的有参构造方法
            Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
            // 创建该类实例
            Log log = candidate.newInstance(LogFactory.class.getName());
            // 这一步判断是否开启debug级别，开启才打印这段日志。
            if (log.isDebugEnabled()) {
                log.debug("Logging initialized using '" + implClass + "' adapter.");
            }
            // 加载成功就更新logConstructor字段，记下这个构造方法
            logConstructor = candidate;
        } catch (Throwable t) {
            throw new LogException("Error setting Log implementation.  Cause: " + t, t);
        }
    }

}
