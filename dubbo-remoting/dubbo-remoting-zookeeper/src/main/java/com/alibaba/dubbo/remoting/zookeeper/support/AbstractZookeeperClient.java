package com.alibaba.dubbo.remoting.zookeeper.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;
/**
 * client的抽象
 * 用了很漂亮的泛型
 * @param <TargetChildListener>
 */
public abstract class AbstractZookeeperClient<TargetChildListener> implements ZookeeperClient {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractZookeeperClient.class);

	private final URL url;

	private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<StateListener>();

	private final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();

	private volatile boolean closed = false;

	public AbstractZookeeperClient(URL url) {
		this.url = url;
	}

	public URL getUrl() {
		return url;
	}
	/**
	 * 生成新节点
	 */
	public void create(String path, boolean ephemeral) {
		int i = path.lastIndexOf('/');
		if (i > 0) {//递归建造目录物理的
			create(path.substring(0, i), false);
		}
		if (ephemeral) {
			createEphemeral(path);//临时的session存在期间有效 不能有子节点所以递归的时候false
		} else {
			createPersistent(path);//永久的
		}
	}

	public void addStateListener(StateListener listener) {
		stateListeners.add(listener);
	}

	public void removeStateListener(StateListener listener) {
		stateListeners.remove(listener);
	}

	public Set<StateListener> getSessionListeners() {
		return stateListeners;
	}
	/**
	 * 添加一个路径下的监听
	 */
	public List<String> addChildListener(String path, final ChildListener listener) {
		ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
		if (listeners == null) {
			childListeners.putIfAbsent(path, new ConcurrentHashMap<ChildListener, TargetChildListener>());
			listeners = childListeners.get(path);
		}
		TargetChildListener targetListener = listeners.get(listener);
		if (targetListener == null) {
			listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
			targetListener = listeners.get(listener);
		}
		return addTargetChildListener(path, targetListener);
	}

	public void removeChildListener(String path, ChildListener listener) {
		ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
		if (listeners != null) {
			TargetChildListener targetListener = listeners.remove(listener);
			if (targetListener != null) {
				removeTargetChildListener(path, targetListener);
			}
		}
	}
	/**
	 * 触发所有的状态监听
	 * @param state
	 */
	protected void stateChanged(int state) {
		for (StateListener sessionListener : getSessionListeners()) {
			sessionListener.stateChanged(state);
		}
	}

	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		try {
			doClose();
		} catch (Throwable t) {
			logger.warn(t.getMessage(), t);
		}
	}

	protected abstract void doClose();

	protected abstract void createPersistent(String path);

	protected abstract void createEphemeral(String path);

	protected abstract TargetChildListener createTargetChildListener(String path, ChildListener listener);

	protected abstract List<String> addTargetChildListener(String path, TargetChildListener listener);

	protected abstract void removeTargetChildListener(String path, TargetChildListener listener);

}
