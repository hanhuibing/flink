/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.executiongraph;

import org.apache.flink.runtime.akka.AkkaUtils;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.instance.DummyActorGateway;
import org.apache.flink.runtime.instance.Instance;
import org.apache.flink.runtime.instance.SimpleSlot;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobmanager.scheduler.LocationPreferenceConstraint;
import org.apache.flink.runtime.jobmanager.slots.ActorTaskManagerGateway;
import org.apache.flink.runtime.jobmaster.LogicalSlot;
import org.apache.flink.runtime.testingUtils.TestingUtils;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.apache.flink.runtime.executiongraph.ExecutionGraphTestUtils.getExecutionVertex;
import static org.apache.flink.runtime.executiongraph.ExecutionGraphTestUtils.getInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExecutionVertexSchedulingTest {

	@Test
	public void testSlotReleasedWhenScheduledImmediately() {
		try {
			final ExecutionJobVertex ejv = getExecutionVertex(new JobVertexID());
			final ExecutionVertex vertex = new ExecutionVertex(ejv, 0, new IntermediateResult[0],
					AkkaUtils.getDefaultTimeout());

			// a slot than cannot be deployed to
			final Instance instance = getInstance(new ActorTaskManagerGateway(DummyActorGateway.INSTANCE));
			final SimpleSlot slot = instance.allocateSimpleSlot();
			
			slot.releaseSlot();
			assertTrue(slot.isReleased());

			CompletableFuture<LogicalSlot> future = new CompletableFuture<>();
			future.complete(slot);

			assertEquals(ExecutionState.CREATED, vertex.getExecutionState());
			// try to deploy to the slot
			vertex.scheduleForExecution(
				new TestingSlotProvider(ignore -> future),
				false,
				LocationPreferenceConstraint.ALL,
				Collections.emptySet());

			// will have failed
			assertEquals(ExecutionState.FAILED, vertex.getExecutionState());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testSlotReleasedWhenScheduledQueued() {
		try {
			final ExecutionJobVertex ejv = getExecutionVertex(new JobVertexID());
			final ExecutionVertex vertex = new ExecutionVertex(ejv, 0, new IntermediateResult[0],
					AkkaUtils.getDefaultTimeout());

			// a slot than cannot be deployed to
			final Instance instance = getInstance(new ActorTaskManagerGateway(DummyActorGateway.INSTANCE));
			final SimpleSlot slot = instance.allocateSimpleSlot();

			slot.releaseSlot();
			assertTrue(slot.isReleased());

			final CompletableFuture<LogicalSlot> future = new CompletableFuture<>();

			assertEquals(ExecutionState.CREATED, vertex.getExecutionState());
			// try to deploy to the slot
			vertex.scheduleForExecution(
				new TestingSlotProvider(ignore -> future),
				true,
				LocationPreferenceConstraint.ALL,
				Collections.emptySet());

			// future has not yet a slot
			assertEquals(ExecutionState.SCHEDULED, vertex.getExecutionState());

			future.complete(slot);

			// will have failed
			assertEquals(ExecutionState.FAILED, vertex.getExecutionState());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testScheduleToDeploying() {
		try {
			final ExecutionJobVertex ejv = getExecutionVertex(new JobVertexID());
			final ExecutionVertex vertex = new ExecutionVertex(ejv, 0, new IntermediateResult[0],
					AkkaUtils.getDefaultTimeout());

			final Instance instance = getInstance(new ActorTaskManagerGateway(
				new ExecutionGraphTestUtils.SimpleActorGateway(TestingUtils.defaultExecutionContext())));
			final SimpleSlot slot = instance.allocateSimpleSlot();

			CompletableFuture<LogicalSlot> future = new CompletableFuture<>();
			future.complete(slot);

			assertEquals(ExecutionState.CREATED, vertex.getExecutionState());

			// try to deploy to the slot
			vertex.scheduleForExecution(
				new TestingSlotProvider(ignore -> future),
				false,
				LocationPreferenceConstraint.ALL,
				Collections.emptySet());
			assertEquals(ExecutionState.DEPLOYING, vertex.getExecutionState());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
