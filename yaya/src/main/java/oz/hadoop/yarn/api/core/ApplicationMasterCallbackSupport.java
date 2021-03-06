/*
 * Copyright 2014 the original author or authors.
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
package oz.hadoop.yarn.api.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.server.nodemanager.NodeManager;


/**
 * INTERNAL API
 * 
 * A specification class meant to customize the behavior of {@link ApplicationMasterLauncher}.
 * The two customization strategies are {@link AMRMClientAsync.CallbackHandler} and
 * {@link NMClientAsync.CallbackHandler}.
 *
 * While it provides a default implementation you may want to override it fully or partially.
 *
 * @author Oleg Zhurakousky
 *
 */
class ApplicationMasterCallbackSupport {

	protected static final Log logger = LogFactory.getLog(ApplicationMasterCallbackSupport.class);

	/**
	 * Builds and instance of {@link AMRMClientAsync.CallbackHandler}
	 *
	 * @param yarnApplicationMaster
	 * @return
	 */
	protected AMRMClientAsync.CallbackHandler buildResourceManagerCallbackHandler(AbstractApplicationContainerLauncher applicationMasterDelegate) {
		return new ResourceManagerCallbackHandler(applicationMasterDelegate);
	}

	/**
	 * Builds and instance of {@link NMClientAsync.CallbackHandler}
	 *
	 * @param yarnApplicationMaster
	 * @return
	 */
	protected NMClientAsync.CallbackHandler buildNodeManagerCallbackHandler(AbstractApplicationContainerLauncher applicationMasterDelegate) {
		// noop for now
		return new NodeManagerCallbaclHandler(applicationMasterDelegate);
	}

	/**
	 * A callback handler registered with the {@link NodeManager} to be notified of
	 * life-cycle events of the deployed containers. For more information
	 * see {@link NMClientAsync.CallbackHandler} documentation
	 *
	 * While there may be no need for handling node manager call backs, the implementation
	 * of {@link NMClientAsyncImpl) requires an instance of such callback handler to be provided
	 * during the construction. Therefore current implementation simply logs received events.
	 */
	private static class NodeManagerCallbaclHandler implements NMClientAsync.CallbackHandler {
		private final AbstractApplicationContainerLauncher applicationMasterDelegate;
		public NodeManagerCallbaclHandler(AbstractApplicationContainerLauncher applicationMasterDelegate){
			this.applicationMasterDelegate = applicationMasterDelegate;
		}
		@Override
		public void onContainerStopped(ContainerId containerId) {
			logger.info("Received onContainerStopped: " + containerId);
		}

		@Override
		public void onContainerStatusReceived(ContainerId containerId, ContainerStatus containerStatus) {
			logger.info("Received onContainerStatusReceived: " + containerStatus);
		}

		@Override
		public void onContainerStarted(ContainerId containerId, Map<String, ByteBuffer> allServiceResponse) {
			logger.info("Received onContainerStarted: " + containerId);
			applicationMasterDelegate.containerStarted(containerId);
		}

		@Override
		public void onStartContainerError(ContainerId containerId, Throwable t) {
			applicationMasterDelegate.containerStartupErrorReceived(containerId, t);
			logger.error("Received onStartContainerError: " + containerId, t);
		}

		@Override
		public void onGetContainerStatusError(ContainerId containerId, Throwable t) {
			logger.error("Received onGetContainerStatusError: " + containerId, t);
		}

		@Override
		public void onStopContainerError(ContainerId containerId, Throwable t) {
			logger.info("Received onStopContainerError: " + containerId);
		}
	}

	/**
	 * A callback handler registered with the {@link AMRMClientAsync} to be notified of
	 * Resource Manager events allowing you to manage the life-cycle of the containers.
	 * The 'containerMonitor' - a {@link CountDownLatch} created with the same count as
	 * the allocated containers is used to manage the life-cycle of this Application Master.
	 */
	private class ResourceManagerCallbackHandler implements AMRMClientAsync.CallbackHandler {

		private final Log logger = LogFactory.getLog(ResourceManagerCallbackHandler.class);

		private final AtomicInteger completedContainersCounter = new AtomicInteger();

		private final AbstractApplicationContainerLauncher applicationMasterDelegate;
		
		private final List<Container> allocatedContainers = new ArrayList<>();

		public ResourceManagerCallbackHandler(AbstractApplicationContainerLauncher applicationMasterDelegate) {
			this.applicationMasterDelegate = applicationMasterDelegate;
		}

		@Override
		public void onContainersCompleted(List<ContainerStatus> completedContainers) {
			logger.info("Received completed contaners callback: " + completedContainers);
			 for (ContainerStatus containerStatus : completedContainers) {
				 logger.info("ContainerStatus: " + containerStatus);
				 this.applicationMasterDelegate.containerCompleted(containerStatus);
				 this.completedContainersCounter.incrementAndGet();
			 }
		}

		/**
		 * Will launch each allocated container asynchronously.
		 */
		@Override
		public void onContainersAllocated(List<Container> allocatedContainers) {
			this.allocatedContainers.addAll(allocatedContainers);
			logger.info("Received allocated containers callback: " + allocatedContainers);
			for (final Container allocatedContainer : allocatedContainers) {
				this.applicationMasterDelegate.containerAllocated(allocatedContainer);
			}
		}

		@Override
		public void onShutdownRequest() {
			logger.info("Received shut down callback");
			this.applicationMasterDelegate.shutdownRequested(this.allocatedContainers);
		}

		@Override
		public void onNodesUpdated(List<NodeReport> updatedNodes) {
			logger.info("Received node update callback for " + updatedNodes);
		}

		@Override
		public float getProgress() {
			return 0;
		}

		@Override
		public void onError(Throwable e) {
			logger.error("Received error", e);
			this.applicationMasterDelegate.errorReceived(e);
		}
	}

}
