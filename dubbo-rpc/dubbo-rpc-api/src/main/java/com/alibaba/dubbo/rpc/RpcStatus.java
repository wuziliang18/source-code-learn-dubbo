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
package com.alibaba.dubbo.rpc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.dubbo.common.URL;

/**
 * URL statistics. (API, Cached, ThreadSafe)
 * 保存统计各个接口和方法的调用次数当前调用量 最大调用时间等信息
 * @see com.alibaba.dubbo.rpc.filter.ActiveLimitFilter
 * @see com.alibaba.dubbo.rpc.filter.ExecuteLimitFilter
 * @see com.alibaba.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance
 * @author william.liangf
 */
public class RpcStatus {
	//服务
    private static final ConcurrentMap<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap<String, RpcStatus>();
    //方法
    private static final ConcurrentMap<String, ConcurrentMap<String, RpcStatus>> METHOD_STATISTICS = new ConcurrentHashMap<String, ConcurrentMap<String, RpcStatus>>();

    /**
     * 
     * @param url
     * @return status
     */
    public static RpcStatus getStatus(URL url) {
        String uri = url.toIdentityString();
        RpcStatus status = SERVICE_STATISTICS.get(uri);
        if (status == null) {
            SERVICE_STATISTICS.putIfAbsent(uri, new RpcStatus());
            status = SERVICE_STATISTICS.get(uri);
        }
        return status;
    }
    
    /**
     * 
     * @param url
     */
    public static void removeStatus(URL url) {
        String uri = url.toIdentityString();
        SERVICE_STATISTICS.remove(uri);
    }
    
    /**
     * 
     * @param url
     * @param methodName
     * @return status
     */
    public static RpcStatus getStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.get(uri);
        if (map == null) {
            METHOD_STATISTICS.putIfAbsent(uri, new ConcurrentHashMap<String, RpcStatus>());
            map = METHOD_STATISTICS.get(uri);
        }
        RpcStatus status = map.get(methodName);
        if (status == null) {
            map.putIfAbsent(methodName, new RpcStatus());
            status = map.get(methodName);
        }
        return status;
    }

    /**
     * 
     * @param url
     */
    public static void removeStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.get(uri);
        if (map != null) {
            map.remove(methodName);
        }
    }

    /**
     * 调用前记录下活动数+1
     * @param url
     */
    public static void beginCount(URL url, String methodName) {
        beginCount(getStatus(url));
        beginCount(getStatus(url, methodName));
    }
    
    private static void beginCount(RpcStatus status) {
        status.active.incrementAndGet();
    }

    /**
     * 调用完成后记录下些数据
     * @param url
     * @param elapsed
     * @param succeeded
     */
    public static void endCount(URL url, String methodName, long elapsed, boolean succeeded) {
        endCount(getStatus(url), elapsed, succeeded);
        endCount(getStatus(url, methodName), elapsed, succeeded);
    }
    
    private static void endCount(RpcStatus status, long elapsed, boolean succeeded) {
        status.active.decrementAndGet();//运行的-1
        status.total.incrementAndGet();//累计一次
        status.totalElapsed.addAndGet(elapsed);//累加花费的总时间
        if (status.maxElapsed.get() < elapsed) {//如果花费时间大于记录的 刷新
            status.maxElapsed.set(elapsed);
        }
        if (succeeded) {//刷新可能是最多的成功花费时间
            if (status.succeededMaxElapsed.get() < elapsed) {
                status.succeededMaxElapsed.set(elapsed);
            }
        } else {
            status.failed.incrementAndGet();//失败记录+1
            status.failedElapsed.addAndGet(elapsed);//失败花费总时间累加
            if (status.failedMaxElapsed.get() < elapsed) {//刷新可能是最多的失败花费时间
                status.failedMaxElapsed.set(elapsed);
            }
        }
    }

    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<String, Object>();

    private final AtomicInteger active = new AtomicInteger();//活动的计数器begin+ end-1

    private final AtomicLong total = new AtomicLong();//调用总次数每end一次+1

    private final AtomicInteger failed = new AtomicInteger();//失败次数 

    private final AtomicLong totalElapsed = new AtomicLong();//花费时间计数每次加花费的时间

    private final AtomicLong failedElapsed = new AtomicLong();//失败花费总时间累加

    private final AtomicLong maxElapsed = new AtomicLong();//最大花费时间

    private final AtomicLong failedMaxElapsed = new AtomicLong();//失败的时候花费的最大时间

    private final AtomicLong succeededMaxElapsed = new AtomicLong();//成功的时候花费的最大时间
    
    private RpcStatus() {}

    /**
     * set value.
     * 
     * @param key
     * @param value
     */
    public void set(String key, Object value) {
        values.put(key, value);
    }

    /**
     * get value.
     * 
     * @param key
     * @return value
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * get active.
     * 
     * @return active
     */
    public int getActive() {
        return active.get();
    }

    /**
     * get total.
     * 
     * @return total
     */
    public long getTotal() {
        return total.longValue();
    }
    
    /**
     * get total elapsed.
     * 
     * @return total elapsed
     */
    public long getTotalElapsed() {
        return totalElapsed.get();
    }

    /**
     * get average elapsed.
     * 
     * @return average elapsed
     */
    public long getAverageElapsed() {
        long total = getTotal();
        if (total == 0) {
            return 0;
        }
        return getTotalElapsed() / total;
    }

    /**
     * get max elapsed.
     * 
     * @return max elapsed
     */
    public long getMaxElapsed() {
        return maxElapsed.get();
    }

    /**
     * get failed.
     * 
     * @return failed
     */
    public int getFailed() {
        return failed.get();
    }

    /**
     * get failed elapsed.
     * 
     * @return failed elapsed
     */
    public long getFailedElapsed() {
        return failedElapsed.get();
    }

    /**
     * get failed average elapsed.
     * 
     * @return failed average elapsed
     */
    public long getFailedAverageElapsed() {
        long failed = getFailed();
        if (failed == 0) {
            return 0;
        }
        return getFailedElapsed() / failed;
    }

    /**
     * get failed max elapsed.
     * 
     * @return failed max elapsed
     */
    public long getFailedMaxElapsed() {
        return failedMaxElapsed.get();
    }

    /**
     * get succeeded.
     * 
     * @return succeeded
     */
    public long getSucceeded() {
        return getTotal() - getFailed();
    }

    /**
     * get succeeded elapsed.
     * 
     * @return succeeded elapsed
     */
    public long getSucceededElapsed() {
        return getTotalElapsed() - getFailedElapsed();
    }

    /**
     * get succeeded average elapsed.
     * 
     * @return succeeded average elapsed
     */
    public long getSucceededAverageElapsed() {
        long succeeded = getSucceeded();
        if (succeeded == 0) {
            return 0;
        }
        return getSucceededElapsed() / succeeded;
    }

    /**
     * get succeeded max elapsed.
     * 
     * @return succeeded max elapsed.
     */
    public long getSucceededMaxElapsed() {
        return succeededMaxElapsed.get();
    }

    /**
     * Calculate average TPS (Transaction per second).
     *
     * @return tps
     */
    public long getAverageTps() {
        if (getTotalElapsed() >= 1000L) {//如果大于一秒计算出
            return getTotal() / (getTotalElapsed() / 1000L);
        }
        return getTotal();// 不到1秒直接返回
    }

}