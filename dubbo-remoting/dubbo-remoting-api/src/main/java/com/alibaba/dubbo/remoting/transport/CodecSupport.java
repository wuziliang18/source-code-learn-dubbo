/*
 * Copyright 1999-2011 Alibaba Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.dubbo.remoting.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.serialize.Serialization;

/**
 * @author <a href="mailto:gang.lvg@alibaba-inc.com">kimi</a>
 */
public class CodecSupport {

    private static final Logger logger = LoggerFactory.getLogger(CodecSupport.class);

    private CodecSupport() {
    }

    private static Map<Byte, Serialization> ID_SERIALIZATION_MAP = new HashMap<Byte, Serialization>();

    static {
        Set<String> supportedExtensions = ExtensionLoader.getExtensionLoader(Serialization.class).getSupportedExtensions();
        for (String name : supportedExtensions) {
            Serialization serialization = ExtensionLoader.getExtensionLoader(Serialization.class).getExtension(name);
            byte idByte = serialization.getContentTypeId();//各个Serialization的id 唯一
            if (ID_SERIALIZATION_MAP.containsKey(idByte)) {
                logger.error("Serialization extension " + serialization.getClass().getName()
                                 + " has duplicate id to Serialization extension "
                                 + ID_SERIALIZATION_MAP.get(idByte).getClass().getName()
                                 + ", ignore this Serialization extension");
                continue;
            }
            ID_SERIALIZATION_MAP.put(idByte, serialization);
        }
    }
    /**
     * 根据id(1,2,3...)获取Serialization扩展实现
     * @param id
     * @return
     */
    public static Serialization getSerializationById(Byte id) {
        return ID_SERIALIZATION_MAP.get(id);
    }
    /**
     * 根据名称获取Serialization扩展实现
     * @param url
     * @return
     */
    public static Serialization getSerialization(URL url) {
        return ExtensionLoader.getExtensionLoader(Serialization.class).getExtension(
            url.getParameter(Constants.SERIALIZATION_KEY, Constants.DEFAULT_REMOTING_SERIALIZATION));
    }
    /**
     * 先用id 找不到从url中获取名称
     * @param url
     * @param id
     * @return
     */
    public static Serialization getSerialization(URL url, Byte id) {
        Serialization result = getSerializationById(id);
        if (result == null) {
            result = getSerialization(url);
        }
        return result;
    }

}
