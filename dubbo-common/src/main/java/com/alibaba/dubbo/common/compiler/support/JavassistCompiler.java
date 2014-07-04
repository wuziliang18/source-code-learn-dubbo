/*
 * Copyright 1999-2011 Alibaba Group.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.common.compiler.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.dubbo.common.utils.ClassHelper;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

/**
 * JavassistCompiler. (SPI, Singleton, ThreadSafe)
 * dubbo的compiler扩展点的默认实现哟 使用了模板方法
 * @author william.liangf
 */
public class JavassistCompiler extends AbstractCompiler {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w\\.\\*]+);\n");

    private static final Pattern EXTENDS_PATTERN = Pattern.compile("\\s+extends\\s+([\\w\\.]+)[^\\{]*\\{\n");

    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("\\s+implements\\s+([\\w\\.]+)\\s*\\{\n");
    

    private static final Pattern METHODS_PATTERN = Pattern.compile("\n(private|public|protected)\\s+");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[^\n]+=[^\n]+;");

    @Override
    public Class<?> doCompile(String name, String source) throws Throwable {
        int i = name.lastIndexOf('.');
        String className = i < 0 ? name : name.substring(i + 1);
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(ClassHelper.getCallerClassLoader(getClass())));
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        List<String> importPackages = new ArrayList<String>();//保存引入的包名 似乎没有去从
        Map<String, String> fullNames = new HashMap<String, String>();//保存完成类与对象的map
        /*1.引入包加入到classpool中*/
        while (matcher.find()) {
            String pkg = matcher.group(1);//引入的类路径或者包+*
            if (pkg.endsWith(".*")) {
                String pkgName = pkg.substring(0, pkg.length() - 2);//如果是.*获取包名
                pool.importPackage(pkgName);
                importPackages.add(pkgName);
            } else {
                int pi = pkg.lastIndexOf('.');
                if (pi > 0) {
	                String pkgName = pkg.substring(0, pi);
	                pool.importPackage(pkgName);
	                importPackages.add(pkgName);
	                fullNames.put(pkg.substring(pi + 1), pkg);
                }
            }
        }
        String[] packages = importPackages.toArray(new String[0]);
        matcher = EXTENDS_PATTERN.matcher(source);
        CtClass cls;
        /*2处理可能存在的父类 ,新创建一个class 空的*/
        if (matcher.find()) {
            String extend = matcher.group(1).trim();
            String extendClass;
            if (extend.contains(".")) {
                extendClass = extend;//如果继承时写的是全路径
            } else if (fullNames.containsKey(extend)) {
                extendClass = fullNames.get(extend);//如果在引入时候能找到
            } else {
                extendClass = ClassUtils.forName(packages, extend).getName();//在制定包下查找
            }
            cls = pool.makeClass(name, pool.get(extendClass));//生成新的class 
        } else {
            cls = pool.makeClass(name);//生成新的class 
        }
        matcher = IMPLEMENTS_PATTERN.matcher(source);
        /*3.处理实现的接口*/
        if (matcher.find()) {
            String[] ifaces = matcher.group(1).trim().split("\\,");
            for (String iface : ifaces) {
                iface = iface.trim();
                String ifaceClass;
                if (iface.contains(".")) {
                    ifaceClass = iface;
                } else if (fullNames.containsKey(iface)) {
                    ifaceClass = fullNames.get(iface);
                } else {
                    ifaceClass = ClassUtils.forName(packages, iface).getName();
                }
                cls.addInterface(pool.get(ifaceClass));
            }
        }
        /*4.处理真正的源码*/
        String body = source.substring(source.indexOf("{") + 1, source.length() - 1);//获取代码主体
        String[] methods = METHODS_PATTERN.split(body);//很不错的一个切分
        for (String method : methods) {
            method = method.trim();
            if (method.length() > 0) {//多余的判断--不多于可能防止第一个为空的
                if (method.startsWith(className)) {
                    cls.addConstructor(CtNewConstructor.make("public " + method, cls));//构造函数
                } else if (FIELD_PATTERN.matcher(method).matches()) {
                    cls.addField(CtField.make("private " + method, cls));//参数 都私有
                } else {
                    cls.addMethod(CtNewMethod.make("public " + method, cls));//方法 公有
                }
            }
        }
        return cls.toClass(ClassHelper.getCallerClassLoader(getClass()), JavassistCompiler.class.getProtectionDomain());
    }

}
