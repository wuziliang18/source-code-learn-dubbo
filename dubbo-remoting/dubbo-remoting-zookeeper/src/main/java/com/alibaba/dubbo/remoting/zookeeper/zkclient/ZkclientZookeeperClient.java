package com.alibaba.dubbo.remoting.zookeeper.zkclient;

import java.util.List;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNoNodeException;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.zookeeper.Watcher.Event.KeeperState;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.support.AbstractZookeeperClient;

public class ZkclientZookeeperClient extends AbstractZookeeperClient<IZkChildListener> {

	private final ZkClient client;

	private volatile KeeperState state = KeeperState.SyncConnected;

	public ZkclientZookeeperClient(URL url) {
		super(url);
		client = new ZkClient(url.getBackupAddress());//初始化zkclient
		client.subscribeStateChanges(new IZkStateListener() {//订阅状态变化 触发各种自定义监听
			public void handleStateChanged(KeeperState state) throws Exception {
				ZkclientZookeeperClient.this.state = state;
				if (state == KeeperState.Disconnected) {
					stateChanged(StateListener.DISCONNECTED);
				} else if (state == KeeperState.SyncConnected) {
					stateChanged(StateListener.CONNECTED);
				}
			}
			public void handleNewSession() throws Exception {
				stateChanged(StateListener.RECONNECTED);
			}
		});
	}
	/**
	 * 新建持久化的路径
	 * @param path
	 */
	public void createPersistent(String path) {
		try {
			client.createPersistent(path, true);
		} catch (ZkNodeExistsException e) {
		}
	}
	/**
	 * 新建临时的路径 临时路径不能有子节点
	 * @param path
	 */
	public void createEphemeral(String path) {
		try {
			client.createEphemeral(path);
		} catch (ZkNodeExistsException e) {
		}
	}

	public void delete(String path) {
		try {
			client.delete(path);
		} catch (ZkNoNodeException e) {
		}
	}

	public List<String> getChildren(String path) {
		try {
			return client.getChildren(path);
        } catch (ZkNoNodeException e) {
            return null;
        }
	}

	public boolean isConnected() {
		return state == KeeperState.SyncConnected;
	}

	public void doClose() {
		client.close();
	}
	/**
	 * 包装一个监听 触发包装的自定义监听
	 * 与状态监听意义差不多 但用法稍微复杂 原因在于初始化前不清楚监听的是哪个路径 监听的具体有什么实现 所以用这种方式触发
	 * @param path
	 * @param listener
	 * @return
	 */
	public IZkChildListener createTargetChildListener(String path, final ChildListener listener) {
		return new IZkChildListener() {
			public void handleChildChange(String parentPath, List<String> currentChilds)
					throws Exception {
				listener.childChanged(parentPath, currentChilds);
			}
		};
	}
	/**
	 * 加入新订阅的监听
	 * @param path
	 * @param listener
	 * @return
	 */
	public List<String> addTargetChildListener(String path, final IZkChildListener listener) {
		return client.subscribeChildChanges(path, listener);
	}
	/**
	 * 解除监听
	 * @param path
	 * @param listener
	 */
	public void removeTargetChildListener(String path, IZkChildListener listener) {
		client.unsubscribeChildChanges(path,  listener);
	}
}
