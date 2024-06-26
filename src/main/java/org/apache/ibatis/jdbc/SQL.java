/**
 * Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.jdbc;

/**
 * @author Clinton Begin
 * 作为AbstractSQL类的子类，只重写了getSelf方法让其返回自身
 * 通过@*Provider提供的SQL语句，就是通过本类实现的
 */
public class SQL extends AbstractSQL<SQL> {

    @Override
    public SQL getSelf() {
        return this;
    }

}
