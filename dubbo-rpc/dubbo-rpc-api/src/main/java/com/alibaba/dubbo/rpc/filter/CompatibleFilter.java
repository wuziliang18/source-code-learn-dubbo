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
package com.alibaba.dubbo.rpc.filter;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.CompatibleTypeUtils;
import com.alibaba.dubbo.common.utils.PojoUtils;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;

/**
 * CompatibleFilter
 * 这个好像没有走 也没看到激活注解配置
 * @author william.liangf
 */
public class CompatibleFilter implements Filter {
    
    private static Logger logger = LoggerFactory.getLogger(CompatibleFilter.class);

    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Result result = invoker.invoke(invocation);
        if (! invocation.getMethodName().startsWith("$") && ! result.hasException()) {
            Object value = result.getValue();
            if (value != null) {
                try {
                    Method method = invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                    Class<?> type = method.getReturnType();
                    Object newValue;
                    String serialization = invoker.getUrl().getParameter(Constants.SERIALIZATION_KEY); 
                    if ("json".equals(serialization)
                            || "fastjson".equals(serialization)){//是json
                        Type gtype = method.getGenericReturnType();
                        newValue = PojoUtils.realize(value, type, gtype);//解析数据
                    } else if (! type.isInstance(value)) {//如果返回值不是方法的类型 需要解析
                        newValue = PojoUtils.isPojo(type)
                            ? PojoUtils.realize(value, type) 
                            : CompatibleTypeUtils.compatibleTypeConvert(value, type);
                        
                    } else {
                        newValue = value;
                    }
                    if (newValue != value) {
                        result = new RpcResult(newValue);//返回真正的结果
                    }
                } catch (Throwable t) {
                    logger.warn(t.getMessage(), t);
                }
            }
        }
        return result;
    }

}