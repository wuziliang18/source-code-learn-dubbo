/*
 * Copyright 1999-2012 Alibaba Group.
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
package com.alibaba.dubbo.common.extension.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.ExtensionFactory;
import com.alibaba.dubbo.common.extension.ExtensionLoader;

/**
 * AdaptiveExtensionFactory
 * ExtensionFactory适配器
 * @author william.liangf
 */
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {
    
    private final List<ExtensionFactory> factories;//只读的 为了安全
    /**
     * ExtensionFactory 适配类的构造函数 缓存所有的ExtensionFactory对象 
     */
    public AdaptiveExtensionFactory() {
        ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
        List<ExtensionFactory> list = new ArrayList<ExtensionFactory>();
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        factories = Collections.unmodifiableList(list);//只读
    }
    /**
     * 循环从ExtensionFactory缓存中获取一个扩展点对象 可以获取就返回
     */
    public <T> T getExtension(Class<T> type, String name) {
        for (ExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);//获取一个扩展点对象
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }

}
