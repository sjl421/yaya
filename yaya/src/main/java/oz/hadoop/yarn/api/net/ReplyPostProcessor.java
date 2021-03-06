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
package oz.hadoop.yarn.api.net;

import java.nio.ByteBuffer;

/**
 * @author Oleg Zhurakousky
 *
 */
public abstract class ReplyPostProcessor {
	
	private volatile ContainerDelegate containerDelegate;
	
	/**
	 * 
	 * @param replyBuffer
	 */
	public void postProcess(ByteBuffer replyBuffer) {
		try {
			this.doProcess(replyBuffer);
		} 
		finally {
			this.release();
		}
	}
	
	/**
	 * 
	 * @param replyBuffer
	 */
	public abstract void doProcess(ByteBuffer replyBuffer);
	
	/**
	 * 
	 * @param containerDelegate
	 */
	void setContainerDelegate(ContainerDelegate containerDelegate) {
		this.containerDelegate = containerDelegate;
	}
	
	/**
	 * 
	 */
	private void release(){
		((ContainerDelegateImpl)this.containerDelegate).release();
	}
}
