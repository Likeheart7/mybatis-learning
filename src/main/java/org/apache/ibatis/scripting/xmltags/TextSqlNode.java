/**
 * Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

import java.util.regex.Pattern;

/**
 * @author Clinton Begin
 * 字符串节点，就是标签内的普通SQL文本，解析是为了替换内部的${}占位符
 */
public class TextSqlNode implements SqlNode {
    private final String text;
    private final Pattern injectionFilter;

    public TextSqlNode(String text) {
        this(text, null);
    }

    public TextSqlNode(String text, Pattern injectionFilter) {
        this.text = text;
        this.injectionFilter = injectionFilter;
    }

    /**
     * 判断解析的文本SQL是否是动态SQL，内部有${}就是动态的，反之不是
     *
     * @return 判断结果
     */
    public boolean isDynamic() {
        // 占位符处理器，该处理器只判断是否含有占位符
        DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
        GenericTokenParser parser = createParser(checker);
        // 如果含有占位符，会将checker的isDynamic属性置为true
        parser.parse(text);
        return checker.isDynamic();
    }

    /**
     * 解析该节点自身
     *
     * @param context 上下文环境，解析结果就合并到这里面
     * @return 是否解析成功
     */
    @Override
    public boolean apply(DynamicContext context) {
        // 创建GenericTokenParser解析器，这里指定的占位符的起止符号分别是"${"和"}"
        GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
        // BindingTokenParser将${}替换为用户传入的实参
        // 将解析之后的SQL片段追加到DynamicContext暂存
        context.appendSql(parser.parse(text));
        return true;
    }

    /**
     * 创建一个通用占位符解析器，用来解析${}占位符
     *
     * @param handler 用来处理${}占位符的专用处理器
     * @return 占位符解析器
     */
    private GenericTokenParser createParser(TokenHandler handler) {
        return new GenericTokenParser("${", "}", handler);
    }

    private static class BindingTokenParser implements TokenHandler {

        private DynamicContext context;
        private Pattern injectionFilter;

        public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
            this.context = context;
            this.injectionFilter = injectionFilter;
        }

        /**
         * 处理${}占位符的方法
         * 过程逻辑是，取出占位符中的变量，以该变量为键去上下文环境中查值，然后用找到的值替换占位符
         */
        @Override
        public String handleToken(String content) {
            // 获取用户提供的实参数据
            Object parameter = context.getBindings().get("_parameter");
            if (parameter == null) {
                context.getBindings().put("value", null);
            } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
                context.getBindings().put("value", parameter);
            }
            // 通过Ognl解析"${}"占位符中的表达式，解析失败的话会返回空字符串
            Object value = OgnlCache.getValue(content, context.getBindings());
            String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"
            checkInjection(srtValue); // 对解析后的值进行过滤
            return srtValue;
        }

        private void checkInjection(String value) {
            if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
                throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
            }
        }
    }

    private static class DynamicCheckerTokenParser implements TokenHandler {

        private boolean isDynamic;

        public DynamicCheckerTokenParser() {
            // Prevent Synthetic Access
        }

        public boolean isDynamic() {
            return isDynamic;
        }

        /**
         * 记录自身是否遇到过占位符
         */
        @Override
        public String handleToken(String content) {
            this.isDynamic = true;
            return null;
        }
    }

}
