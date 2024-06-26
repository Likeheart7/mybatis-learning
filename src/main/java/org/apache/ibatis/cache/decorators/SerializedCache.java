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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.SerialFilterChecker;

import java.io.*;

/**
 * @author Clinton Begin
 * 序列化装饰器，为缓存提供序列化功能
 * 保证外部读取缓存中的对象时，每次读取的都是一个全新的拷贝而不是引用，防止外部引用修改缓存内的对象
 */
public class SerializedCache implements Cache {

    private final Cache delegate;

    public SerializedCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    /**
     * 插入缓存
     *
     * @param key    键
     * @param object 缓存对象
     */
    @Override
    public void putObject(Object key, Object object) {
        // 需要缓存的对象必须是可序列化的
        if (object == null || object instanceof Serializable) {
            // 将数据序列化后写入缓存
            delegate.putObject(key, serialize((Serializable) object));
        } else {
            throw new CacheException("SharedCache failed to make a copy of a non-serializable object: " + object);
        }
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 获取的缓存
     */
    @Override
    public Object getObject(Object key) {
        // 获取缓存，这里实际获取的是序列化后的串
        Object object = delegate.getObject(key);
        // 将获取结果反序列化后返回
        return object == null ? null : deserialize((byte[]) object);
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    private byte[] serialize(Serializable value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new CacheException("Error serializing object.  Cause: " + e, e);
        }
    }

    private Serializable deserialize(byte[] value) {
        SerialFilterChecker.check();
        Serializable result;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(value);
             ObjectInputStream ois = new CustomObjectInputStream(bis)) {
            result = (Serializable) ois.readObject();
        } catch (Exception e) {
            throw new CacheException("Error deserializing object.  Cause: " + e, e);
        }
        return result;
    }

    public static class CustomObjectInputStream extends ObjectInputStream {

        public CustomObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
            return Resources.classForName(desc.getName());
        }

    }

}
