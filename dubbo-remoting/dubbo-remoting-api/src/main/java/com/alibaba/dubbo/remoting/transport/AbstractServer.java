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
package com.alibaba.dubbo.remoting.transport;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ExecutorUtil;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.Server;
import com.alibaba.dubbo.remoting.transport.dispatcher.WrappedChannelHandler;

/**
 * AbstractServer
 * 
 * @author qian.lei
 * @author ding.lid
 */
public abstract class AbstractServer extends AbstractEndpoint implements Server {
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractServer.class);

    private InetSocketAddress              localAddress;//服务端的地址

    private InetSocketAddress              bindAddress;//可能是localAddress 也可能ip是0.0.0.0

    private int                            accepts;//服务器端接受的连接

    private int                            idleTimeout = 600; //600 seconds 空闲的超时时间
    
    protected static final String SERVER_THREAD_POOL_NAME  ="DubboServerHandler";
    
    ExecutorService executor;//对象独自的线程池

    public AbstractServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);
        localAddress = getUrl().toInetSocketAddress();
        String host = url.getParameter(Constants.ANYHOST_KEY, false) 
                        || NetUtils.isInvalidLocalHost(getUrl().getHost()) 
                        ? NetUtils.ANYHOST : getUrl().getHost();
        bindAddress = new InetSocketAddress(host, getUrl().getPort());
        this.accepts = url.getParameter(Constants.ACCEPTS_KEY, Constants.DEFAULT_ACCEPTS);
        this.idleTimeout = url.getParameter(Constants.IDLE_TIMEOUT_KEY, Constants.DEFAULT_IDLE_TIMEOUT);
        try {
            doOpen();//调用模板方法 完成具体的open操作
            if (logger.isInfoEnabled()) {
                logger.info("Start " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
            }
        } catch (Throwable t) {
            throw new RemotingException(url.toInetSocketAddress(), null, "Failed to bind " + getClass().getSimpleName() 
                                        + " on " + getLocalAddress() + ", cause: " + t.getMessage(), t);
        }
        if (handler instanceof WrappedChannelHandler ){
            executor = ((WrappedChannelHandler)handler).getExecutor();
        }
    }
    
    protected abstract void doOpen() throws Throwable;
    
    protected abstract void doClose() throws Throwable;

    public void reset(URL url) {
        if (url == null) {
            return;
        }
        try {
            if (url.hasParameter(Constants.ACCEPTS_KEY)) {
                int a = url.getParameter(Constants.ACCEPTS_KEY, 0);
                if (a > 0) {
                    this.accepts = a;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(Constants.IDLE_TIMEOUT_KEY)) {
                int t = url.getParameter(Constants.IDLE_TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.idleTimeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(Constants.THREADS_KEY) 
                    && executor instanceof ThreadPoolExecutor && !executor.isShutdown()) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                int threads = url.getParameter(Constants.THREADS_KEY, 0);
                int max = threadPoolExecutor.getMaximumPoolSize();
                int core = threadPoolExecutor.getCorePoolSize();
                if (threads > 0 && (threads != max || threads != core)) {
                    if (threads < core) {
                        threadPoolExecutor.setCorePoolSize(threads);
                        if (core == max) {
                            threadPoolExecutor.setMaximumPoolSize(threads);
                        }
                    } else {
                        threadPoolExecutor.setMaximumPoolSize(threads);
                        if (core == max) {
                            threadPoolExecutor.setCorePoolSize(threads);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        super.setUrl(getUrl().addParameters(url.getParameters()));//告诉u父类 url已经是最新的了
    }

    public void send(Object message, boolean sent) throws RemotingException {
        Collection<Channel> channels = getChannels();
        for (Channel channel : channels) {//给所有的通道发送信息wuzl个人感觉是因为server端发信息应该是全局的所以给全部
            if (channel.isConnected()) {
                channel.send(message, sent);
            }
        }
    }
    
    public void close() {
        if (logger.isInfoEnabled()) {
            logger.info("Close " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
        }
        ExecutorUtil.shutdownNow(executor ,100);
        try {
            super.close();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            doClose();//模板方法
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }
    
    public void close(int timeout) {
        ExecutorUtil.gracefulShutdown(executor ,timeout);
        close();
    }

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }
    
    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public int getAccepts() {
        return accepts;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public void connected(Channel ch) throws RemotingException {
        Collection<Channel> channels = getChannels();//调用的抽象方法  由子类实现
        if (accepts > 0 && channels.size() > accepts) {// 如果连接数过多了 不接受新的接入  关闭通道
            logger.error("Close channel " + ch + ", cause: The server " + ch.getLocalAddress() + " connections greater than max config " + accepts);
            ch.close();
            return;
        }
        super.connected(ch);
    }
    
    @Override
    public void disconnected(Channel ch) throws RemotingException {
        Collection<Channel> channels = getChannels();
        if (channels.size() == 0){
            logger.warn("All clients has discontected from " + ch.getLocalAddress() + ". You can graceful shutdown now.");
        }
        super.disconnected(ch);
    }
    
}